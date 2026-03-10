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
Summarize a Wikipedia collection directory produced by collect_wikipedia.py.

Reads <data_dir>/<lang>/sentences.txt files and reports:
  - Sentence count per language
  - Avg/median sentence length (characters)
  - Whether the lang hit the 500k cap or fell short
  - Languages that need renaming (directory name is a sanitized wiki code,
    not a clean ISO 639-3 code)
  - Suggested keep/drop based on --min-sentences threshold

Outputs a TSV summary and a human-readable report to stdout.

Usage:
    python summarize_wikipedia.py ~/datasets/wikipedia-dumps
    python summarize_wikipedia.py ~/datasets/wikipedia-dumps --min-sentences 5000
    python summarize_wikipedia.py ~/datasets/wikipedia-dumps --tsv summary.tsv
"""

import argparse
import statistics
from pathlib import Path

CAP = 500_000
ISO3_RE_LEN = 3   # genuine ISO 639-3 codes are exactly 3 chars


def is_likely_wiki_code(name: str) -> bool:
    """Heuristic: directory names that aren't clean 3-letter ISO 639-3 codes."""
    return len(name) != ISO3_RE_LEN or "_" in name or "-" in name


def read_lang(lang_dir: Path) -> dict:
    sentences_file = lang_dir / "sentences.txt"
    if not sentences_file.exists():
        return None

    lengths = []
    with open(sentences_file, encoding="utf-8") as f:
        for line in f:
            tab = line.find("\t")
            if tab < 0:
                continue
            # Only count lines that start with a number — guards against
            # files written before the embedded-newline fix was applied.
            prefix = line[:tab]
            if not prefix.strip().isdigit():
                continue
            lengths.append(len(line) - tab - 1)

    if not lengths:
        return None

    return {
        "lang":       lang_dir.name,
        "count":      len(lengths),
        "hit_cap":    len(lengths) >= CAP * 0.99,
        "avg_chars":  sum(lengths) / len(lengths),
        "med_chars":  statistics.median(lengths),
        "needs_rename": is_likely_wiki_code(lang_dir.name),
    }


def main():
    parser = argparse.ArgumentParser(
        description="Summarize collected Wikipedia sentence data"
    )
    parser.add_argument(
        "data_dir", type=Path,
        help="Root directory of collect_wikipedia.py output",
    )
    parser.add_argument(
        "--min-sentences", type=int, default=5_000,
        help="Threshold below which a language is flagged for dropping (default: 5000)",
    )
    parser.add_argument(
        "--tsv", type=Path, default=None,
        help="Write per-language TSV to this path",
    )
    args = parser.parse_args()

    rows = []
    for lang_dir in sorted(args.data_dir.iterdir()):
        if not lang_dir.is_dir():
            continue
        r = read_lang(lang_dir)
        if r:
            rows.append(r)

    if not rows:
        print("No language directories found.")
        return

    rows.sort(key=lambda r: -r["count"])

    total_sents  = sum(r["count"] for r in rows)
    at_cap       = sum(1 for r in rows if r["hit_cap"])
    thin         = [r for r in rows if r["count"] < args.min_sentences]
    need_rename  = [r for r in rows if r["needs_rename"]]
    in_progress  = [r for r in rows
                    if not r["hit_cap"] and r["count"] >= args.min_sentences]

    # ── Console report ────────────────────────────────────────────────────────
    print(f"\n{'=' * 68}")
    print(f"Wikipedia collection summary: {args.data_dir}")
    print(f"{'=' * 68}")
    print(f"  Languages collected:  {len(rows)}")
    print(f"  Total sentences:      {total_sents:>12,}")
    print(f"  Hit 500k cap:         {at_cap}")
    print(f"  Under {args.min_sentences:,} (thin):  {len(thin)}")
    print(f"  Need renaming:        {len(need_rename)}")
    print()

    print(f"{'Lang':<12} {'Sentences':>10}  {'Avg':>6}  {'Med':>6}  {'Cap?':>5}  {'Note'}")
    print("-" * 68)
    for r in rows:
        note = ""
        if r["needs_rename"]:
            note += "RENAME "
        if r["count"] < args.min_sentences:
            note += "THIN"
        elif r["hit_cap"]:
            note += "cap"
        print(f"{r['lang']:<12} {r['count']:>10,}  "
              f"{r['avg_chars']:>6.0f}  {r['med_chars']:>6.0f}  "
              f"{'yes' if r['hit_cap'] else 'no':>5}  {note}")

    if thin:
        print()
        print(f"Thin languages (<{args.min_sentences:,} sentences):")
        for r in sorted(thin, key=lambda r: r["count"]):
            print(f"  {r['lang']:<12} {r['count']:>8,}")

    if need_rename:
        print()
        print("Directories needing renaming (not clean ISO 639-3):")
        for r in sorted(need_rename, key=lambda r: r["lang"]):
            print(f"  {r['lang']}")

    # ── TSV output ────────────────────────────────────────────────────────────
    if args.tsv:
        with open(args.tsv, "w", encoding="utf-8") as f:
            f.write("lang\tsentences\thit_cap\tavg_chars\tmed_chars\tneeds_rename\n")
            for r in rows:
                f.write(
                    f"{r['lang']}\t{r['count']}\t{int(r['hit_cap'])}\t"
                    f"{r['avg_chars']:.1f}\t{r['med_chars']:.1f}\t"
                    f"{int(r['needs_rename'])}\n"
                )
        print(f"\nTSV written to: {args.tsv}")


if __name__ == "__main__":
    main()
