"""Incident-report assistant (PC prototype): you talk, it classifies, works out what's missing, asks,
classifies again, and stops when the form is complete. Panels show the classifier and the form after
every message.

  python laya/assistant.py                      interactive chat
  python laya/assistant.py --script FILE        one user message per line
  python laya/assistant.py --replay --limit 40  replay test-set user turns, print a summary
  python laya/assistant.py --selftest           classifier / rules / validator checks
Options: --model Qwen3:0.6B (Ollama)  --threshold 0.8  --ckpt laya_ft.pt
"""
import argparse, json, os, re, sys, time
import numpy as np
import torch

HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
from common import (LAYA_REVISION, LayaClassifier, add_lora, build_samples, label_space, load_conversations,  # noqa: E402
                    load_laya_encoder, merge_lora)
import form as F  # noqa: E402
from llm import LLM, rule_extract  # noqa: E402
from tokenizers import Tokenizer  # noqa: E402
from rich.console import Console, Group  # noqa: E402
from rich.panel import Panel  # noqa: E402
from rich.table import Table  # noqa: E402
from paths import laya_directory

LD = laya_directory()
SESSIONS = os.path.join(HERE, "out", "sessions")


def split_sentences(text):
    parts = [p.strip() for line in text.splitlines() for p in re.split(r"(?<=[.!?])\s+", line)]
    return [p for p in parts if p]


class Classifier:
    """Fine-tuned Laya (fp32, CPU), loaded once and kept warm."""
    def __init__(self, ckpt):
        torch.set_num_threads(os.cpu_count() or 8)
        self.tok = Tokenizer.from_file(os.path.join(LD, "tokenizer/tokenizer.json"))
        self.tax, self.types, self.fields = label_space()
        ck = torch.load(ckpt, map_location="cpu", weights_only=True)
        assert ck["types"] == self.types and ck["fields"] == self.fields
        m = LayaClassifier(add_lora(load_laya_encoder(LD, attn="sdpa"), ck["lora"]["r"], ck["lora"]["alpha"]),
                           len(self.types), len(self.fields))
        m.load_state_dict(ck["state"], strict=False)
        merge_lora(m.enc)
        self.model, self.T = m.eval(), ck["temperature"]

    @torch.no_grad()
    def __call__(self, turns):
        """turns: list of user turns, each a list of sentences. Returns probs + per-sentence analysis."""
        fake = {"id": "live", "kind": "single", "turns": [
            {"role": "user", "type_known": "undetermined",
             "sentences": [{"text": s, "relevant": True, "fields": []} for s in t]} for t in turns]}
        s = build_samples([fake], self.tok, self.types, self.fields)[-1]
        t0 = time.time()
        tl, rl, fl = self.model(torch.from_numpy(s["input_ids"])[None], torch.from_numpy(s["attention_mask"])[None])
        ms = (time.time() - t0) * 1000
        p = torch.softmax(tl[0] / self.T, -1).numpy()
        flat = [(k, sent) for k, t in enumerate(turns) for sent in t]
        kept = flat[-len(s["sent_rel"]):] if len(s["sent_rel"]) else []
        sents = []
        for j, (k, text) in enumerate(kept):
            tok = s["sent_index"] == j
            rel = float(torch.softmax(rl[0][tok].mean(0), -1)[1])
            fp = torch.sigmoid(fl[0][tok].mean(0)).numpy()
            sents.append({"turn": k, "text": text, "relevant": rel,
                          "fields": [self.fields[i] for i in np.where(fp > 0.5)[0]]})
        return {"probs": {t: float(p[i]) for i, t in enumerate(self.types)}, "sentences": sents, "ms": ms,
                "tokens": int(s["attention_mask"].sum())}


ORDINALS = {"1": 0, "one": 0, "first": 0, "2": 1, "two": 1, "second": 1, "3": 2, "three": 2, "third": 2}


class Assistant:
    def __init__(self, clf, llm, threshold=0.8, summarise=True, llm_questions=True):
        self.clf, self.llm, self.th, self.summarise, self.llm_questions = clf, llm, threshold, summarise, llm_questions
        self.reset()

    def reset(self):
        self.state, self.turns, self.chat, self.last_action, self.cls = F.FormState(), [], [], None, None
        self.llm_stat, self.note, self.extracted, self.log, self.routing, self.summary = None, "", {}, [], None, ""
        self.q_stat = None

    def _confirm(self, text):
        """Map a reply to one of the offered types: rules first, then the LLM."""
        opts = self.state.pending_confirm
        words = re.findall(r"[a-z0-9]+", text.lower())
        for w in words:
            if w in ORDINALS and ORDINALS[w] < len(opts):
                return opts[ORDINALS[w]], "rule"
        for o in opts:
            lw = set(re.findall(r"[a-z]+", F.label(o).lower())) - {"fault", "issue", "and", "or"}
            if lw and lw & set(words):
                return o, "rule"
        if self.llm:
            choice, stat = self.llm.choose(opts, {o: F.label(o) for o in opts}, text)
            self.llm_stat = stat
            return choice, "llm"
        return None, None

    def step(self, text):
        st = self.state
        sents = split_sentences(text) or [text]
        ended = F.is_end(text)
        if not ended:
            self.turns.append(sents)
        turn = len(self.turns)
        self.chat.append(("user", text))
        if ended and self.cls is not None:  # closing reply: no re-classification or extraction needed
            action = F.next_action(st, self.cls["probs"], self.th, turn, ended=True)
            return self._finish(action, turn, text)
        if not self.turns:  # "done" as the very first message
            self.turns.append(sents); turn = 1
        self.cls = self.clf(self.turns)
        probs = self.cls["probs"]
        if st.pending_confirm:
            top = max(probs, key=probs.get)
            if not (top in F.TYPES and probs[top] >= self.th):  # a confident classifier beats reply matching
                choice, _ = self._confirm(text)
                if choice:
                    st.type, st.type_source, st.type_conf = choice, "user-confirmed", 1.0
            st.pending_confirm = None
        self.note = F.settle_type(st, probs, self.th)

        # extraction: fields just asked + fields the classifier saw in this turn (relevant sentences only)
        top3 = [t for t in sorted(probs, key=probs.get, reverse=True) if t in F.TYPES][:3]
        cand = F.candidate_fields([st.type] if st.type else top3)
        cur = [s for s in self.cls["sentences"] if s["turn"] == turn - 1]
        asked = self.last_action["fields"] if self.last_action and self.last_action["kind"] == "ask" else []
        detected = [f for s in cur if s["relevant"] >= 0.5 for f in s["fields"] if f in cand]
        want = list(dict.fromkeys(asked + detected))[:5]
        rel_text = " ".join(s["text"] for s in cur if s["relevant"] >= 0.5) or (text if asked else "")
        newly = st.type and (self.note.startswith(("type settled", "type changed")) or st.type != getattr(self, "_prev_type", None))
        if newly:  # catch-up: the type just settled, read everything relevant so far for its required fields
            want = list(dict.fromkeys(want + [f for f in F.required_fields(st.type) if not st.filled(f)]))[:5]
            rel_text = " ".join(s["text"] for s in self.cls["sentences"] if s["relevant"] >= 0.5) or text
        self._prev_type = st.type
        self.extracted, self.llm_stat = {}, None
        loc_want = list(dict.fromkeys(want + [f for f in ("building", "floor", "occurred_at") if not st.filled(f)]))
        ruled = rule_extract(text, loc_want)  # deterministic first (building codes, floors, "just now")
        for f, v in ruled.items():
            st.set(f, v, text, turn)
        want = [f for f in want if f not in ruled]
        self.extracted = dict(ruled)
        if want and rel_text and self.llm:
            q = self.last_action["text"] if self.last_action else None
            vals, self.llm_stat = self.llm.extract([F.field_meta(f) for f in want], rel_text, q)
            for f, v in vals.items():
                st.set(f, v, rel_text, turn)
            self.extracted |= vals
        if "occurred_at" in asked and not st.filled("occurred_at"):  # taxonomy default_occurred_at
            st.set("occurred_at", "around report time (approximate)", "(no time given)", turn)

        action = F.next_action(st, probs, self.th, turn, ended=ended)
        return self._finish(action, turn, text)

    def _finish(self, action, turn, text):
        st, probs = self.state, self.cls["probs"]
        action["template"] = action["text"]
        if action["kind"] in ("ask", "detail") and self.llm and self.llm_questions:  # LLM writes the question
            q, qstat = self.llm.question([F.field_meta(f) for f in action["fields"]], F.label(st.type), text)
            if q and len(action["fields"]) > 1:  # a two-field question must mention both, or use the template
                qw = set(re.findall(r"[a-z]+", q.lower()))
                if not all(set(f.split("_")) & qw for f in action["fields"]):
                    q = None
            if q:
                lead = action["text"][: action["text"].find(F.phrase(action["fields"]))] if F.phrase(action["fields"]) in action["text"] else ""
                action["text"] = lead + q + (F.END_HINT if action["kind"] == "detail" else "")
                action["question_by"] = "llm"
            self.q_stat = qstat
        self.last_action = action
        self.chat.append(("assistant", action["text"]))
        transcript = " ".join(s for t in self.turns for s in t)
        self.routing = F.route(st, transcript)
        if st.done and st.outcome == "submitted" and self.summarise and self.llm:
            rel = [s["text"] for s in self.cls["sentences"] if s["relevant"] >= 0.5]
            self.summary, _ = self.llm.summarise(rel)
        self.log.append({"turn": turn, "user": text, "probs": probs, "sentences": self.cls["sentences"],
                         "classifier_ms": self.cls["ms"], "note": self.note, "extracted": self.extracted,
                         "llm": self.llm_stat, "question_llm": self.q_stat, "action": action, "form": st.to_json(),
                         "routing": self.routing})
        self.q_stat = None
        return action

    def save(self):
        os.makedirs(SESSIONS, exist_ok=True)
        p = os.path.join(SESSIONS, time.strftime("%Y%m%d-%H%M%S") + ".json")
        json.dump({"log": self.log, "final": self.state.to_json(), "routing": self.routing, "summary": self.summary},
                  open(p, "w"), indent=1, default=str)
        return p


# ----------------------------------------------------------------------------- display
def bar(p, w=20):
    n = int(round(p * w))
    return "█" * n + "·" * (w - n)


def render(a, console, clear=False):
    st, cls = a.state, a.cls
    if clear:
        console.clear()
    chat = Table.grid(padding=(0, 1))
    for who, msg in a.chat[-8:]:
        chat.add_row(f"[bold cyan]you[/]" if who == "user" else "[bold green]bot[/]", msg)
    if a.last_action:
        by = " · question written by LLM" if a.last_action.get("question_by") == "llm" else ""
        chat.add_row("", f"[dim]why: {a.last_action['reason']}{by}[/]")

    ct = Table(show_header=True, header_style="bold", box=None)
    ct.add_column("type"); ct.add_column("p", justify="right"); ct.add_column("")
    for t in sorted(cls["probs"], key=cls["probs"].get, reverse=True)[:5]:
        p = cls["probs"][t]
        style = "bold green" if t == st.type else ("yellow" if t in ("undetermined", "none") else "")
        ct.add_row(f"[{style}]{F.label(t)}[/]" if style else F.label(t), f"{p:.2f}", bar(p))
    sent = Table(show_header=True, header_style="bold", box=None)
    sent.add_column("turn"); sent.add_column("sentence"); sent.add_column("relevant", justify="right"); sent.add_column("fields")
    for s in cls["sentences"][-8:]:
        r = s["relevant"]
        sent.add_row(str(s["turn"] + 1), s["text"][:70], f"[{'green' if r >= .5 else 'dim'}]{r:.2f}[/]", ", ".join(s["fields"]))
    llm = ""
    if a.llm_stat:
        L = a.llm_stat
        llm = (f"LLM {L['ms']} ms · {L['prompt_tokens']}+{L['output_tokens']} tok "
               f"({'fits' if L['fits_device'] else '[red]over[/]'} 256) · extracted {a.extracted or '{}'}")
    cpanel = Panel(Group(f"[bold]{a.note}[/]   classifier {cls['ms']:.0f} ms, {cls['tokens']} tokens", ct, "", sent,
                         f"[dim]{llm}[/]"), title="classifier", border_style="blue")

    ft = Table(show_header=True, header_style="bold", box=None)
    ft.add_column(""); ft.add_column("field"); ft.add_column("value"); ft.add_column("evidence", overflow="fold")
    req = F.required_fields(st.type) if st.type else F.CORE
    rows = req + [f for f in st.values if f not in req]
    for f in rows:
        meta = F.field_meta(f)
        star = ("[bold red]![/] " if f in F.ESSENTIAL else "") + ("★ " if meta["safety"] else "")
        if st.filled(f):
            v = st.values[f]["value"]
            mark = "[yellow]?[/]" if v == "unknown" else "[green]✓[/]"
            ft.add_row(mark, star + f, str(v), f"[dim]t{st.values[f]['turn']}: {st.values[f]['evidence'][:60]}[/]")
        else:
            ft.add_row("[red]✗[/]", star + f, "[dim]missing[/]" if f in req else "", "")
    v = lambda f: st.values[f]["value"] if st.filled(f) else "[red]?[/]"
    who = F.DEPT_NAMES[F.TYPES[st.type]["dept"]] if st.type else "[red]?[/]"
    head = (f"[bold]! WHAT[/] {F.label(st.type) if st.type else '[red]?[/]'}"
            + (f" ({st.type_source}, {st.type_conf:.2f})" if st.type else "")
            + f"   [bold]! WHO[/] {who}   [bold]! WHERE[/] building {v('building')}, floor {v('floor')}")
    route = ""
    if a.routing:
        r = a.routing
        route = (f"priority [bold]{r['priority']} {r['priority_label']}[/] (SLA {r['sla_min']} min) · team {r['team']} · "
                 f"notify {', '.join(r['notify'])}" + (f" · rules: {'; '.join(r['rules_fired'])}" if r['rules_fired'] else "") +
                 (f" · also check: {', '.join(r['secondary'])}" if r['secondary'] else ""))
    phase = ("[bold green]READY to file[/] (essentials complete) · say 'done' to finish" if st.ready
             else "[yellow]collecting essentials[/]: " + ", ".join(st.missing_essentials()))
    status = phase + f" · questions asked {st.followups}" + (f" · [bold green]{st.outcome.upper()}[/]" if st.done else "")
    fpanel = Panel(Group(head, ft, route, status, (f"summary: {a.summary}" if a.summary else "")), title="form", border_style="magenta")
    console.print(Panel(chat, title="chat", border_style="cyan"))
    console.print(cpanel)
    console.print(fpanel)


# ----------------------------------------------------------------------------- modes
def interactive(a, console):
    console.print("[bold]Incident assistant[/] — describe the problem. Say 'done' to file once it's ready; Ctrl+C or 'quit' to exit.\n")
    while not a.state.done:
        try:
            text = console.input("[bold cyan]you> [/]").strip()
        except (EOFError, KeyboardInterrupt):
            break
        if text.lower() in ("quit", "exit"):
            break
        if text:
            a.step(text)
            render(a, console, clear=True)
    console.print(f"session saved: {a.save()}")


def scripted(a, console, path):
    for line in open(path):
        if a.state.done:
            break
        if line.strip() and not line.startswith("#"):
            console.rule(f"[bold cyan]you:[/] {line.strip()}")
            a.step(line.strip())
            render(a, console)
    console.print(f"session saved: {a.save()}")


def replay(a, console, limit):
    """Feed each test conversation's user turns in order (they answer the dataset's questions, not ours)."""
    convs = load_conversations(os.path.join(ROOT, "datagen/out"), "test")[:limit]
    rows = []
    for c in convs:
        a.reset()
        users = [t for t in c["turns"] if t["role"] == "user"]
        for t in users:
            if a.state.done:
                break
            a.step(" ".join(s["text"] for s in t["sentences"]))
        mentioned = {f for t in users for s in t["sentences"] for f in s["fields"]}
        true = c["incident_type"]
        ok = (a.state.outcome == "out_of_scope") if true == "none" else (a.state.type == true)
        p = a.cls["probs"]; guess = a.state.type or max((t for t in p if t in F.TYPES), key=p.get)
        ok_guess = ok if true == "none" else guess == true
        clf_top1 = max(p, key=p.get) == true
        cand = set(F.required_fields(true)) if true in F.TYPES else set()
        rel = mentioned & cand
        filled = set(a.state.values)
        rows.append({"id": c["id"], "true": true, "pred": a.state.type, "outcome": a.state.outcome, "type_ok": ok, "type_ok_or_top1": ok_guess, "classifier_top1_ok": clf_top1,
                     "complete": a.state.done and not a.state.missing(), "ready": a.state.ready, "questions": a.state.followups,
                     "turns_used": len(a.turns), "req_mentioned": len(rel), "req_mentioned_filled": len(rel & filled),
                     "filled_not_mentioned": len(filled - mentioned - {"occurred_at"})})
        console.print(f"{c['id']:32s} true={true:22s} pred={str(a.state.type):22s} {'✓' if ok else '✗'} "
                      f"q={a.state.followups} filled={len(filled)} outcome={a.state.outcome}")
    calls = [x for x in a.llm.calls] if a.llm else []
    s = {"conversations": len(rows), "type_acc_end_settled": float(np.mean([r["type_ok"] for r in rows])),
         "type_acc_end_settled_or_top1": float(np.mean([r["type_ok_or_top1"] for r in rows])),
         "classifier_top1_at_last_turn": float(np.mean([r["classifier_top1_ok"] for r in rows])),
         "forms_complete": float(np.mean([r["complete"] for r in rows])),
         "essentials_ready": float(np.mean([r["ready"] for r in rows])),
         "avg_questions": float(np.mean([r["questions"] for r in rows])),
         "mentioned_required_recall": sum(r["req_mentioned_filled"] for r in rows) / max(1, sum(r["req_mentioned"] for r in rows)),
         "llm_calls": len(calls), "llm_ms_median": float(np.median([c["ms"] for c in calls])) if calls else None,
         "llm_prompt_tokens_max": max((c["prompt_tokens"] or 0) for c in calls) if calls else None,
         "llm_fit_256": float(np.mean([c["fits_device"] for c in calls])) if calls else None}
    console.print(Panel(json.dumps(s, indent=1), title="replay summary"))
    os.makedirs(SESSIONS, exist_ok=True)
    out = os.path.join(SESSIONS, f"replay_{a.llm.model.replace(':', '_') if a.llm else 'nollm'}.json")
    json.dump({"summary": s, "rows": rows}, open(out, "w"), indent=1)
    console.print(f"saved {out}")


def selftest(clf, llm, console):
    ok = True
    def check(name, cond, detail=""):
        nonlocal ok
        ok &= bool(cond)
        console.print(f"{'[green]PASS[/]' if cond else '[red]FAIL[/]'} {name} {detail}")
    convs = load_conversations(os.path.join(ROOT, "datagen/out"), "test")
    hits = []
    for c in convs[::11][:12]:  # 12 conversations spread over the test set, end-of-conversation type
        turns = [[s["text"] for s in t["sentences"]] for t in c["turns"] if t["role"] == "user"]
        p = clf(turns)["probs"]
        hits.append(max(p, key=p.get) == c["incident_type"])
    check("classifier end-of-conversation accuracy on 12 test conversations >= 9/12 (benchmark ~90%)",
          sum(hits) >= 9, f"({sum(hits)}/12)")
    st = F.FormState(); st.type = "water_leak"; st.set("near_electrical", True, "x", 1)
    r = F.route(st, "")
    check("rule: water leak near electrics -> P1", r["priority"] == "P1" and "Electrical Technician" in r["notify"], str(r["notify"]))
    st = F.FormState(); st.type = "pest_sighting"; st.set("pest_kind", "snake", "x", 1)
    check("rule: snake -> P1 + security", F.route(st, "")["priority"] == "P1")
    st = F.FormState(); st.type = "elevator_fault"; st.set("persons_trapped", "unknown", "x", 1)
    check("rule: unknown safety field -> worse case", F.route(st, "")["priority"] == "P1")
    st = F.FormState(); st.type = "water_leak"
    act = F.next_action(st, {"water_leak": 0.95}, 0.8, 1)
    check("policy: essentials (building, floor) asked first", act["fields"] == ["building", "floor"], str(act["fields"]))
    st.set("building", "N", "x", 1); st.set("floor", 4, "x", 1)
    act = F.next_action(st, {"water_leak": 0.95}, 0.8, 2)
    check("policy: ready after essentials, then safety details with end option",
          st.ready and act["fields"] == ["water_spread", "near_electrical"] and "done" in act["text"], act["kind"])
    act = F.next_action(st, {"water_leak": 0.95}, 0.8, 3, ended=True)
    check("policy: 'done' files the report", st.done and st.outcome == "submitted", act["text"])
    check("end phrases", F.is_end("done") and F.is_end("that's all") and not F.is_end("done, it's on floor 3"))
    from llm import validate
    m = [F.field_meta("water_spread"), F.field_meta("space_id"), F.field_meta("floor")]
    v = validate({"water_spread": "flooding", "space_id": "Room 9 basement", "floor": "4"}, m, "the pantry on floor 4")
    check("validator: rejects off-enum and ungrounded text", v == {"floor": 4}, str(v))
    if llm and llm.available():
        vals, stat = llm.extract([F.field_meta("floor")], "I'm on the third floor", "Which floor?")
        check("llm reachable + JSON", isinstance(vals, dict), f"{vals} {stat}")
    console.print("[bold green]selftest passed[/]" if ok else "[bold red]selftest failed[/]")
    return ok


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--script"); ap.add_argument("--replay", action="store_true"); ap.add_argument("--limit", type=int, default=40)
    ap.add_argument("--selftest", action="store_true"); ap.add_argument("--model", default="Qwen3:0.6B")
    ap.add_argument("--threshold", type=float, default=0.8); ap.add_argument("--ckpt", default=os.path.join(ROOT, "laya_ft.pt"))
    ap.add_argument("--no-llm", action="store_true")
    ap.add_argument("--template-questions", action="store_true", help="use the taxonomy's question texts instead of LLM-written ones")
    args = ap.parse_args()
    console = Console()
    with console.status("loading classifier (fine-tuned Laya) ..."):
        clf = Classifier(args.ckpt)
    llm = None if args.no_llm else LLM(args.model)
    if llm and not llm.available():
        console.print(f"[red]Ollama model {args.model} not available; run `ollama serve` and `ollama pull`. Continuing without LLM.[/]")
        llm = None
    if args.selftest:
        sys.exit(0 if selftest(clf, llm, console) else 1)
    a = Assistant(clf, llm, args.threshold, llm_questions=not args.template_questions)
    if args.replay:
        replay(a, console, args.limit)
    elif args.script:
        scripted(a, console, args.script)
    else:
        interactive(a, console)


if __name__ == "__main__":
    main()
