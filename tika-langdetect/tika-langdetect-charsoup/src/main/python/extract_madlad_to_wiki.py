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
Extract sentences from locally-downloaded MADLAD data and write them into
the wikipedia-dumps corpus structure so that PrepareCorpus picks them up
alongside existing Wikipedia sentences.

The MADLAD files store whole documents as single TSV lines with paragraph
boundaries encoded as the two-character literal sequence \\n (backslash + n).
This script splits on that sequence, applies quality filters identical to
download_madlad.py, and writes lineNum<TAB>sentence output files.

Usage:
    python extract_madlad_to_wiki.py \\
        --madlad-dir  ~/datasets/madlad/data \\
        --wiki-dir    ~/datasets/wikipedia-dumps \\
        --langs mya xho nya smo sot tet orm udm tir hil ewe tso aka tsn aym \\
        [--min-chars 50] [--max-chars 1000] [--max-per-lang 500000] [--dry-run]

Output:
    For each language code, writes (or appends to):
        <wiki-dir>/<lang>/sentences_madlad.txt

    The directory is created if it does not exist. If sentences_madlad.txt
    already exists it is overwritten.
"""

import argparse
import os
import re
import unicodedata
from pathlib import Path

# ---------------------------------------------------------------------------
# Quality filter constants (mirror download_madlad.py defaults)
# ---------------------------------------------------------------------------
DEFAULT_MIN_CHARS = 50
DEFAULT_MAX_CHARS = 1000
DEFAULT_MAX_PER_LANG = 500_000

# Fraction of characters that must be alphabetic (Unicode category L*)
MIN_ALPHA_RATIO = 0.40

# Reject lines that contain URLs
URL_RE = re.compile(r'https?://|www\.', re.IGNORECASE)

# Reject lines that look like file paths / breadcrumbs (many slashes or pipes)
JUNK_RE = re.compile(r'([|/\\]{3,})')

# Script ranges for languages with distinctive non-Latin scripts
# Used to require that ≥40 % of letters are in the expected script block.
SCRIPT_RANGES = {
    'mya': ('\u1000', '\u109F'),   # Myanmar
    'tir': ('\u1200', '\u137F'),   # Ethiopic  (also covers Amharic, etc.)
    'udm': ('\u0400', '\u04FF'),   # Cyrillic
}


def alpha_ratio(text: str) -> float:
    letters = sum(1 for c in text if unicodedata.category(c).startswith('L'))
    return letters / max(len(text), 1)


def script_ratio(text: str, lo: str, hi: str) -> float:
    letters = sum(1 for c in text if unicodedata.category(c).startswith('L'))
    in_script = sum(1 for c in text if lo <= c <= hi)
    return in_script / max(letters, 1)


def is_good(line: str, lang: str, min_chars: int, max_chars: int) -> bool:
    if not (min_chars <= len(line) <= max_chars):
        return False
    if URL_RE.search(line):
        return False
    if JUNK_RE.search(line):
        return False
    if alpha_ratio(line) < MIN_ALPHA_RATIO:
        return False
    if lang in SCRIPT_RANGES:
        lo, hi = SCRIPT_RANGES[lang]
        if script_ratio(line, lo, hi) < 0.40:
            return False
    return True


def extract_lang(madlad_dir: Path, lang: str,
                 min_chars: int, max_chars: int,
                 max_per_lang: int) -> list[str]:
    """Return up to max_per_lang clean sentences for lang."""
    lang_dir = madlad_dir / lang
    if not lang_dir.exists():
        print(f"  [{lang}] NOT FOUND in {madlad_dir}")
        return []

    sentences = []
    for fname in sorted(lang_dir.iterdir()):
        if not fname.suffix == '.txt':
            continue
        with open(fname, encoding='utf-8', errors='ignore') as f:
            for raw_line in f:
                # Format: lineNum<TAB>document_text
                # Document text uses literal \n (two chars) as paragraph sep.
                parts = raw_line.split('\t', 1)
                doc_text = parts[-1] if len(parts) > 1 else raw_line
                for para in doc_text.split('\\n'):
                    para = para.strip()
                    if is_good(para, lang, min_chars, max_chars):
                        sentences.append(para)
                        if len(sentences) >= max_per_lang:
                            return sentences
    return sentences


def write_output(wiki_dir: Path, lang: str,
                 sentences: list[str], dry_run: bool) -> None:
    out_dir = wiki_dir / lang
    out_file = out_dir / 'sentences_madlad.txt'
    if dry_run:
        print(f"  [{lang}] DRY-RUN: would write {len(sentences):,} sentences → {out_file}")
        return
    out_dir.mkdir(parents=True, exist_ok=True)
    with open(out_file, 'w', encoding='utf-8') as f:
        for i, sent in enumerate(sentences, 1):
            f.write(f"{i}\t{sent}\n")
    print(f"  [{lang}] wrote {len(sentences):,} sentences → {out_file}")


def main():
    parser = argparse.ArgumentParser(
        description="Extract MADLAD sentences into wikipedia-dumps structure.")
    parser.add_argument('--madlad-dir', required=True,
                        help='Root of downloaded MADLAD data (contains lang subdirs)')
    parser.add_argument('--wiki-dir', required=True,
                        help='Root of wikipedia-dumps (sentences_madlad.txt goes here)')
    parser.add_argument('--langs', nargs='+', required=True,
                        help='Language codes to process')
    parser.add_argument('--min-chars', type=int, default=DEFAULT_MIN_CHARS)
    parser.add_argument('--max-chars', type=int, default=DEFAULT_MAX_CHARS)
    parser.add_argument('--max-per-lang', type=int, default=DEFAULT_MAX_PER_LANG)
    parser.add_argument('--dry-run', action='store_true',
                        help='Print what would be written without writing')
    args = parser.parse_args()

    madlad_dir = Path(args.madlad_dir).expanduser()
    wiki_dir   = Path(args.wiki_dir).expanduser()

    print(f"MADLAD source : {madlad_dir}")
    print(f"Wiki dest     : {wiki_dir}")
    print(f"Languages     : {args.langs}")
    print(f"Filters       : min={args.min_chars}  max={args.max_chars}  "
          f"max-per-lang={args.max_per_lang:,}")
    print(f"Dry run       : {args.dry_run}")
    print()

    total = 0
    for lang in args.langs:
        print(f"Processing {lang}...")
        sentences = extract_lang(madlad_dir, lang,
                                 args.min_chars, args.max_chars,
                                 args.max_per_lang)
        write_output(wiki_dir, lang, sentences, args.dry_run)
        total += len(sentences)

    print(f"\nTotal sentences written: {total:,}")


if __name__ == '__main__':
    main()
