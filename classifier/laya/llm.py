"""Small-LLM value extraction for the incident form (Qwen3-0.6B via Ollama on the PC; same prompts on
Qualcomm Genie/GenieX on the 8 Elite later; laya/qwen_finetune.ipynb fine-tunes it on exactly these prompts). The LLM never picks the incident type; it only fills values for a handful of fields.
Prompts are kept tiny on purpose (small device context windows; every call logs its token count)."""
import json, re, time, urllib.request

OLLAMA = "http://localhost:11434/api/chat"
DEVICE_CONTEXT = 256

SYSTEM = ("You fill an incident report form from what the user said. Reply with JSON only. "
          "Only include fields the user actually stated. Use \"unknown\" if the user says they don't know. "
          "Never guess.")


def _norm(s):
    return re.sub(r"[^a-z0-9 ]", " ", str(s).lower()).split()


def _describe(meta):
    if meta["kind"] == "enum":
        return f"{meta['name']}: one of {', '.join(meta['values'])}"
    if meta["kind"] == "bool":
        return f"{meta['name']}: yes or no"
    if meta["kind"] == "int":
        return f"{meta['name']}: a number"
    hint = f" (known: {', '.join(meta['values'])}, or as said)" if meta["values"] else ""
    return f"{meta['name']}: short text{hint}"


def _schema(metas):
    props = {}
    for m in metas:
        if m["kind"] == "enum":
            props[m["name"]] = {"type": "string", "enum": m["values"] + ["unknown"]}
        elif m["kind"] == "bool":
            props[m["name"]] = {"type": "string", "enum": ["yes", "no", "unknown"]}
        else:
            props[m["name"]] = {"type": "string"}
    return {"type": "object", "properties": props}


def build_prompt(metas, user_text, question=None):
    """The exact extraction prompt (also used to build the fine-tuning data). Returns chat messages."""
    prompt = (f"Assistant asked: {question}\n" if question else "") + f"User said: {user_text}\nFields:\n" + \
        "\n".join("- " + _describe(m) for m in metas)
    return [{"role": "system", "content": SYSTEM}, {"role": "user", "content": prompt}]


def schema(metas):
    return _schema(metas)


class LLM:
    def __init__(self, model="Qwen3:0.6B", timeout=60):
        self.model, self.timeout, self.calls = model, timeout, []

    def _chat(self, messages, schema, num_predict=80):
        body = {"model": self.model, "messages": messages, "stream": False, "format": schema, "think": False,
                "options": {"temperature": 0, "num_predict": num_predict, "num_ctx": 1024}}
        req = urllib.request.Request(OLLAMA, data=json.dumps(body).encode(), headers={"Content-Type": "application/json"})
        t0 = time.time()
        with urllib.request.urlopen(req, timeout=self.timeout) as r:
            out = json.loads(r.read())
        stat = {"ms": round((time.time() - t0) * 1000), "prompt_tokens": out.get("prompt_eval_count"),
                "output_tokens": out.get("eval_count")}
        stat["fits_device"] = (stat["prompt_tokens"] or 0) + (stat["output_tokens"] or 0) <= DEVICE_CONTEXT
        self.calls.append(stat)
        return out["message"]["content"], stat

    def available(self):
        try:
            with urllib.request.urlopen("http://localhost:11434/api/tags", timeout=3) as r:
                names = [m["name"].lower() for m in json.loads(r.read())["models"]]
            return self.model.lower() in names or self.model.lower() + ":latest" in names
        except Exception:
            return False

    def extract(self, metas, user_text, question=None):
        """metas: field_meta dicts (1-5 fields). Returns ({field: value}, stat). Values validated."""
        if not metas or not user_text.strip():
            return {}, None
        msgs = build_prompt(metas, user_text, question)
        data, stat = {}, None
        for _ in range(2):  # one retry on invalid JSON
            raw, stat = self._chat(msgs, _schema(metas))
            try:
                data = json.loads(raw)
                break
            except json.JSONDecodeError:
                data = {}
        return validate(data, metas, user_text), stat

    def choose(self, options, labels, user_text):
        """Map a reply to an offered option ("the second one", "yes it's a leak"). Returns an option id or None."""
        prompt = (f"Options: " + "; ".join(f"{i + 1} = {labels[o]}" for i, o in enumerate(options)) +
                  f"\nUser said: {user_text}\nWhich option did the user pick? Answer 0 if none of them.")
        raw, stat = self._chat([{"role": "user", "content": prompt}],
                               {"type": "object", "properties": {"option": {"type": "integer"}}, "required": ["option"]}, 10)
        try:
            k = int(json.loads(raw)["option"])
        except Exception:
            return None, stat
        return (options[k - 1] if 1 <= k <= len(options) else None), stat

    def question(self, metas, incident, user_said):
        """One short, specific question asking for these fields (LLM-written). Returns (text or None, stat)."""
        hint = "; ".join(f"{m['name'].replace('_', ' ')} (e.g. '{m['question']}')" for m in metas)
        prompt = (f"Incident: {incident}. The user just said: \"{user_said[:200]}\"\n"
                  f"Ask the user for: {hint}.\nWrite ONE short, friendly, specific question. Do not repeat what they said.")
        raw, stat = self._chat([{"role": "system", "content": "You are an incident reporting assistant."},
                                {"role": "user", "content": prompt}],
                               {"type": "object", "properties": {"question": {"type": "string"}}, "required": ["question"]}, 48)
        try:
            q = json.loads(raw)["question"].strip()
        except Exception:
            return None, stat
        ok = 10 <= len(q) <= 220 and q.count("?") >= 1 and "{" not in q
        return (q if ok else None), stat

    def summarise(self, sentences):
        if not sentences:
            return "", None
        raw, stat = self._chat([{"role": "user", "content": "Summarise this incident report in one short sentence:\n" +
                                 " ".join(sentences)}], {"type": "object", "properties": {"summary": {"type": "string"}},
                                                         "required": ["summary"]}, 50)
        try:
            return json.loads(raw)["summary"], stat
        except Exception:
            return "", stat


BUILDING_WORDS = {"building", "block", "tower", "hall", "house", "annex", "wing", "campus", "centre", "center",
                  "pavilion", "office", "site", "plaza", "park"}
DONT_KNOW = re.compile(r"\b(don'?t know|do not know|not sure|no idea|unsure|can'?t tell|cannot tell|dunno|not certain)\b", re.I)
WORD_NUM = {w: i for i, w in enumerate("zero one two three four five six seven eight nine ten eleven twelve".split())}
WORD_NUM |= {"first": 1, "second": 2, "third": 3, "fourth": 4, "fifth": 5, "sixth": 6, "seventh": 7, "eighth": 8,
             "ninth": 9, "tenth": 10, "eleventh": 11, "twelfth": 12, "ground": 1}


def validate(data, metas, user_text):
    """Keep only values that are allowed and grounded in what the user said."""
    out, words = {}, set(_norm(user_text))
    by = {m["name"]: m for m in metas}
    for k, v in (data or {}).items():
        if k not in by or v in (None, "", []):
            continue
        m, v = by[k], str(v).strip()
        if v.lower() == "unknown":
            if DONT_KNOW.search(user_text):  # only when the user actually said they don't know
                out[k] = "unknown"
        elif m["kind"] == "enum":
            if v in m["values"]:
                out[k] = v
        elif m["kind"] == "bool":
            if v.lower() in ("yes", "no"):
                out[k] = v.lower() == "yes"
        elif m["kind"] == "int":
            nums = re.findall(r"-?\d+", v) or [str(WORD_NUM[w]) for w in _norm(v) if w in WORD_NUM]
            said = set(re.findall(r"\d+", user_text)) | {str(WORD_NUM[w]) for w in words if w in WORD_NUM}
            if nums and nums[0].lstrip("-") in said:  # the number was said as digits or as a word ("fourth")
                out[k] = int(nums[0])
        else:  # text: most of its words must appear in the user's words (evidence rule)
            vw = _norm(v)
            if k == "building" and v.upper() not in ("N", "QRC") and not (set(vw) & BUILDING_WORDS):
                continue  # "desk", "pantry" etc. are not buildings
            if len(v) <= 3:  # short codes like "N", "QRC": exact word in the user's text
                if re.search(rf"\b{re.escape(v)}\b", user_text, re.I):
                    out[k] = v.upper() if v.isalpha() else v
            elif vw and sum(w in words for w in vw) / len(vw) >= 0.6:
                out[k] = v
    return out


FLOOR_RE = re.compile(r"\b(?:(\d+|" + "|".join(WORD_NUM) + r")(?:st|nd|rd|th)?\s+(?:floor|level)|(?:floor|level)\s+(\d+|"
                      + "|".join(WORD_NUM) + r"))\b", re.I)
BUILDING_RE = re.compile(r"\b(QRC|N)\b")
NOW_RE = re.compile(r"\b(just now|right now|a (?:minute|moment) ago|just happened)\b", re.I)


def rule_extract(text, want):
    """Deterministic extraction for the most common location/time answers (runs before the LLM, ~0 ms)."""
    out = {}
    if "floor" in want:
        m = FLOOR_RE.search(text)
        if m:
            w = (m.group(1) or m.group(2)).lower()
            out["floor"] = int(w) if w.isdigit() else WORD_NUM[w]
        elif re.search(r"\bground floor\b", text, re.I):
            out["floor"] = 1
    if "building" in want:
        m = BUILDING_RE.search(text)
        if m and (m.group(1) == "QRC" or re.search(r"\b(building|block|of|in|at)\s+N\b|\bN\b\s*(building|,|floor)", text)):
            out["building"] = m.group(1)
    if "occurred_at" in want and NOW_RE.search(text):
        out["occurred_at"] = NOW_RE.search(text).group(1).lower()
    return out
