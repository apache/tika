#!/usr/bin/env python3
"""
Filter top-10-language contamination from a preprocessed data directory.

Reads the output of TrainLanguageModel --prep-only:
  <prep_dir>/pool/<lang>   one sentence per line, filename = ISO 639-3 code
  <prep_dir>/dev.txt       lang TAB text, one per line
  <prep_dir>/test_raw.txt  lang TAB text, one per line
  <prep_dir>/held_out.txt  lang TAB text, one per line

For every sentence, if fastText predicts one of the top-10 dominant internet
languages with confidence >= THRESHOLD, and the labeled language differs (and
the pair is not a known confusable), the sentence is removed.

Output mirrors the input structure under <out_dir>:
  <out_dir>/pool/<lang>
  <out_dir>/dev.txt
  <out_dir>/test_raw.txt
  <out_dir>/held_out.txt

A removal log for each file is written to <out_dir>/removed/.

Usage:
    python3 filter_contamination.py <prep_dir> [<out_dir>]

Defaults:
    prep_dir = ~/datasets/madlad/lang-detect/preprocessed
    out_dir  = ~/datasets/madlad/lang-detect/preprocessed_clean
"""

import os
import sys
from pathlib import Path

import fasttext

fasttext.FastText.eprint = lambda x: None   # suppress noisy load message

# ── Top-10 dominant internet languages most likely to contaminate MADLAD ──
TOP10_FT_TO_639_3 = {
    "__label__en": "eng",
    "__label__zh": "zho",
    "__label__es": "spa",
    "__label__ar": "ara",
    "__label__pt": "por",
    "__label__ru": "rus",
    "__label__fr": "fra",
    "__label__de": "deu",
    "__label__ja": "jpn",
    "__label__ko": "kor",
}

# ── Confusable groups — loaded from the shared confusables.txt resource ──
# Single source of truth: edit confusables.txt, not this file.
_CONFUSABLES_TXT = (
    Path(__file__).parent
    / "../../main/resources/org/apache/tika/langdetect/charsoup/confusables.txt"
).resolve()


def _load_confusables(path: Path) -> list:
    groups = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#"):
                groups.append(set(line.split(",")))
    return groups


CONFUSABLE_GROUPS = _load_confusables(_CONFUSABLES_TXT)

THRESHOLD = 0.80

# ── Build lang → confusable-group lookup ──
_CONFUSABLE_MAP = {}
for _g in CONFUSABLE_GROUPS:
    for _lang in _g:
        _CONFUSABLE_MAP[_lang] = _g


def is_confusable(lang_a, lang_b):
    g = _CONFUSABLE_MAP.get(lang_a)
    return g is not None and lang_b in g


def should_remove(model, lang, text):
    """Return True if fastText confidently identifies text as a different top-10 language."""
    labels, probs = model.predict(text.replace("\n", " "), k=1)
    ft_lang = TOP10_FT_TO_639_3.get(labels[0])
    return (
        ft_lang is not None
        and ft_lang != lang
        and float(probs[0]) >= THRESHOLD
        and not is_confusable(lang, ft_lang)
    )


# ── Pool filter: per-language files, one sentence per line, no header ──

def filter_pool_file(model, lang, in_path, out_path, removed_path):
    kept = removed = skipped = 0
    os.makedirs(os.path.dirname(out_path),     exist_ok=True)
    os.makedirs(os.path.dirname(removed_path), exist_ok=True)

    with open(in_path,      encoding="utf-8") as fin, \
         open(out_path,     "w", encoding="utf-8") as fout, \
         open(removed_path, "w", encoding="utf-8") as frem:
        for raw in fin:
            line = raw.rstrip("\n")
            if not line:
                skipped += 1
                continue
            if should_remove(model, lang, line):
                frem.write(line + "\n")
                removed += 1
            else:
                fout.write(line + "\n")
                kept += 1

    total = kept + removed + skipped
    pct   = 100 * removed / max(total, 1)
    return total, kept, removed, pct


def filter_pool(model, pool_in, pool_out, removed_dir):
    print(f"\n── Pool: {pool_in}")
    print(f"         → {pool_out}")
    os.makedirs(pool_out, exist_ok=True)

    grand_total = grand_kept = grand_removed = 0
    for fname in sorted(f for f in os.listdir(pool_in) if not f.startswith(".")):
        in_path  = os.path.join(pool_in,   fname)
        out_path = os.path.join(pool_out,  fname)
        rem_path = os.path.join(removed_dir, "pool_" + fname)
        if not os.path.isfile(in_path):
            continue
        lang = fname
        total, kept, removed, pct = filter_pool_file(
            model, lang, in_path, out_path, rem_path)
        print(f"  {lang:<8s}  {total:>8,}  →  kept {kept:>8,}  "
              f"removed {removed:>6,}  ({pct:5.1f}%)")
        grand_total   += total
        grand_kept    += kept
        grand_removed += removed

    grand_pct = 100 * grand_removed / max(grand_total, 1)
    print(f"  {'POOL TOTAL':<8s}  {grand_total:>8,}  →  kept {grand_kept:>8,}  "
          f"removed {grand_removed:>6,}  ({grand_pct:5.1f}%)")


# ── Eval filter: lang TAB text files ──

def filter_eval_file(model, in_path, out_path, removed_path):
    kept = removed = skipped = 0
    os.makedirs(os.path.dirname(out_path),     exist_ok=True)
    os.makedirs(os.path.dirname(removed_path), exist_ok=True)

    with open(in_path,      encoding="utf-8") as fin, \
         open(out_path,     "w", encoding="utf-8") as fout, \
         open(removed_path, "w", encoding="utf-8") as frem:
        for raw in fin:
            line = raw.rstrip("\n")
            tab = line.find("\t")
            if tab < 0:
                skipped += 1
                continue
            lang = line[:tab]
            text = line[tab + 1:]
            if should_remove(model, lang, text):
                frem.write(line + "\n")
                removed += 1
            else:
                fout.write(line + "\n")
                kept += 1

    total = kept + removed + skipped
    pct   = 100 * removed / max(total, 1)
    name  = os.path.basename(in_path)
    print(f"  {name:<20s}  {total:>8,}  →  kept {kept:>8,}  "
          f"removed {removed:>6,}  ({pct:5.1f}%)")


def filter_eval_files(model, prep_in, prep_out, removed_dir):
    print(f"\n── Eval files: {prep_in}")
    for fname in ("dev.txt", "test_raw.txt"):
        in_path  = os.path.join(prep_in,     fname)
        out_path = os.path.join(prep_out,    fname)
        rem_path = os.path.join(removed_dir, fname)
        if not os.path.exists(in_path):
            print(f"  {fname}: not found, skipping")
            continue
        filter_eval_file(model, in_path, out_path, rem_path)


# ── Main ──

def main():
    base_prep = os.path.expanduser(
        "~/datasets/madlad/lang-detect/preprocessed")
    base_clean = os.path.expanduser(
        "~/datasets/madlad/lang-detect/preprocessed_clean")

    prep_dir = sys.argv[1] if len(sys.argv) > 1 else base_prep
    out_dir  = sys.argv[2] if len(sys.argv) > 2 else base_clean
    removed_dir = os.path.join(out_dir, "removed")

    model_path = os.path.expanduser(
        "~/datasets/madlad/lang-detect/lid.176.bin")
    if not os.path.exists(model_path):
        print(f"ERROR: fastText model not found: {model_path}")
        print("Download with:")
        print("  curl -L -o ~/datasets/madlad/lang-detect/lid.176.bin "
              "https://dl.fbaipublicfiles.com/fasttext/supervised-models/lid.176.bin")
        sys.exit(1)

    print(f"Loading fastText model: {model_path}")
    model = fasttext.load_model(model_path)
    print(f"Model loaded.")
    print(f"Prep dir  : {prep_dir}")
    print(f"Output dir: {out_dir}")
    print(f"Removed   : {removed_dir}")
    print(f"Threshold : {THRESHOLD}")

    os.makedirs(out_dir,     exist_ok=True)
    os.makedirs(removed_dir, exist_ok=True)

    filter_pool(
        model,
        os.path.join(prep_dir, "pool"),
        os.path.join(out_dir,  "pool"),
        removed_dir,
    )
    filter_eval_files(model, prep_dir, out_dir, removed_dir)

    print("\nDone.")


if __name__ == "__main__":
    main()
