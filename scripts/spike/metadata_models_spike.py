"""Step 1.5 spike: can GLiNER (entities) and a SetFit-style sentence encoder run locally?

This proves the mechanism on one synthetic email. It does NOT train or evaluate; accuracy work is
steps 1.6-1.8. Run inside the spike venv:

    venv/bin/python -I scripts/spike/metadata_models_spike.py <gold.json> [--small]

It reads only `currentText` from a synthetic gold file (data we authored, safe to display).
Models download to the Hugging Face cache on first run.
"""
import argparse
import json
import sys
import time


def timed(label, fn):
    start = time.perf_counter()
    result = fn()
    print(f"[time] {label}: {time.perf_counter() - start:.1f}s", flush=True)
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("gold", help="a synthetic .gold.json file")
    parser.add_argument("--small", action="store_true", help="use the small English GLiNER model")
    args = parser.parse_args()

    text = json.load(open(args.gold, encoding="utf-8"))["currentText"]
    print(f"[input] {len(text)} chars from {args.gold}")
    print(f"[input] text: {text!r}\n")

    report = {"python": sys.version.split()[0]}

    # --- GLiNER: zero-shot entity extraction, no training needed ---
    from gliner import GLiNER
    gliner_name = "urchade/gliner_small-v2.1" if args.small else "urchade/gliner_multi-v2.1"
    model = timed(f"GLiNER load ({gliner_name})", lambda: GLiNER.from_pretrained(gliner_name))
    labels = ["person", "course", "date", "deadline", "identifier", "organization"]
    entities = timed("GLiNER predict", lambda: model.predict_entities(text, labels, threshold=0.5))
    print("[gliner] entities:")
    for e in entities:
        print(f"    {e['label']:12} {e['text']!r}  (score {e['score']:.2f})")
    report["gliner_model"] = gliner_name
    report["gliner_entities"] = [{"label": e["label"], "text": e["text"]} for e in entities]

    # --- SetFit body: the multilingual sentence encoder it builds on ---
    from sentence_transformers import SentenceTransformer
    st_name = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
    encoder = timed(f"SentenceTransformer load ({st_name})", lambda: SentenceTransformer(st_name))
    embedding = timed("encode", lambda: encoder.encode([text]))
    print(f"[setfit] embedding shape: {embedding.shape}")
    report["encoder_model"] = st_name
    report["embedding_dim"] = int(embedding.shape[1])

    print("\n[summary] " + json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
