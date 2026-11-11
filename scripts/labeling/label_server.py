"""A local labelling page for the human label sets (to-do 3.1 and 3.10).

    python3 -I scripts/labeling/label_server.py [--port 8765]

Run from the project root, then open the printed address. Uses only the standard library. It
binds to 127.0.0.1 and nothing leaves this machine, which the Enron emails require.

Three sets:

- `audit`: the 25 Enron audit emails, labelled by hand and blind. Saving writes
  `<stem>.gold.json` in the audit folder, which `build_prelabels.py audit-compare` and
  `finalize-test` read. The `.gold.todo.json` file is left in place. This server never reads the
  LLM drafts of the test or dev sets, so the page cannot show them.
- `reply`: review of the 36 frozen reply checks (`*.reply.json`). The checks are not changed.
  Saving writes `<email>.reply.review.json` next to them (the reviewed checks plus a verdict),
  so that corrections can be applied in one commit before any comparison is reported.
- `multi`: review of the 12 multi-request gold labels (`en-m*.gold.json`), saved the same way
  as `<email>.gold.review.json`.
- `test`: quick review of the LLM labels of the 75 Enron test emails outside the audit, focused
  on DATE / DEADLINE and speech acts (the fields where the audit, protocol B27, found the LLM
  unreliable). Opened only after `audit-compare` has run, because it shows the LLM drafts.
  Saving writes `<stem>.gold.reviewed.json`; the draft stays, so the review's changes can be
  counted, and `build_prelabels.py finalize-test` prefers the reviewed file.
- `rubric`: human rubric (plan W6) on the 20 real-mail replies sampled by `RealMailBenchmark`
  (`rubric-sample.md`, seed 20261111) from one W9 run (`--rubric-run`, by default the 3 B run after the
  excerpt fix, protocol B62). Blind: the page shows the email and the reply only, never the automatic
  flags, the model or the run. Four criteria scored 0-2 with fixed anchors (`RUBRIC`). Saving writes
  `<run>/rubric/<email>.<rater>.json` (Git-ignored like the run); a second reader starts the server with
  `--rater R2` and scores into separate files, so the two can be compared.

Every request must carry the session token that is written into the page, and the Host header
must be the loopback address, so other websites open in the browser cannot read or write labels.
"""
import argparse
import email
import email.policy
import email.utils
import json
import re
import secrets
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

ROOT = Path.cwd()
HERE = Path(__file__).resolve().parent
AUDIT = ROOT / "benchmark/data/enron/labeling/audit"
TEST = ROOT / "benchmark/data/enron/labeling/test"
DEV = ROOT / "benchmark/data/synthetic-dev"
MULTI = ROOT / "benchmark/data/synthetic-multi"
KNOWLEDGE = ROOT / "src/test/resources/university/knowledge"
FROZEN_TEST = ROOT / "benchmark/data/enron/labeling/frozen-test"
RUBRIC_RUN = ROOT / "runtime/real-mail/3b-excerpt-fix-2026-11-11"
RATER = "R1"
# Criteria of the W9 rubric sheet (fixed before any scoring); anchors added so two readers score alike.
RUBRIC = [
    {"id": "polite", "name": "Polite and appropriate",
     "levels": {"2": "polite, fitting register, greets the sender", "1": "minor issue (stiff, odd or missing greeting)",
                "0": "rude, inappropriate, or addressed to the wrong person"}},
    {"id": "answers", "name": "Answers or acknowledges what was asked",
     "levels": {"2": "deals with everything the email asks, or acknowledges an FYI sensibly",
                "1": "deals with part of it", "0": "misses or misunderstands what was asked"}},
    {"id": "inventsNothing", "name": "Invents no fact",
     "levels": {"2": "states nothing beyond the email and what the writer can know",
                "1": "a minor unsupported detail",
                "0": "an invented fact, decision, promise or action (e.g. \"I have attached\", \"confirmed\")"}},
    {"id": "admitsLimits", "name": "Admits what it cannot know",
     "levels": {"2": "says it will check, or asks, where information is missing", "1": "partly",
                "0": "pretends to know or decides what it cannot", "n/a": "nothing in the email needed this"}},
]

ENTITY_TYPES = ["PERSON", "COURSE", "DATE", "DEADLINE", "ID"]
ACTS = ["request", "propose", "commit", "deliver"]
ROLES = ["student", "teacher", "administration", "researcher"]
CONTRIBUTORS = ["student", "teacher", "research", "administration", "generic"]
METADATA_KEYS = ["sender", "recipients", "date", "threadId", "currentText", "entities", "speechActs",
                 "roleHint", "language"]
REPLY_KEYS = ["schema", "language", "replyExpected", "greet", "notGreet", "mustMention", "mustAsk",
              "mustNotContain", "allowInternal", "note", "revision"]
VERDICTS = ["ok", "changed", "unsure"]
STEM = re.compile(r"^[A-Za-z0-9-]+$")
TITLES = re.compile(r"^(prof|dr|mr|mrs|ms|mme|m|professor)\.?\s", re.IGNORECASE)
LEADING_WORDS = re.compile(r"^(by|until|on|before|after|from|at|in|the)\s", re.IGNORECASE)
WEEKDAY = re.compile(r"\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday|weekend|lundi|mardi|"
                     r"mercredi|jeudi|vendredi|samedi|dimanche)\b", re.IGNORECASE)
PHONE = re.compile(r"\d{3}[-. )]+\d{3}[-. ]\d{4}")
QUOTE_MARKERS = re.compile(r"^\s*>|-{3,}\s*original message|forwarded by|^\s*from:.*\n\s*(sent|to):",
                           re.IGNORECASE | re.MULTILINE)
CLOSING = re.compile(r"(best regards|kind regards|regards|thanks|thank you|cheers|sincerely|cordialement|"
                     r"bien à vous)\s*[,.!]?\s*$", re.IGNORECASE)


# The same date patterns as the page's "possible dates" (label.html, DATE_PATTERNS).
MONTHS = ("January|February|March|April|May|June|July|August|September|October|November|December|"
          "Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sept|Sep|Oct|Nov|Dec")
DAYS = "Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday"
DATE_PATTERNS = [re.compile(p, re.IGNORECASE) for p in (
    rf"\b(?:(?:next|this|last)\s+)?(?:{DAYS})\b",
    rf"\b(?:{MONTHS})\.?\s+\d{{1,2}}(?:st|nd|rd|th)?(?:,?\s+\d{{4}})?\b",
    rf"\b\d{{1,2}}(?:st|nd|rd|th)?\s+(?:of\s+)?(?:{MONTHS})\.?(?:,?\s+\d{{4}})?\b",
    rf"\b(?:{MONTHS})\.?\s+\d{{4}}\b",
    r"\b\d{1,2}/\d{1,2}(?:/\d{2,4})?\b",
    r"\b(?:this|next|last)\s+(?:week|weekend|month|year)\b|\bend of (?:the )?(?:day|week|month|year)\b")]
CHECK_ACTS = ("deliver", "commit", "propose")


def date_candidates(text):
    spans = sorted((m.start(), m.end()) for p in DATE_PATTERNS for m in p.finditer(text))
    merged = []
    for start, end in spans:
        if merged and start <= merged[-1][1] + 2 and not text[merged[-1][1]:start].strip(" ,\t\n"):
            merged[-1][1] = max(merged[-1][1], end)
        else:
            merged.append([start, end])
    return [" ".join(text[a:b].split()) for a, b in merged]


def review_reasons(draft):
    """What a reviewer must look at in one LLM draft: the fields the audit (protocol B27) found unreliable."""
    reasons = []
    entities = draft.get("entities") or []
    dated = [e for e in entities if e.get("type") in ("DATE", "DEADLINE")]
    if dated:
        reasons.append(f"{len(dated)} date(s): DATE or DEADLINE?")
    texts = [normalized(e.get("text", "")) for e in entities]
    missed = [c for c in date_candidates(draft.get("currentText") or "")
              if not any(t and (t in normalized(c) or normalized(c) in t) for t in texts)]
    if missed:
        reasons.append(f"{len(missed)} possible date(s) not labelled")
    for act in CHECK_ACTS:
        if act in (draft.get("speechActs") or []):
            reasons.append(f"LLM chose {act}: right?")
    return reasons


def normalized(text):
    return " ".join(text.split()).lower()


def sets():
    """Each set's folder, how to find its items, and where saving writes."""
    def stems(folder, suffix, prefix=""):
        return sorted(p.name[:-len(suffix)] for p in folder.glob(f"{prefix}*{suffix}"))
    audit_ids = json.loads((AUDIT / "audit-ids.json").read_text(encoding="utf-8")) if AUDIT.exists() else []
    return {
        "audit": {"folder": AUDIT, "ids": list(audit_ids)},
        "reply": {"folder": None, "ids": [("dev", s) for s in stems(DEV, ".reply.json")]
                  + [("multi", s) for s in stems(MULTI, ".reply.json")]},
        "multi": {"folder": MULTI, "ids": stems(MULTI, ".gold.json", "en-m") + stems(MULTI, ".gold.json", "fr-m")},
        # The audited emails are left out: their drafts are the LLM side of the audit comparison.
        # Listed only once every audit email has a hand label, so the drafts cannot bias the audit.
        "test": {"folder": TEST, "ids": [s for s in stems(TEST, ".gold.draft.json") if s not in audit_ids]
                 if audit_ids and all((AUDIT / f"{a}.gold.json").exists() for a in audit_ids) else []},
        "rubric": {"folder": FROZEN_TEST, "ids": rubric_ids()},
    }


def rubric_ids():
    sample = RUBRIC_RUN / "rubric-sample.md"
    if not sample.exists():
        return []
    return list(dict.fromkeys(re.findall(r"^\| (enron-t\d+-m\d+) \|", sample.read_text(encoding="utf-8"), re.MULTILINE)))


def rubric_reply(item_id):
    """The reply text of one email from the run's drafts.md; flags and other metadata are left out (blind)."""
    drafts = (RUBRIC_RUN / "drafts.md").read_text(encoding="utf-8")
    section = re.search(rf"^## {re.escape(item_id)} \(.*?(?=^## |\Z)", drafts, re.MULTILINE | re.DOTALL)
    if not section or "**Reply:**" not in section.group(0):
        return None
    reply = section.group(0).split("**Reply:**", 1)[1].split("**Flags:**", 1)[0]
    return "\n".join(line[2:] if line.startswith("> ") else line.lstrip(">") for line in reply.strip().splitlines())


def folder_of(set_name, item_id):
    """The folder of an item, only for ids the set lists, so no other path can be reached."""
    if not STEM.match(item_id or ""):
        raise LookupError("bad id")
    info = sets()[set_name]
    if set_name == "reply":
        for part, stem in info["ids"]:
            if stem == item_id:
                return DEV if part == "dev" else MULTI
        raise LookupError("unknown id")
    if item_id not in info["ids"]:
        raise LookupError("unknown id")
    return info["folder"]


def output_path(set_name, item_id):
    folder = folder_of(set_name, item_id)
    if set_name == "rubric":
        return RUBRIC_RUN / "rubric" / f"{item_id}.{RATER}.json"
    suffix = {"audit": ".gold.json", "reply": ".reply.review.json", "multi": ".gold.review.json",
              "test": ".gold.reviewed.json"}[set_name]
    return folder / f"{item_id}{suffix}"


def read_json(path):
    """Reads JSON, accepting raw line breaks inside strings, which hand edits often leave."""
    return json.loads(path.read_text(encoding="utf-8"), strict=False)


def parse_email(path):
    raw = path.read_bytes()
    message = email.message_from_bytes(raw, policy=email.policy.default)
    try:
        part = message.get_body(preferencelist=("plain",))
        body = part.get_content() if part is not None else message.get_payload(decode=True).decode("utf-8", "replace")
    except Exception:  # Malformed MIME: show the raw text after the headers.
        text = raw.decode("utf-8", "replace")
        body = text.split("\n\n", 1)[1] if "\n\n" in text else text
    # The compat32 parser keeps malformed headers as text instead of failing on them.
    plain = email.message_from_bytes(raw)
    headers = {name: str(plain.get(name, "")) for name in ("From", "To", "Cc", "Date", "Subject", "Message-ID")}
    facts = {"sender": "", "recipients": [], "date": ""}
    try:
        facts["sender"] = email.utils.parseaddr(headers["From"])[1].lower()
        facts["recipients"] = [address.lower() for _, address in
                               email.utils.getaddresses([headers["To"], headers["Cc"]]) if address]
        when = email.utils.parsedate_to_datetime(headers["Date"])
        facts["date"] = when.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    except Exception:
        pass
    return {"headers": headers, "body": body.replace("\r\n", "\n"), "facts": facts}


def validate_metadata(label, mail):
    errors, warnings = [], []
    for key in METADATA_KEYS:
        if key not in label:
            errors.append(f"Missing field {key}")
    if errors:
        return errors, warnings
    extra = [key for key in label if key not in METADATA_KEYS]
    if extra:
        errors.append(f"Unknown fields: {', '.join(extra)}")
    if not isinstance(label["sender"], str) or not label["sender"].strip():
        errors.append("sender must not be empty")
    elif label["sender"] != label["sender"].lower():
        errors.append("sender must be lowercase")
    if not isinstance(label["recipients"], list) or not all(isinstance(r, str) and r for r in label["recipients"]):
        errors.append("recipients must be a list of addresses")
    if not isinstance(label["date"], str) or not re.fullmatch(r"\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ", label["date"]):
        errors.append("date must be a UTC instant such as 2000-06-10T10:22:00Z")
    if not isinstance(label["threadId"], str) or not re.fullmatch(r"<[^<>\s]+>", label["threadId"]):
        errors.append("threadId must be a Message-ID in angle brackets")
    text = label["currentText"] if isinstance(label["currentText"], str) else ""
    if not isinstance(label["currentText"], str):
        errors.append("currentText must be text")
    if label["language"] not in ("en", "fr"):
        errors.append("language must be en or fr")
    if label["roleHint"] is not None and label["roleHint"] not in ROLES:
        errors.append(f"roleHint must be null or one of {', '.join(ROLES)}")
    acts = label["speechActs"]
    if not isinstance(acts, list) or any(a not in ACTS for a in acts):
        errors.append(f"speechActs may only hold {', '.join(ACTS)}")
    elif len(set(acts)) != len(acts):
        errors.append("A speech act is listed twice")

    entities = label["entities"] if isinstance(label["entities"], list) else []
    if not isinstance(label["entities"], list):
        errors.append("entities must be a list")
    seen, dated = set(), {}
    persons = []
    for entity in entities:
        if not isinstance(entity, dict) or set(entity) != {"type", "text"}:
            errors.append("Each entity needs exactly a type and a text")
            continue
        kind, value = entity["type"], entity["text"]
        if kind not in ENTITY_TYPES:
            errors.append(f"Unknown entity type {kind}")
            continue
        if not isinstance(value, str) or not value.strip():
            errors.append(f"Empty {kind} entity")
            continue
        if value != value.strip():
            errors.append(f"{kind} '{value}' has spaces around it")
        key = (kind, normalized(value))
        if key in seen:
            errors.append(f"{kind} '{value}' is listed twice (list an entity once per email)")
        seen.add(key)
        if normalized(value) not in normalized(text):
            errors.append(f"{kind} '{value}' does not occur in currentText (label only the current text)")
        if LEADING_WORDS.match(value) and kind not in ("DATE", "DEADLINE"):
            warnings.append(f"{kind} '{value}' starts with a surrounding word; copy only the entity")
        if kind in ("COURSE", "ID", "PERSON") and WEEKDAY.search(value):
            warnings.append(f"{kind} '{value}' looks like a date; dates are DATE or DEADLINE")
        if kind in ("DATE", "DEADLINE"):
            other = dated.get(normalized(value))
            if other and other != kind:
                errors.append(f"'{value}' is both DATE and DEADLINE")
            dated[normalized(value)] = kind
            if LEADING_WORDS.match(value):
                warnings.append(f"{kind} '{value}' starts with a surrounding word; copy only the date")
            if normalized(value) in ("today", "tomorrow", "yesterday"):
                errors.append(f"Bare '{value}' is not an entity")
        if kind == "PERSON":
            if TITLES.match(value):
                warnings.append(f"PERSON '{value}' has a title; leave titles out")
            persons.append(value)
        if kind == "ID":
            if value.startswith("#"):
                errors.append(f"ID '{value}' must be written without a leading #")
            if PHONE.search(value):
                warnings.append(f"ID '{value}' looks like a phone number, which is not an ID")
    for name in persons:
        for other in persons:
            words, longer = set(normalized(name).split()), set(normalized(other).split())
            if name != other and words < longer:
                warnings.append(f"PERSON '{name}' looks like a shorter form of '{other}'; list the fullest form once")

    if text:
        if QUOTE_MARKERS.search(text):
            warnings.append("currentText seems to hold quoted or forwarded text")
        last_lines = [line for line in text.strip().splitlines() if line.strip()][-2:]
        if any(CLOSING.search(line) for line in last_lines):
            warnings.append("currentText seems to end with a sign-off; the sign-off and signature are left out")
        body = normalized(mail["body"])
        missing = [line.strip() for line in text.splitlines() if line.strip() and normalized(line) not in body]
        if missing:
            warnings.append(f"{len(missing)} line(s) of currentText are not in the email body, e.g. "
                            f"'{missing[0][:60]}'")
    facts = mail["facts"]
    if facts["sender"] and label["sender"] != facts["sender"]:
        warnings.append(f"sender differs from the From header ({facts['sender']})")
    if facts["date"] and label["date"] != facts["date"]:
        warnings.append(f"date differs from the Date header ({facts['date']})")
    if facts["recipients"] and isinstance(label["recipients"], list) \
            and sorted(label["recipients"]) != sorted(facts["recipients"]):
        warnings.append("recipients differ from the To and Cc headers (malformed addresses can explain this)")
    return errors, warnings


def validate_term_groups(name, groups, errors):
    if not isinstance(groups, list):
        errors.append(f"{name} must be a list")
        return
    ids = set()
    for group in groups:
        if not isinstance(group, dict) or set(group) != {"id", "any"}:
            errors.append(f"Each {name} entry needs exactly an id and a list of terms")
            continue
        if not group["id"] or not re.fullmatch(r"[a-z0-9-]+", group["id"]):
            errors.append(f"{name}: id '{group['id']}' must be lowercase letters, digits and '-'")
        if group["id"] in ids:
            errors.append(f"{name}: id '{group['id']}' is used twice")
        ids.add(group["id"])
        if not isinstance(group["any"], list) or not group["any"] or not all(isinstance(t, str) and t.strip()
                                                                               for t in group["any"]):
            errors.append(f"{name} '{group['id']}' needs at least one non-empty term")


def validate_rubric(scores):
    missing = [c["name"] for c in RUBRIC if str(scores.get(c["id"])) not in c["levels"]]
    return ([f"Still to score: {', '.join(missing)}"] if missing else []), []


def validate_review(review, set_name):
    errors, warnings = [], []
    verdict = review.get("_review", {})
    if verdict.get("status") not in VERDICTS:
        errors.append("Choose a verdict: ok, changed or unsure")
    if verdict.get("status") in ("changed", "unsure") and not str(verdict.get("comment", "")).strip():
        errors.append("Say in the comment what changed or what is unsure")
    if set_name == "reply":
        if review.get("schema") != "reply-gold/v1":
            errors.append("schema must stay reply-gold/v1")
        if review.get("language") not in ("en", "fr"):
            errors.append("language must be en or fr")
        for key in ("replyExpected", "allowInternal"):
            if not isinstance(review.get(key), bool):
                errors.append(f"{key} must be true or false")
        greet = review.get("greet")
        if greet is not None and (not isinstance(greet, list) or not all(isinstance(g, str) and g for g in greet)):
            errors.append("greet must be null (any greeting) or a list of names")
        if not isinstance(review.get("notGreet"), list):
            errors.append("notGreet must be a list of names")
        for key in ("mustMention", "mustAsk", "mustNotContain"):
            validate_term_groups(key, review.get(key), errors)
        if review.get("replyExpected") is False and (review.get("mustMention") or review.get("mustAsk")):
            warnings.append("No reply expected, but facts or asks are listed; they are not scored for such emails")
        names = {normalized(n) for n in (greet or [])} & {normalized(n) for n in review.get("notGreet") or []}
        if names:
            errors.append(f"A name is both in greet and notGreet: {', '.join(sorted(names))}")
    else:
        if not isinstance(review.get("requests"), int) or review["requests"] < 1:
            errors.append("requests must be a whole number of at least 1")
        contributors = review.get("contributors")
        if not isinstance(contributors, list) or not contributors or any(c not in CONTRIBUTORS for c in contributors):
            errors.append(f"contributors must be some of {', '.join(CONTRIBUTORS)}")
        elif len(set(contributors)) != len(contributors):
            errors.append("A contributor is listed twice")
    return errors, warnings


def item_status(set_name, item_id):
    path = output_path(set_name, item_id)
    if not path.exists():
        return "todo"
    try:
        data = read_json(path)
    except ValueError:
        return "invalid"
    if set_name in ("audit", "rubric"):
        return "done"
    if set_name == "test":
        return "changed" if data.get("_review", {}).get("changedFields") else "ok"
    return data.get("_review", {}).get("status", "invalid")


def load_item(set_name, item_id):
    folder = folder_of(set_name, item_id)
    mail = parse_email(folder / f"{item_id}.eml")
    result = {"email": mail, "label": None, "original": None, "loadNote": None}
    out = output_path(set_name, item_id)
    if set_name == "audit":
        source = out if out.exists() else folder / f"{item_id}.gold.todo.json"
        try:
            data = read_json(source)
            result["label"] = {key: data.get(key) for key in METADATA_KEYS}
            result["loadNote"] = "Saved label" if source == out else "Template (not saved yet)"
        except ValueError as problem:
            result["loadNote"] = f"Could not read {source.name}: {problem}. Starting from the email's headers."
            result["label"] = None
        if result["label"] is None:
            result["label"] = {"sender": mail["facts"]["sender"], "recipients": mail["facts"]["recipients"],
                               "date": mail["facts"]["date"], "threadId": mail["headers"]["Message-ID"].strip(),
                               "currentText": "", "entities": [], "speechActs": [], "roleHint": None,
                               "language": "en"}
    elif set_name == "rubric":
        result["reply"] = rubric_reply(item_id)
        result["rubric"] = RUBRIC
        result["rater"] = RATER
        saved = read_json(out) if out.exists() else {}
        result["label"] = {"scores": saved.get("scores", {}), "note": saved.get("note", "")}
        result["loadNote"] = "Saved scores" if out.exists() else "Not scored yet"
    elif set_name == "test":
        draft = read_json(folder / f"{item_id}.gold.draft.json")
        result["original"] = {key: draft.get(key) for key in METADATA_KEYS}
        result["attention"] = draft.get("_attention") or []
        result["evidence"] = (draft.get("_evidence") or {}).get("acts") or {}
        result["reasons"] = review_reasons(draft)
        result["label"] = {key: read_json(out).get(key) for key in METADATA_KEYS} if out.exists() else result["original"]
        result["loadNote"] = "Saved review" if out.exists() else "LLM draft, not reviewed yet"
    else:
        original_name = f"{item_id}.reply.json" if set_name == "reply" else f"{item_id}.gold.json"
        result["original"] = read_json(folder / original_name)
        result["label"] = read_json(out) if out.exists() else None
        result["loadNote"] = "Saved review" if out.exists() else "Not reviewed yet"
    return result


def knowledge():
    documents = []
    if KNOWLEDGE.exists():
        readers = {s["id"]: s["readers"] for s in json.loads((KNOWLEDGE / "sources.json").read_text(encoding="utf-8"))}
        for path in sorted(KNOWLEDGE.glob("*/*")):
            if path.is_file():
                documents.append({"source": path.parent.name, "file": path.name,
                                  "readers": readers.get(path.parent.name, []),
                                  "text": path.read_text(encoding="utf-8")})
    return documents


def save(set_name, item_id, payload):
    folder = folder_of(set_name, item_id)
    if set_name == "rubric":
        scores = payload.get("scores") if isinstance(payload.get("scores"), dict) else {}
        errors, warnings = validate_rubric(scores)
        data = {"schema": "rubric/v1", "email": item_id, "run": RUBRIC_RUN.name, "rater": RATER,
                "scores": {c["id"]: str(scores.get(c["id"])) for c in RUBRIC},
                "note": str(payload.get("note", ""))[:2000], "seconds": round(float(payload.get("seconds", 0)), 1),
                "at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")}
        path = output_path(set_name, item_id)
        if not errors:
            path.parent.mkdir(exist_ok=True)
    elif set_name in ("audit", "test"):
        label = {key: payload.get(key) for key in METADATA_KEYS} if isinstance(payload, dict) else {}
        errors, warnings = validate_metadata(label, parse_email(folder / f"{item_id}.eml"))
        data = dict(label)
        if set_name == "test":
            draft = read_json(folder / f"{item_id}.gold.draft.json")
            extra = payload.get("_review") if isinstance(payload.get("_review"), dict) else {}
            data["_review"] = {"changedFields": [k for k in METADATA_KEYS if draft.get(k) != label.get(k)],
                               "reasons": review_reasons(draft),
                               "checked": [str(c) for c in extra.get("checked", [])][:200],
                               "seconds": round(float(extra.get("seconds", 0)), 1),
                               "at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")}
    else:
        errors, warnings = validate_review(payload if isinstance(payload, dict) else {}, set_name)
        keys = REPLY_KEYS if set_name == "reply" else ["schema", "requests", "contributors", "note"]
        data = {key: payload.get(key) for key in keys if key in payload}
        data["_review"] = {"status": payload["_review"]["status"], "comment": payload["_review"].get("comment", ""),
                           "at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")} if not errors else None
    if errors:
        return {"saved": False, "errors": errors, "warnings": warnings}
    path = output_path(set_name, item_id)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    temporary.replace(path)
    return {"saved": True, "errors": [], "warnings": warnings, "file": str(path.relative_to(ROOT))}


def make_handler(token, port):
    page = (HERE / "label.html").read_text(encoding="utf-8").replace("__TOKEN__", token)
    allowed_hosts = {f"127.0.0.1:{port}", f"localhost:{port}"}

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):  # Keep email ids and paths out of the terminal log.
            pass

        def send(self, status, body, kind="application/json"):
            data = body if isinstance(body, bytes) else json.dumps(body, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", f"{kind}; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Security-Policy", "default-src 'self'; script-src 'unsafe-inline'; "
                                                        "style-src 'unsafe-inline'; connect-src 'self'")
            self.end_headers()
            self.wfile.write(data)

        def guarded(self):
            if self.headers.get("Host") not in allowed_hosts:
                self.send(403, {"error": "wrong host"})
                return None
            url = urlparse(self.path)
            if url.path != "/" and self.headers.get("X-Label-Token") != token:
                self.send(403, {"error": "missing token"})
                return None
            return url, {k: v[0] for k, v in parse_qs(url.query).items()}

        def do_GET(self):
            checked = self.guarded()
            if not checked:
                return
            url, query = checked
            try:
                if url.path == "/":
                    self.send(200, page.encode("utf-8"), "text/html")
                elif url.path == "/api/items":
                    name = query.get("set")
                    info = sets()[name]
                    ids = [s for _, s in info["ids"]] if name == "reply" else info["ids"]
                    items = [{"id": i, "status": item_status(name, i)} for i in ids]
                    if name == "test":
                        for item in items:
                            item["reasons"] = review_reasons(read_json(TEST / f"{item['id']}.gold.draft.json"))
                    self.send(200, items)
                elif url.path == "/api/item":
                    self.send(200, load_item(query.get("set"), query.get("id")))
                elif url.path == "/api/knowledge":
                    self.send(200, knowledge())
                else:
                    self.send(404, {"error": "not found"})
            except (KeyError, LookupError):
                self.send(404, {"error": "unknown set or item"})

        def do_POST(self):
            checked = self.guarded()
            if not checked:
                return
            url, query = checked
            try:
                length = int(self.headers.get("Content-Length", "0"))
                if length > 1_000_000:
                    self.send(413, {"error": "too large"})
                    return
                payload = json.loads(self.rfile.read(length).decode("utf-8"))
                name, item_id = query.get("set"), query.get("id")
                if url.path == "/api/save":
                    self.send(200, save(name, item_id, payload))
                elif url.path == "/api/check":
                    if name == "rubric":
                        folder_of(name, item_id)
                        errors, warnings = validate_rubric(payload.get("scores") or {})
                    elif name in ("audit", "test"):
                        folder = folder_of(name, item_id)
                        errors, warnings = validate_metadata(payload, parse_email(folder / f"{item_id}.eml"))
                    else:
                        errors, warnings = validate_review(payload, name)
                    self.send(200, {"errors": errors, "warnings": warnings})
                else:
                    self.send(404, {"error": "not found"})
            except (KeyError, LookupError):
                self.send(404, {"error": "unknown set or item"})
            except (ValueError, TypeError, AttributeError) as problem:
                self.send(400, {"error": f"bad request: {problem}"})

    return Handler


def main():
    global RATER, RUBRIC_RUN
    parser = argparse.ArgumentParser(description="Local labelling page (loopback only)")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--rater", default=RATER, help="rater code for the rubric set, e.g. R1 or R2")
    parser.add_argument("--rubric-run", default=str(RUBRIC_RUN.relative_to(ROOT)),
                        help="W9 run folder whose rubric sample is scored")
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9]{1,8}", args.rater):
        raise SystemExit("--rater must be 1-8 letters or digits")
    RATER, RUBRIC_RUN = args.rater, (ROOT / args.rubric_run).resolve()
    if not RUBRIC_RUN.is_relative_to((ROOT / "runtime").resolve()):
        raise SystemExit("--rubric-run must be a folder under runtime/")
    if not (ROOT / "benchmark").is_dir():
        raise SystemExit("Run this from the project root")
    token = secrets.token_urlsafe(24)
    server = ThreadingHTTPServer(("127.0.0.1", args.port), make_handler(token, args.port))
    print(f"Labelling page: http://127.0.0.1:{args.port}/  (Ctrl+C to stop)", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
