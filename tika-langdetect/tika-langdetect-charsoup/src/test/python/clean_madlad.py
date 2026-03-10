#!/usr/bin/env python3
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
Clean MADLAD supplement data and write sentences_madlad.txt into the
wikipedia-dumps language directory.

For each language:
  1. Read sentences_madlad.txt from the MADLAD source directory
  2. Split each Leipzig line's text on embedded \\n (MADLAD stores paragraphs)
  3. Apply same cleaning as collect_wikipedia.py:
       - Whitespace normalization
       - Length filter (MIN_CHARS / MAX_CHARS)
       - Dirty-pattern filter (URLs, wiki markup, ISBN, etc.)
       - Alphabetic ratio filter
       - fastText top-10 contamination filter
  4. Exact-deduplicate, also excluding any sentence already in sentences.txt
  5. Write output as sentences_madlad.txt in the wikipedia-dumps language dir

Usage:
    python3 clean_madlad.py div lus cnh kha
    python3 clean_madlad.py --all
    python3 clean_madlad.py div --madlad-dir ~/datasets/madlad/data \\
                                --wiki-dir  ~/datasets/wikipedia-dumps \\
                                --ft-model  ~/datasets/lid.176.bin
"""

import argparse
import logging
import re
from pathlib import Path

import fasttext

fasttext.FastText.eprint = lambda x: None

log = logging.getLogger(__name__)
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
    datefmt="%H:%M:%S",
)

# ── Cleaning parameters (keep in sync with collect_wikipedia.py) ──────────────
MIN_CHARS = 50
MAX_CHARS = 500
MIN_ALPHA_RATIO = 0.5

_DIRTY = re.compile(
    r'https?://|www\.'
    r'|{{|\[\[|\]\]'
    r'|^\s*[|!]'
    r'|\(PDF\)'
    r'|^\([a-z]{2,3}(?:,\s*[a-z]{2,3})*\)\s'
    r'|\(:[a-z]{2,3}:'
    r'|\bISBN\b|\bISSN\b'
    r'|Lorem ipsum'
)

# ── fastText top-10 filter (keep in sync with collect_wikipedia.py) ───────────
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
THRESHOLD = 0.80
THRESHOLD_EN = 0.65

# ── Confusables ───────────────────────────────────────────────────────────────
_CONFUSABLES_TXT = (
    Path(__file__).parent
    / "../../main/resources/org/apache/tika/langdetect/charsoup/confusables.txt"
).resolve()


def _load_confusables(path: Path) -> dict[str, set[str]]:
    cmap: dict[str, set[str]] = {}
    if not path.exists():
        log.warning(f"confusables.txt not found: {path}")
        return cmap
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#"):
                group = set(line.split(","))
                for lang in group:
                    cmap[lang] = group
    return cmap


_CONFUSABLE_MAP = _load_confusables(_CONFUSABLES_TXT)


def is_confusable(lang_a: str, lang_b: str) -> bool:
    g = _CONFUSABLE_MAP.get(lang_a)
    return g is not None and lang_b in g


def should_remove(ft_model, iso3: str, text: str) -> bool:
    labels, probs = ft_model.predict(text.replace("\n", " "), k=1)
    ft_iso3 = TOP10_FT_TO_639_3.get(labels[0])
    threshold = THRESHOLD_EN if ft_iso3 == "eng" else THRESHOLD
    return (
        ft_iso3 is not None
        and ft_iso3 != iso3
        and float(probs[0]) >= threshold
        and not is_confusable(iso3, ft_iso3)
    )


def clean(sentence: str) -> str | None:
    s = " ".join(sentence.replace('\\n', ' ').split())
    if not (MIN_CHARS <= len(s) <= MAX_CHARS):
        return None
    if _DIRTY.search(s):
        return None
    if sum(c.isalpha() for c in s) / len(s) < MIN_ALPHA_RATIO:
        return None
    return s


def read_existing(sentences_file: Path) -> set[str]:
    """Load sentences already collected from Wikipedia to avoid duplicates."""
    seen = set()
    if not sentences_file.exists():
        return seen
    with open(sentences_file, encoding="utf-8", errors="replace") as f:
        for line in f:
            tab = line.find("\t")
            if tab >= 0 and line[:tab].strip().isdigit():
                seen.add(line[tab + 1:].rstrip("\n"))
    return seen


def split_lines(text: str) -> list[str]:
    """Split MADLAD paragraph text into sentence candidates.

    MADLAD stores paragraphs with the literal two-character sequence \\n
    (backslash + n) as a separator — NOT actual newlines. Split on that first,
    then further split long lines at sentence boundaries.
    """
    chunks = []
    for line in text.split('\\n'):
        line = line.strip()
        if not line:
            continue
        if len(line) <= MAX_CHARS:
            chunks.append(line)
        else:
            parts = re.split(r'(?<=[.!?])\s+', line)
            current = ""
            for part in parts:
                if not current:
                    current = part
                elif len(current) + 1 + len(part) <= MAX_CHARS:
                    current += " " + part
                else:
                    chunks.append(current)
                    current = part
            if current:
                chunks.append(current)
    return chunks


def clean_lang(iso3: str, madlad_dir: Path, wiki_dir: Path,
               ft_model, max_sentences: int) -> dict:
    src = madlad_dir / iso3 / "sentences_madlad.txt"
    if not src.exists():
        log.warning(f"[{iso3}] No source file: {src}")
        return {"lang": iso3, "error": "no source"}

    out_dir = wiki_dir / iso3
    out_dir.mkdir(parents=True, exist_ok=True)
    out_file = out_dir / "sentences_madlad.txt"

    # Load existing Wikipedia sentences to avoid overlap
    existing = read_existing(out_dir / "sentences.txt")
    log.info(f"[{iso3}] {len(existing):,} existing Wikipedia sentences (excluded from MADLAD)")

    seen = set(existing)
    kept: list[str] = []

    stats = {"lang": iso3, "read": 0, "candidates": 0, "dirty": 0, "ft": 0, "dupe": 0, "kept": 0}

    with open(src, encoding="utf-8", errors="replace") as f:
        for raw_line in f:
            tab = raw_line.find("\t")
            if tab < 0:
                continue
            if not raw_line[:tab].strip().isdigit():
                continue
            stats["read"] += 1
            paragraph = raw_line[tab + 1:].rstrip("\n")

            for candidate in split_lines(paragraph):
                stats["candidates"] += 1
                cleaned = clean(candidate)
                if cleaned is None:
                    stats["dirty"] += 1
                    continue
                if cleaned in seen:
                    stats["dupe"] += 1
                    continue
                if should_remove(ft_model, iso3, cleaned):
                    stats["ft"] += 1
                    continue
                seen.add(cleaned)
                kept.append(cleaned)
                if len(kept) >= max_sentences:
                    break
            if len(kept) >= max_sentences:
                break

    stats["kept"] = len(kept)

    if kept:
        with open(out_file, "w", encoding="utf-8") as f:
            for i, s in enumerate(kept, 1):
                f.write(f"{i}\t{s}\n")
        log.info(
            f"[{iso3}] kept={len(kept):,}  "
            f"(read={stats['read']:,} paragraphs → {stats['candidates']:,} candidates; "
            f"dirty={stats['dirty']:,} ft={stats['ft']:,} dupe={stats['dupe']:,})"
        )
    else:
        log.warning(f"[{iso3}] No sentences survived cleaning — not writing output")

    return stats


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "langs", nargs="*",
        help="ISO 639-3 codes to process (e.g. div lus cnh kha). "
             "Omit to use --all."
    )
    parser.add_argument(
        "--all", action="store_true",
        help="Process every language present in --madlad-dir"
    )
    parser.add_argument(
        "--madlad-dir", type=Path,
        default=Path.home() / "datasets/madlad/data",
        help="Root of MADLAD data (default: ~/datasets/madlad/data)"
    )
    parser.add_argument(
        "--wiki-dir", type=Path,
        default=Path.home() / "datasets/wikipedia-dumps",
        help="Root of wikipedia-dumps (default: ~/datasets/wikipedia-dumps)"
    )
    parser.add_argument(
        "--ft-model", type=Path,
        default=Path.home() / "datasets/lid.176.bin",
        help="Path to fastText lid.176.bin model"
    )
    parser.add_argument(
        "--max-sentences", type=int, default=500_000,
        help="Max sentences per language (default: 500000)"
    )
    args = parser.parse_args()

    if not args.langs and not args.all:
        parser.error("Specify language codes or --all")

    if args.all:
        langs = sorted(d.name for d in args.madlad_dir.iterdir() if d.is_dir())
    else:
        langs = args.langs

    log.info(f"Loading fastText model: {args.ft_model}")
    ft_model = fasttext.load_model(str(args.ft_model))

    results = []
    for iso3 in langs:
        r = clean_lang(iso3, args.madlad_dir, args.wiki_dir, ft_model,
                       args.max_sentences)
        results.append(r)

    total_kept = sum(r.get("kept", 0) for r in results)
    print(f"\nSummary: {len(langs)} languages processed, {total_kept:,} total sentences written")
    for r in sorted(results, key=lambda x: -x.get("kept", 0)):
        if r.get("kept", 0) > 0:
            print(f"  {r.get('lang', '?'):15s}  kept={r['kept']:>8,}")


if __name__ == "__main__":
    main()
