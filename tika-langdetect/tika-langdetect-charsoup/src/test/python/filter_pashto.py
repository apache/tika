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
Filter Pashto (pus) corpus to remove sentences that are likely
mislabeled Dari, Persian, or other languages.

Leipzig web crawl data for Pashto has ~18% contamination from
Dari/Persian (which share Arabic script). This filter keeps only
sentences containing at least one Pashto-specific character that
does not appear in Dari, Persian, or Urdu:

    ښ (xe), ځ (dze), ږ (zhe), ټ (te), ډ (dal), ړ (re), ڼ (nun), ۍ (ye)

This is a high-precision heuristic: some genuine short Pashto
sentences will be lost, but the remaining data is much cleaner.

Usage:
    python filter_pashto.py <corpus_dir>

Rewrites <corpus_dir>/pus/sentences.txt in place (with backup).
"""

import sys
from pathlib import Path

PASHTO_CHARS = set("ښځږټډړڼۍ")


def filter_pashto(sentences_file: Path) -> tuple[int, int]:
    """Filter sentences, keeping only those with Pashto-specific chars.

    Returns (kept, total).
    """
    backup = sentences_file.with_suffix(".txt.bak")
    sentences_file.rename(backup)

    total = 0
    kept = 0
    with open(backup, encoding="utf-8") as fin, \
         open(sentences_file, "w", encoding="utf-8") as fout:
        for line in fin:
            total += 1
            tab = line.find("\t")
            text = line[tab + 1:] if tab >= 0 else line
            if any(c in PASHTO_CHARS for c in text):
                fout.write(f"{kept + 1}\t{text}" if tab >= 0 else line)
                kept += 1

    return kept, total


def main():
    if len(sys.argv) < 2:
        print("Usage: filter_pashto.py <corpus_dir>", file=sys.stderr)
        print("  Filters <corpus_dir>/pus/sentences.txt in place.", file=sys.stderr)
        sys.exit(1)

    corpus_dir = Path(sys.argv[1])
    pus_file = corpus_dir / "pus" / "sentences.txt"

    if not pus_file.is_file():
        print(f"Error: {pus_file} not found", file=sys.stderr)
        sys.exit(1)

    print(f"Filtering {pus_file} ...")
    kept, total = filter_pashto(pus_file)
    removed = total - kept
    print(f"  Total:   {total:,}")
    print(f"  Kept:    {kept:,} ({100 * kept / total:.1f}%)")
    print(f"  Removed: {removed:,} ({100 * removed / total:.1f}%)")
    print(f"  Backup:  {pus_file.with_suffix('.txt.bak')}")


if __name__ == "__main__":
    main()
