"""Writes the frozen reply checks (reply-gold/v1) next to each synthetic email. Run once, from the project root."""
import json, sys, os

def c(id, *terms):
    return {"id": id, "any": list(terms)}

# Shared term lists (English and French) for facts from the knowledge base.
DECIDER_EN = c("decider", "course leader")
LIMIT_EN = c("limit", "seven days", "7 days", "one week")
CERT_EN = c("medical-certificate", "medical certificate", "medical note", "doctor's note", "certificate from your doctor")
DECIDER_FR = c("decider", "responsable du cours", "responsable de l'enseignement", "enseignant responsable", "course leader")
LIMIT_FR = c("limit", "sept jours", "7 jours", "une semaine")
CERT_FR = c("medical-certificate", "certificat médical", "justificatif médical", "certificat")
NOISE_EN = c("noise", "facilities portal", "medical certificate", "seven days", "registrar", "resit")
NOISE_FR = c("noise", "facilities portal", "portail", "certificat médical", "sept jours", "rattrapage")

def gold(language, reply_expected, greet, not_greet, mention=(), ask=(), must_not=(), allow_internal=False, note=""):
    return {"schema": "reply-gold/v1", "language": language, "replyExpected": reply_expected,
            "greet": greet, "notGreet": list(not_greet), "mustMention": list(mention), "mustAsk": list(ask),
            "mustNotContain": list(must_not), "allowInternal": allow_internal, "note": note}

DEV = {
 "en-01": gold("en", True, ["Sarah"], ["Martin"], [DECIDER_EN, LIMIT_EN], [CERT_EN],
     [c("own-student-number", "20231187"), c("promise", "extension is granted", "I grant you", "I can give you until", "you have an extension", "your extension has been")],
     note="Ill student asks for an extension; the policy: course leader decides, at most seven days, medical certificate required"),
 "en-02": gold("en", False, None, [], note="The owner's own sent reply; nothing to answer"),
 "en-03": gold("en", False, ["Sarah"], ["Martin"], must_not=[c("promise", "extension is granted", "I grant you")], note="A thank-you; no reply needed"),
 "en-04": gold("en", False, None, [], note="Registrar broadcast to all students"),
 "en-05": gold("en", True, ["Tom"], ["Anna"], [c("meeting", "Thursday", "12 November")], [],
     [c("confirms-for-owner", "Thursday works", "see you on Thursday", "I will be there", "I confirm the meeting", "I'll be there")],
     note="Colleague proposes a meeting; the system cannot know the owner's agenda, so it must not accept"),
 "en-06": gold("en", False, None, [], note="The owner's own sent reply"),
 "en-07": gold("en", False, None, [], note="Helpdesk notification"),
 "en-08": gold("en", True, ["Mei"], ["Kofi"], [c("room-not-announced", "to be announced", "not yet", "not been announced", "not announced", "will be announced")], [],
     [c("invented-room", "room A", "room B", "room 1", "room 2")],
     note="Forwarded question about the exam room, which the forwarded notice says is still to be announced"),
 "en-09": gold("en", False, None, [], note="Teacher's broadcast to the class"),
 "en-10": gold("en", True, ["Omar"], [], [], [], [NOISE_EN],
     note="Thesis submission: the knowledge base has no procedure, so the reply must not import unrelated ones"),
 "en-11": gold("en", False, None, [], note="Library notice"),
 "en-12": gold("en", True, ["Grace"], ["Rossi"], [c("invoice", "2026-0457", "invoice")], [],
     [c("payment-promise", "will be paid", "has been paid", "we will pay", "payment has been made", "I will pay")],
     note="Partner sends an invoice; the system must not promise payment"),
 "fr-01": gold("fr", True, ["Lucas"], ["Dubois"], [DECIDER_FR, LIMIT_FR], [CERT_FR],
     [c("own-student-number", "21904432"), c("promise", "je vous accorde", "vous est accordée", "votre demande est acceptée", "votre report est accepté")],
     note="French version of en-01; the knowledge is in English, the reply must be in French"),
 "fr-02": gold("fr", False, None, [], note="The owner's own sent reply"),
 "fr-03": gold("fr", False, ["Lucas"], ["Dubois"], note="A thank-you; no reply needed"),
 "fr-04": gold("fr", False, None, [], note="Broadcast of the student office"),
 "fr-05": gold("fr", True, ["Hugo"], ["Nadia"], [c("meeting", "jeudi", "12 novembre")], [],
     [c("confirms-for-owner", "jeudi me convient", "je serai là", "je confirme", "à jeudi")],
     note="French version of en-05"),
 "fr-06": gold("fr", False, None, [], note="The owner's own sent reply"),
 "fr-07": gold("fr", False, None, [], note="IT notification"),
 "fr-08": gold("fr", True, ["Inès", "Ines"], ["Mathis"], [c("room-not-announced", "à préciser", "pas encore", "annoncée", "communiquée", "précisée")], [],
     [c("invented-room", "salle A", "salle B", "salle 1", "salle 2")],
     note="French version of en-08"),
 "fr-09": gold("fr", False, None, [], note="Teacher's broadcast"),
 "fr-10": gold("fr", True, ["Yanis"], [], [], [], [NOISE_FR], note="French version of en-10"),
 "fr-11": gold("fr", False, None, [], note="Library notice"),
 "fr-12": gold("fr", True, ["Julie"], ["Bernard"], [c("invoice", "2026-0812", "facture")], [],
     [c("payment-promise", "sera réglée", "a été réglée", "nous paierons", "paiement effectué", "sera payée")],
     note="French version of en-12"),
}

MULTI = {
 "en-m01": gold("en", True, ["Sarah"], ["Martin"], [DECIDER_EN, LIMIT_EN], [CERT_EN],
     [c("promise", "extension is granted", "I grant you", "you have an extension", "I will sign", "I have signed", "I signed")],
     note="Extension (policy) and a form to sign (no knowledge: must not promise to sign)"),
 "en-m02": gold("en", True, ["Paul"], ["Emma"], [c("rooms", "facilities portal"), c("research-office", "research office"), c("ten-days", "ten days", "10 days")], [],
     [c("decides-for-owner", "I will co-author", "happy to co-author", "I agree to co-author", "let's swap", "I can swap")],
     allow_internal=True, note="Colleague: room swap (facilities portal) and a grant (research office reviews budgets ten days before the deadline)"),
 "en-m03": gold("en", True, ["Anna"], ["Emma"], [], [],
     [NOISE_EN, c("promise", "I will share", "attached the dataset", "here is the dataset", "I have attached")],
     note="Partner: dataset and conference invoice; no knowledge covers either"),
 "en-m04": gold("en", True, ["Tom"], ["Martin", "Professor"], [c("faculty-page", "faculty page")], [],
     [c("confirms-for-owner", "is still on 14 December", "yes, the exam is", "see you next week", "let's meet")],
     note="Exam date (published on the faculty page; not confirmed by the data) and a thesis meeting"),
 "en-m05": gold("en", True, None, ["Martin"], [], [],
     [c("decides-for-owner", "I have signed", "I signed", "I will sign", "I confirm my timetable", "my timetable is confirmed")],
     note="Registrar: contract amendment and timetable; no knowledge, nothing to decide for the owner"),
 "en-m06": gold("en", True, ["Chris"], [], [], [c("clarify", "what", "which", "more details", "clarify", "more information", "more about")], [NOISE_EN],
     note="Vague message: the reply should ask what it is about"),
 "en-m07": gold("en", True, ["Lea"], ["Martin"], [], [],
     [c("promise", "I will correct", "grade has been changed", "you are accepted", "welcome to the lab", "I will change your grade")],
     note="Grade review and an internship; no knowledge; the internal marking guide must not leak"),
 "en-m08": gold("en", True, ["Ines"], ["Emma"], [], [],
     [c("decides-for-owner", "I will take the invigilation", "I can take", "I'll take", "happy to split", "let's split")],
     allow_internal=True, note="Colleague: invigilation and marking; staff, so the marking guide may be used"),
 "en-m09": gold("en", True, ["Mark"], ["Martin"], [c("research-office", "research office")], [],
     [c("decides-for-owner", "we will join", "happy to join", "I have signed", "I will sign", "we would be glad to join")],
     note="Industry partner: funding call (research office first) and an NDA"),
 "en-m10": gold("en", True, ["Julia"], ["Emma"], [], [],
     [NOISE_EN, c("accepts-for-owner", "see you on Friday", "I would love to", "I'd love to", "sounds great")],
     note="Personal lunch invitation; the system cannot accept for the owner"),
 "en-m11": gold("en", True, ["Noah"], [], [c("registrar", "registrar"), c("three-days", "three working days", "3 working days"), DECIDER_EN, LIMIT_EN],
     [c("student-number", "student number"), CERT_EN],
     [c("promise", "extension is granted", "I grant you", "you can join the lab", "you are welcome to join")],
     note="Attestation (registrar, three working days, needs the student number), extension (policy), lab (no knowledge)"),
 "en-m12": gold("en", True, ["Yuki"], ["Emma"], [], [],
     [NOISE_EN, c("decides-for-owner", "I have filled", "I filled", "I have reviewed", "I will review it by")],
     note="Project paper review and a travel form; no knowledge (no SuperMAS role yet)"),
}

root = sys.argv[1]
for folder, table in (("synthetic-dev", DEV), ("synthetic-multi", MULTI)):
    for name, g in table.items():
        assert os.path.exists(os.path.join(root, folder, name + ".eml")), name
        with open(os.path.join(root, folder, name + ".reply.json"), "w", encoding="utf-8") as out:
            json.dump(g, out, ensure_ascii=False, indent=2)
            out.write("\n")
print(len(DEV), len(MULTI))
