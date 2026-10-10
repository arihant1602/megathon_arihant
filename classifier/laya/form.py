"""Incident form: schema, state, ask-policy and priority/routing rules, all from incident_taxonomy.json.
Plain code, no ML: the classifier decides the type, the LLM extracts values, this module decides what is
missing, what to ask next, when the form is complete, and how urgent it is and who gets it."""
import json, os, re, time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TAX = json.load(open(os.path.join(ROOT, "incident_taxonomy.json")))
POLICY = TAX["completion_policy"]
CORE = ["building", "floor", "space_id", "occurred_at"]
OPTIONAL_COMMON = ["space_type", "landmark", "immediate_actions_taken", "persons_involved", "witnesses", "asset_tag"]
LOCATION = ["building", "floor", "space_id"]
TYPES = {t["id"]: t for t in TAX["incident_types"]}
PRIORITY_RANK = {"P1": 1, "P2": 2, "P3": 3, "P4": 4}
ROLE_NAMES = {f"{d['id']}.{r['id']}": r["name"] for d in TAX["departments"] for r in d["roles"]}
DEPT_NAMES = {d["id"]: d["name"] for d in TAX["departments"]}
MAX_FOLLOWUPS = POLICY["max_followup_turns"]
MAX_PER_Q = POLICY["max_fields_per_question"]
ESSENTIAL = POLICY.get("essential_fields", ["incident_type", "building", "floor"])   # WHAT (-> WHO) + WHERE
ESSENTIAL_LOC = [f for f in ESSENTIAL if f != "incident_type"]
END_HINT = " (You can also say 'done' to file the report now.)"
END_RE = re.compile(r"^\W*(done|i'?m done|that'?s (all|it|everything)|that is (all|it|everything)|"
                    r"no(pe)?,? that'?s (all|it)|no(pe)?,? that is (all|it)|nothing (else|more)|"
                    r"no(thing)? more|end|finish(ed)?|submit( it)?|file it|that will be all|no,? thanks?|bye|goodbye)\W*$", re.I)


def is_end(text):
    """User wants to stop (short closing replies only, so 'done, it's on floor 3' is not an ending)."""
    return bool(END_RE.match(text.strip())) and len(text.split()) <= 6


def field_meta(name):
    """Normalised field description: kind (enum/bool/int/text), allowed values, safety flag, question."""
    if name in TAX["field_library"]:
        f = TAX["field_library"][name]
        kind = {"enum": "enum", "bool": "bool", "int": "int"}.get(f["type"], "text")
        return {"name": name, "kind": kind, "values": f.get("values", []), "safety": bool(f.get("safety")),
                "question": f.get("question", f"What is the {name.replace('_', ' ')}?")}
    c = TAX["common_fields"][name]
    if name == "space_type":
        return {"name": name, "kind": "enum", "values": TAX["location_model"]["space_types"], "safety": False, "question": ""}
    kind = "int" if c.get("type") == "int" else "text"
    hint = [b["code"] for b in TAX["location_model"]["buildings"]] if name == "building" else []
    return {"name": name, "kind": kind, "values": hint, "safety": False,
            "question": c.get("question", f"What is the {name.replace('_', ' ')}?")}


def label(type_id):
    return {"undetermined": "not clear yet", "none": "not an incident"}.get(type_id, TYPES.get(type_id, {}).get("label", type_id))


def required_fields(type_id):
    return CORE + (TYPES[type_id]["required"] if type_id in TYPES else [])


def candidate_fields(type_ids):
    """Fields worth extracting while the type is (un)settled: core + optional common + those types' fields."""
    out = CORE + OPTIONAL_COMMON
    for t in type_ids:
        if t in TYPES:
            out += [f for f in TYPES[t]["required"] + TYPES[t].get("optional", []) if f not in out]
    return out


def ask_order(type_id, missing):
    """completion_policy.ask_order: safety-critical -> location -> type_required -> occurred_at."""
    safety = [f for f in missing if field_meta(f)["safety"]]
    loc = [f for f in LOCATION if f in missing and f not in safety]
    typ = [f for f in missing if f not in safety + loc + ["occurred_at"]]
    tail = ["occurred_at"] if "occurred_at" in missing else []
    return safety + loc + typ + tail


def phrase(fields):
    """Join the taxonomy's question texts for up to MAX_PER_Q fields into one natural question."""
    qs = [field_meta(f)["question"] for f in fields]
    if len(qs) == 1:
        return qs[0]
    return qs[0] + " And " + qs[1][0].lower() + qs[1][1:]


class FormState:
    def __init__(self):
        self.values = {}            # field -> {"value", "evidence", "turn"}
        self.type = None            # settled incident type id
        self.type_source = None     # "classifier" | "user-confirmed"
        self.type_conf = 0.0
        self.pending_confirm = None # list of type ids offered in a "Is it A, B or C?" question
        self.followups = 0          # field questions asked since the type settled
        self.asked = []             # history of (turn, fields asked)
        self.fire_notice_given = False
        self.ready = False          # essentials (type, building, floor) known: report can be filed
        self.ready_turn = None
        self.asked_fields = set()   # detail fields already asked once (never asked twice)
        self.done = False
        self.outcome = None         # "submitted" | "out_of_scope" | "stopped"
        self.started = time.time()

    def set(self, field, value, evidence, turn):
        self.values[field] = {"value": value, "evidence": evidence, "turn": turn}

    def filled(self, field):
        return field in self.values

    def missing(self):
        return [f for f in required_fields(self.type) if not self.filled(f)] if self.type else list(CORE)

    def missing_essentials(self):
        return ([] if self.type else ["incident_type"]) + [f for f in ESSENTIAL_LOC if not self.filled(f)]

    def to_json(self):
        return {"type": self.type, "type_source": self.type_source, "type_conf": self.type_conf,
                "values": self.values, "followups": self.followups, "asked": self.asked, "outcome": self.outcome,
                "ready": self.ready, "ready_turn": self.ready_turn}


def settle_type(state, probs, threshold):
    """Update the settled type from the classifier. Returns a note describing what happened (for display)."""
    top = max(probs, key=probs.get)
    p = probs[top]
    if state.type_source == "user-confirmed" and not (top in TYPES and top != state.type and p >= threshold):
        return f"type confirmed by user: {state.type}"
    if top in TYPES and p >= threshold:
        if state.type and state.type != top:
            note = f"type changed {state.type} -> {top} ({p:.2f})"
        elif state.type != top:
            note = f"type settled: {top} ({p:.2f} >= {threshold})"
        else:
            note = f"type holds: {top} ({p:.2f})"
        state.type, state.type_source, state.type_conf = top, "classifier", p
        return note
    if state.type and probs.get(state.type, 0) < 0.3 and top != state.type:  # the classifier has moved on
        old = state.type
        state.type, state.type_source, state.type_conf = None, None, 0.0
        return f"un-settled {old} (now {probs.get(old, 0):.2f}); top {top} {p:.2f} < {threshold}, will ask"
    if state.type:
        return f"keeping {state.type}; top now {top} {p:.2f} < {threshold}"
    return f"not settled: top {top} {p:.2f} < {threshold}"


def next_action(state, probs, threshold, turn, ended=False):
    """Decide the assistant's next move. Returns dict(kind, text, fields, reason).
    Phase 1, essentials (type -> who, building + floor -> where): ask only for those.
    Phase 2, ready: ask remaining details one question at a time (each field once); the user can end any time."""
    top = max(probs, key=probs.get)
    if not state.type:
        if top == "none" and probs[top] >= threshold:
            state.done, state.outcome = True, "out_of_scope"
            return {"kind": "out_of_scope", "fields": [], "reason": f"classifier: not an incident ({probs[top]:.2f})",
                    "text": "This assistant is only for reporting facility incidents (leaks, faults, safety, security). "
                            "Nothing has been filed."}
        ranked = [t for t in sorted(probs, key=probs.get, reverse=True) if t in TYPES][:3]
        lead = "I can't file this yet: I still need to know what the problem is. " if ended else ""
        if sum(probs[t] for t in ranked) >= 0.5 and top != "undetermined":
            state.pending_confirm = ranked
            opts = ", ".join(f"({i + 1}) {label(t)}" for i, t in enumerate(ranked))
            return {"kind": "confirm", "fields": [], "reason": f"essential: type below {threshold}, offer top-3",
                    "text": lead + f"Just to make sure I file this right, is it about {opts}, or something else?"}
        return {"kind": "open", "fields": [], "reason": "essential: type not clear yet",
                "text": lead + "Can you tell me a bit more about what's happening?"}

    prefix = ""
    if state.type == "fire_smoke" and not state.fire_notice_given:
        prefix = TYPES["fire_smoke"]["user_prompt_override"] + " "
        state.fire_notice_given = True
    em = [f for f in ESSENTIAL_LOC if not state.filled(f)]
    if em:  # phase 1: WHERE is still missing
        state.followups += 1
        state.asked.append((turn, em))
        lead = "I can't file this yet without the location. " if ended else ""
        return {"kind": "ask", "essential": True, "fields": em[:MAX_PER_Q], "reason": "essential: location missing",
                "text": prefix + lead + phrase(em[:MAX_PER_Q])}

    team = DEPT_NAMES[TYPES[state.type]["dept"]]
    if not state.ready:
        state.ready, state.ready_turn = True, turn
        prefix += f"Got it, your report is ready and {team} will handle it. "
    if ended:
        state.done, state.outcome = True, "submitted"
        return {"kind": "submit", "fields": [], "reason": "user ended the conversation",
                "text": prefix + f"Report filed. {team} has been notified. Thanks!"}
    details = [f for f in ask_order(state.type, state.missing()) if f not in state.asked_fields]
    if details:  # phase 2: more details, safety first, each field asked once
        ask = details[:MAX_PER_Q]
        state.asked_fields |= set(ask)
        state.followups += 1
        state.asked.append((turn, ask))
        why = "detail: safety field" if field_meta(ask[0])["safety"] else "detail: more information"
        return {"kind": "detail", "fields": ask, "reason": why, "text": prefix + phrase(ask) + END_HINT}
    return {"kind": "offer_end", "fields": [], "reason": "no more details to ask",
            "text": prefix + "That's everything I need. Anything else you'd like to add? Say 'done' to file the report."}


# ----------------------------------------------------------------------------- priority & routing
def _match(cond, values):
    f, op, v = cond["field"], cond["op"], cond["value"]
    if f not in values:
        return False
    val = values[f]["value"]
    if val == "unknown" and field_meta(f)["safety"]:  # global rule: unknown safety field -> worse case
        return True
    return val == v if op == "eq" else (val in v if op == "in" else False)


def route(state, transcript):
    """Priority, owning team, people to notify, SLA, secondary incidents, from the taxonomy rules."""
    if not state.type:
        return None
    t = TYPES[state.type]
    pri, notify, fired, reroute = t["default_priority"], [f"{t['dept']}.{t['role']}"], [], None
    for r in t.get("rules", []):
        if _match(r["when"], state.values):
            fired.append(f"{r['when']['field']} {r['when']['op']} {r['when']['value']}")
            if "set_priority" in r and PRIORITY_RANK[r["set_priority"]] < PRIORITY_RANK[pri]:
                pri = r["set_priority"]
            notify += [n for n in r.get("add_notify", []) if n not in notify]
            reroute = r.get("reroute_to", reroute)
    secondary = []
    words = set(re.findall(r"[a-z]+", transcript.lower()))
    for g in TAX["global_rules"]:
        if g["when"]["field"] == "transcript_mentions" and words & set(g["when"]["value"]) and g["type"] != state.type:
            secondary.append(g["type"])
    sla = TAX["priorities"][pri]["response_sla_min"]
    return {"priority": pri, "priority_label": TAX["priorities"][pri]["label"], "team": DEPT_NAMES[t["dept"]],
            "notify": [ROLE_NAMES.get(n, n) for n in notify], "rules_fired": fired, "reroute_to": reroute,
            "secondary": secondary, "sla_min": sla}
