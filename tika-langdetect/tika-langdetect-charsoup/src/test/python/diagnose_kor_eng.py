#!/usr/bin/env python3
"""
Diagnose English→Korean false positives in the CharSoup model.

1. Compute cosine similarity between all class weight vectors, rank where
   eng↔kor sits relative to all other pairs.
2. For Flores English sentences predicted as Korean, show raw pre-softmax
   logits for eng vs kor — to separate "genuine weight-space overlap" from
   "softmax amplification of a tiny gap".

Usage:
    python3 diagnose_kor_eng.py <model.bin> <flores_dev.tsv>
"""

import sys
import struct
import math
import collections

# ---------------------------------------------------------------------------
# Model loading
# ---------------------------------------------------------------------------

def load_model(path):
    with open(path, 'rb') as f:
        magic   = struct.unpack('>I', f.read(4))[0]
        version = struct.unpack('>I', f.read(4))[0]
        assert magic == 0x4C444D31, f"Bad magic: {magic:#010x}"
        assert version in (1, 2), f"Unknown version: {version}"
        num_buckets = struct.unpack('>I', f.read(4))[0]
        num_classes = struct.unpack('>I', f.read(4))[0]
        feature_flags = 0
        if version == 2:
            feature_flags = struct.unpack('>I', f.read(4))[0]

        labels = []
        for _ in range(num_classes):
            length = struct.unpack('>H', f.read(2))[0]
            labels.append(f.read(length).decode('utf-8'))

        scales = list(struct.unpack(f'>{num_classes}f', f.read(4 * num_classes)))
        biases = list(struct.unpack(f'>{num_classes}f', f.read(4 * num_classes)))

        # flat weights: bucket-major [bucket * num_classes + class], INT8 signed
        flat = f.read(num_buckets * num_classes)
        flat_signed = [b if b < 128 else b - 256 for b in flat]

    print(f"Model: {num_classes} classes, {num_buckets} buckets, flags={feature_flags:#04x}")
    return labels, scales, biases, flat_signed, num_buckets, num_classes


def get_class_weight_vector(flat, num_classes, num_buckets, class_idx, scales):
    """Return the dequantized weight vector for one class (length=num_buckets)."""
    scale = scales[class_idx]
    return [scale * flat[b * num_classes + class_idx] for b in range(num_buckets)]


def cosine(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    na  = math.sqrt(sum(x * x for x in a))
    nb  = math.sqrt(sum(x * x for x in b))
    if na == 0 or nb == 0:
        return 0.0
    return dot / (na * nb)


# ---------------------------------------------------------------------------
# Feature extraction (mirrors CharSoupFeatureExtractor / ScriptAwareFeatureExtractor)
# This is a Python port of the Java bigram extraction pipeline.
# It only implements bigrams (no trigrams, no word unigrams) which is the
# baseline. The ScriptAwareFeatureExtractor adds word-unigrams; we approximate
# by ignoring word-unigrams since we can't easily replicate the Java FNV hash
# without the exact Java int overflow semantics. This is sufficient to check
# which buckets get activated.
# ---------------------------------------------------------------------------

FNV_OFFSET = 0x811c9dc5
FNV_PRIME  = 0x01000193
MASK32     = 0xFFFFFFFF
SENTINEL   = ord('_')


def fnv_feed_int(h, value):
    for shift in (0, 8, 16, 24):
        h ^= (value >> shift) & 0xFF
        h = (h * FNV_PRIME) & MASK32
    return h


def fnv_bigram(cp1, cp2):
    h = FNV_OFFSET
    h = fnv_feed_int(h, cp1)
    h = fnv_feed_int(h, cp2)
    return h


def extract_bigrams(text, num_buckets):
    """Return a bucket-count dict (sparse)."""
    counts = collections.defaultdict(int)
    prev_cp = SENTINEL
    prev_was_letter = False

    for ch in text:
        cp = ord(ch)
        # skip transparent chars (nonspacing marks, tatweel, ZWNJ, ZWJ)
        if cp >= 0x0300:
            import unicodedata
            cat = unicodedata.category(ch)
            if cat == 'Mn' or cp in (0x0640, 0x200C, 0x200D):
                continue

        if ch.isalpha():
            lower_cp = ord(ch.lower())
            if prev_was_letter:
                h = fnv_bigram(prev_cp, lower_cp)
                bucket = (h & 0x7FFFFFFF) % num_buckets
                counts[bucket] += 1
            else:
                h = fnv_bigram(SENTINEL, lower_cp)
                bucket = (h & 0x7FFFFFFF) % num_buckets
                counts[bucket] += 1
            prev_cp = lower_cp
            prev_was_letter = True
        else:
            if prev_was_letter:
                h = fnv_bigram(prev_cp, SENTINEL)
                bucket = (h & 0x7FFFFFFF) % num_buckets
                counts[bucket] += 1
            prev_was_letter = False

    if prev_was_letter:
        h = fnv_bigram(prev_cp, SENTINEL)
        bucket = (h & 0x7FFFFFFF) % num_buckets
        counts[bucket] += 1

    return counts


def predict_logits(flat, num_classes, num_buckets, scales, biases, counts):
    dots = [0] * num_classes
    for bucket, cnt in counts.items():
        off = bucket * num_classes
        for c in range(num_classes):
            dots[c] += flat[off + c] * cnt
    logits = [biases[c] + scales[c] * dots[c] for c in range(num_classes)]
    return logits


def normalize_lang(raw):
    keep_script = {'ace_Arab','arb_Latn','bjn_Arab','kas_Deva',
                   'knc_Latn','min_Arab','taq_Tfng'}
    if raw in keep_script:
        return raw
    idx = raw.find('_')
    return raw[:idx] if idx >= 0 else raw


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    if len(sys.argv) < 3:
        print("Usage: diagnose_kor_eng.py <model.bin> <flores_dev.tsv>")
        sys.exit(1)

    model_path  = sys.argv[1]
    flores_path = sys.argv[2]

    labels, scales, biases, flat, num_buckets, num_classes = load_model(model_path)
    label_to_idx = {l: i for i, l in enumerate(labels)}

    eng_idx = label_to_idx.get('eng')
    kor_idx = label_to_idx.get('kor')
    if eng_idx is None or kor_idx is None:
        print("ERROR: model doesn't have 'eng' or 'kor' label")
        sys.exit(1)

    # -----------------------------------------------------------------------
    # Part 1: Cosine similarity between all class pairs — where does eng↔kor rank?
    # -----------------------------------------------------------------------
    print("\n=== Part 1: Weight-vector cosine similarities ===")
    print("Building class weight vectors (dequantized)...")
    class_vecs = []
    for c in range(num_classes):
        class_vecs.append(get_class_weight_vector(flat, num_classes, num_buckets, c, scales))

    eng_vec = class_vecs[eng_idx]
    kor_vec = class_vecs[kor_idx]
    eng_kor_cos = cosine(eng_vec, kor_vec)
    print(f"eng↔kor cosine similarity: {eng_kor_cos:.4f}")

    # Sample 500 random pairs to get a distribution
    import random
    random.seed(42)
    all_pairs = [(i, j) for i in range(num_classes) for j in range(i+1, num_classes)]
    sample = random.sample(all_pairs, min(2000, len(all_pairs)))
    sample_sims = sorted([cosine(class_vecs[i], class_vecs[j]) for i, j in sample], reverse=True)

    above = sum(1 for s in sample_sims if s >= eng_kor_cos)
    print(f"Of {len(sample_sims)} random pairs: {above} have cosine ≥ eng↔kor ({eng_kor_cos:.4f})")
    print(f"eng↔kor cosine percentile (high=similar): top {100*above/len(sample_sims):.1f}% of pairs")

    # Show top-20 most similar pairs involving eng or kor
    print(f"\nTop-15 pairs by cosine (all pairs, not sampled):")
    eng_kor_pairs = []
    for i in range(num_classes):
        for j in range(i+1, num_classes):
            s = cosine(class_vecs[i], class_vecs[j])
            if labels[i] in ('eng','kor') or labels[j] in ('eng','kor'):
                eng_kor_pairs.append((s, labels[i], labels[j]))
    eng_kor_pairs.sort(reverse=True)
    for s, a, b in eng_kor_pairs[:15]:
        marker = " ← ENG↔KOR" if {a, b} == {'eng', 'kor'} else ""
        print(f"  {a:10s} ↔ {b:10s}  cos={s:.4f}{marker}")

    # -----------------------------------------------------------------------
    # Part 2: For Flores eng→kor mislabeled sentences, show raw logits
    # -----------------------------------------------------------------------
    print("\n=== Part 2: Raw logits for eng sentences predicted as kor ===")
    print("Loading Flores data...")
    examples = []
    with open(flores_path, encoding='utf-8') as f:
        for line in f:
            parts = line.rstrip('\n').split('\t', 1)
            if len(parts) != 2:
                continue
            lang, text = parts
            lang = normalize_lang(lang)
            if lang != 'eng':
                continue
            counts = extract_bigrams(text, num_buckets)
            logits = predict_logits(flat, num_classes, num_buckets, scales, biases, counts)
            best_idx = max(range(num_classes), key=lambda c: logits[c])
            if labels[best_idx] == 'kor':
                eng_logit = logits[eng_idx]
                kor_logit = logits[kor_idx]
                examples.append((kor_logit - eng_logit, eng_logit, kor_logit, text))

    examples.sort(reverse=True)
    print(f"Found {len(examples)} eng sentences predicted as kor by our Python extractor")
    print(f"(Note: Python extractor is approx — missing word-unigrams from ScriptAwareFeatureExtractor)")
    print()
    print(f"{'kor_logit':>10} {'eng_logit':>10} {'gap':>8}  sentence (first 100 chars)")
    print("-" * 80)
    for gap, eng_l, kor_l, text in examples[:20]:
        print(f"  {kor_l:8.3f}   {eng_l:8.3f}  {gap:+7.3f}  {text[:80]}")

    if examples:
        gaps = [g for g, _, _, _ in examples]
        print(f"\nGap stats (kor_logit - eng_logit) over {len(examples)} sentences:")
        print(f"  min={min(gaps):.3f}  median={sorted(gaps)[len(gaps)//2]:.3f}  max={max(gaps):.3f}")


if __name__ == '__main__':
    main()
