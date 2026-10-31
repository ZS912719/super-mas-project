"""Train the speech-act classifier offline, compare it with a simpler head-only baseline, and export
it for Java. See docs/reports/step-1.7.md.

    tmp/venv/bin/python -I scripts/training/train_setfit.py cv DEV_DIR OUT.json [--only-fold N]
    tmp/venv/bin/python -I scripts/training/train_setfit.py export DEV_DIR OUT_DIR [--model headOnly|setfit]

Run both under a memory cap so that overshooting kills only this job:

    systemd-run --user --scope -p MemoryMax=3.5G -p MemorySwapMax=0 tmp/venv/bin/python -I ...

`cv` runs every model in its own child process, so each one returns its memory when it ends. Finished
folds are kept in OUT.json.work and skipped when the command is run again.

Reads only the dev folder, never the frozen test set. Uses `.gold.json` where a reviewed label exists,
otherwise the unreviewed draft, and records how many of each were used.
"""
import argparse
import hashlib
import json
import os
import random
import resource
import subprocess
import sys
import time
from pathlib import Path

ACTS = ["request", "propose", "commit", "deliver"]
BASE_MODEL = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
SEED = 20261031
FOLDS = 5


def load(directory):
    rows, reviewed, drafts = [], 0, 0
    for path in sorted(Path(directory).glob("*.eml")):
        stem = path.name[:-4]
        gold = path.with_name(stem + ".gold.json")
        draft = path.with_name(stem + ".gold.draft.json")
        if gold.exists():
            label, reviewed = json.loads(gold.read_text()), reviewed + 1
        else:
            label, drafts = json.loads(draft.read_text()), drafts + 1
        if label["currentText"].strip():  # Nothing to classify in a pure forward.
            rows.append((stem, label["currentText"], [int(a in label["speechActs"]) for a in ACTS]))
    return rows, {"reviewedLabels": reviewed, "draftLabels": drafts, "usedEmails": len(rows)}


def f1_table(truth, predicted):
    table = {}
    for i, act in enumerate(ACTS):
        tp = sum(t[i] and p[i] for t, p in zip(truth, predicted))
        fp = sum((not t[i]) and p[i] for t, p in zip(truth, predicted))
        fn = sum(t[i] and (not p[i]) for t, p in zip(truth, predicted))
        table[act] = {"tp": tp, "fp": fp, "fn": fn, "f1": 2 * tp / (2 * tp + fp + fn) if tp else 0.0}
    table["macroF1"] = sum(table[a]["f1"] for a in ACTS) / len(ACTS)
    return table


def folds(rows, count):
    order = list(range(len(rows)))
    random.Random(SEED).shuffle(order)
    return [order[i::count] for i in range(count)]


def peak_mb():
    return round(resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024)


def train_setfit(texts, labels, batch_size):
    from datasets import Dataset
    from setfit import SetFitModel, Trainer, TrainingArguments
    model = SetFitModel.from_pretrained(BASE_MODEL, multi_target_strategy="one-vs-rest",
                                        head_params={"class_weight": "balanced", "max_iter": 1000})
    # The word table is 82% of the weights. Training it costs about 1.2 GB in gradients and optimizer state.
    model.model_body[0].auto_model.embeddings.word_embeddings.weight.requires_grad_(False)
    arguments = TrainingArguments(batch_size=batch_size, num_iterations=5, num_epochs=1, seed=SEED,
                                  report_to="none", save_strategy="no", show_progress_bar=False)
    trainer = Trainer(model=model, args=arguments,
                      train_dataset=Dataset.from_dict({"text": texts, "label": labels}))
    trainer.train()
    return model


def train_head_only(encoder, texts, labels):
    from sklearn.linear_model import LogisticRegression
    from sklearn.multiclass import OneVsRestClassifier
    head = OneVsRestClassifier(LogisticRegression(class_weight="balanced", max_iter=1000))
    head.fit(encoder.encode(texts), labels)
    return head


def split(rows, number):
    held = folds(rows, FOLDS)[number]
    held_set = set(held)
    return [rows[i] for i in range(len(rows)) if i not in held_set], [rows[i] for i in held]


def cv_part(args):
    """Child process: trains one model on one fold and saves its predictions."""
    rows, _ = load(args.dev_dir)
    train, test = split(rows, args.fold)
    texts, labels, held_texts = [r[1] for r in train], [r[2] for r in train], [r[1] for r in test]
    started = time.time()
    if args.kind == "setfit":
        predicted = train_setfit(texts, labels, args.batch_size).predict(held_texts).tolist()
    else:
        from sentence_transformers import SentenceTransformer
        encoder = SentenceTransformer(BASE_MODEL)
        predicted = train_head_only(encoder, texts, labels).predict(encoder.encode(held_texts)).tolist()
    target = Path(args.target)
    partial = target.with_suffix(".partial")
    partial.write_text(json.dumps({"key": args.key, "predicted": predicted, "peakMb": peak_mb(),
                                   "seconds": round(time.time() - started)}))
    partial.replace(target)  # A crash can never leave a half-written part that looks finished.


def run_part(args, rows, work, number, kind):
    target = work / f"{kind}-{number}.json"
    # A part may only be reused if it came from the same emails, labels and settings.
    key = hashlib.sha256(json.dumps([rows, args.batch_size, kind, number]).encode()).hexdigest()
    if target.exists():
        part = json.loads(target.read_text())
        if part["key"] == key:
            print(f"{kind} fold {number + 1}: already done", flush=True)
            return part
    outcome = subprocess.run([sys.executable, "-I", __file__, "cv-part", args.dev_dir, str(target), key, kind,
                              str(number), "--batch-size", str(args.batch_size)])
    if outcome.returncode:
        sys.exit(f"{kind} fold {number + 1} failed with exit code {outcome.returncode}; "
                 "-9 usually means the memory cap. Finished parts are kept, so run the command again.")
    part = json.loads(target.read_text())
    print(f"{kind} fold {number + 1}: {part['seconds']}s, peak {part['peakMb']} MB", flush=True)
    return part


def cv(args):
    rows, provenance = load(args.dev_dir)
    work = Path(args.out + ".work")
    work.mkdir(parents=True, exist_ok=True)
    numbers = range(FOLDS) if args.only_fold is None else [args.only_fold]
    parts = {kind: [run_part(args, rows, work, n, kind) for n in numbers] for kind in ("headOnly", "setfit")}
    if args.only_fold is not None:
        return
    truth = [rows[i][2] for n in range(FOLDS) for i in folds(rows, FOLDS)[n]]
    result = {"data": provenance, "batchSize": args.batch_size, "frozenWordEmbeddings": True}
    for kind, runs in parts.items():
        result[kind] = f1_table(truth, [p for run in runs for p in run["predicted"]])
        result[kind + "PeakMb"] = max(run["peakMb"] for run in runs)
        result[kind + "Seconds"] = sum(run["seconds"] for run in runs)
    Path(args.out).write_text(json.dumps(result, indent=2))
    print(json.dumps(result))


def export(args):
    """Trains on all dev data, then writes model.onnx, tokenizer.json and head.json. Both models share this form."""
    import numpy as np
    import onnxruntime
    import torch
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    rows, provenance = load(args.dev_dir)
    texts, labels = [r[1] for r in rows], [r[2] for r in rows]
    if args.model == "setfit":
        model = train_setfit(texts, labels, args.batch_size)
        body, classifier = model.model_body, model.model_head
        python_out = lambda: model.predict(texts).tolist()
    else:
        from sentence_transformers import SentenceTransformer
        body = SentenceTransformer(BASE_MODEL)
        classifier = train_head_only(body, texts, labels)
        python_out = lambda: classifier.predict(body.encode(texts)).tolist()
    transformer = body[0].auto_model.eval()
    tokenizer = body.tokenizer

    class MeanPooled(torch.nn.Module):
        def __init__(self, inner):
            super().__init__()
            self.inner = inner

        def forward(self, input_ids, attention_mask):
            hidden = self.inner(input_ids=input_ids, attention_mask=attention_mask).last_hidden_state
            mask = attention_mask.unsqueeze(-1).float()
            return (hidden * mask).sum(1) / mask.sum(1).clamp(min=1e-9)

    sample = tokenizer(["An example email."], return_tensors="pt", padding=True, truncation=True, max_length=128)
    torch.onnx.export(MeanPooled(transformer), (sample["input_ids"], sample["attention_mask"]),
                      str(out / "model.onnx"), input_names=["input_ids", "attention_mask"],
                      output_names=["embedding"], opset_version=18, dynamo=False,
                      dynamic_axes={"input_ids": {0: "batch", 1: "tokens"},
                                    "attention_mask": {0: "batch", 1: "tokens"}})
    tokenizer.save_pretrained(str(out))
    head = {"acts": ACTS, "maxTokens": 128, "classifiers": [
        {"coefficients": c.coef_[0].tolist(), "intercept": float(c.intercept_[0])}
        for c in classifier.estimators_]}
    (out / "head.json").write_text(json.dumps(head))

    # The exported model must reproduce the Python model's decisions.
    session = onnxruntime.InferenceSession(str(out / "model.onnx"), providers=["CPUExecutionProvider"])
    agree = total = 0
    for text, expected in zip(texts, python_out()):
        encoded = tokenizer([text], return_tensors="np", truncation=True, max_length=128)
        embedding = session.run(None, {"input_ids": encoded["input_ids"].astype(np.int64),
                                       "attention_mask": encoded["attention_mask"].astype(np.int64)})[0][0]
        decided = [int(np.dot(embedding, c["coefficients"]) + c["intercept"] > 0) for c in head["classifiers"]]
        agree += decided == list(expected)
        total += 1
    digest = hashlib.sha256("\n".join(texts).encode()).hexdigest()
    version = {"model": args.model, "baseModel": BASE_MODEL, "seed": SEED,
               "setfitBatchSize": args.batch_size if args.model == "setfit" else None,
               "data": provenance, "textsSha256": digest,
               "onnxAgreesWithPython": f"{agree}/{total}", "provisional": provenance["draftLabels"] > 0}
    (out / "version.json").write_text(json.dumps(version, indent=2))
    print(json.dumps(version))
    print(f"peak {peak_mb()} MB", flush=True)
    if agree != total:
        sys.exit("exported model disagrees with the Python model")


def main():
    os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(required=True)
    p = commands.add_parser("cv")
    p.add_argument("dev_dir")
    p.add_argument("out")
    p.add_argument("--only-fold", type=int, help="Run just this fold (0-based), to measure memory first.")
    p.add_argument("--batch-size", type=int, default=16)
    p.set_defaults(run=cv)
    p = commands.add_parser("cv-part", help="Internal: one model on one fold.")
    p.add_argument("dev_dir")
    p.add_argument("target")
    p.add_argument("key")
    p.add_argument("kind", choices=["headOnly", "setfit"])
    p.add_argument("fold", type=int)
    p.add_argument("--batch-size", type=int, default=16)
    p.set_defaults(run=cv_part)
    p = commands.add_parser("export")
    p.add_argument("dev_dir")
    p.add_argument("out")
    p.add_argument("--model", choices=["headOnly", "setfit"], default="headOnly")
    p.add_argument("--batch-size", type=int, default=16)
    p.set_defaults(run=export)
    args = parser.parse_args()
    args.run(args)


if __name__ == "__main__":
    main()
