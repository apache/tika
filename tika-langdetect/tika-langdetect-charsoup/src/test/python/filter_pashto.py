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


def filter_pashto(sentences_file: Path) -> tuple[int, int, int, int]:
    """Filter at sentence level, keeping only sentences with Pashto-specific chars.

    Each MADLAD document line contains sentences separated by the literal two-character
    sequence '\\n'. This function splits each document into individual sentences, keeps
    only those containing at least one Pashto-specific character, then reassembles the
    surviving sentences back into a document. Documents with no surviving sentences are
    dropped entirely.

    Returns (docs_kept, docs_total, sents_kept, sents_total).
    """
    backup = sentences_file.with_suffix(".txt.bak")
    sentences_file.rename(backup)

    docs_total = docs_kept = 0
    sents_total = sents_kept = 0

    with open(backup, encoding="utf-8") as fin, \
         open(sentences_file, "w", encoding="utf-8") as fout:
        for line in fin:
            docs_total += 1
            tab = line.find("\t")
            if tab < 0:
                continue
            doc_text = line[tab + 1:].rstrip("\n")

            # Split on the literal two-character sequence \n used by MADLAD
            parts = doc_text.split("\\n")
            sents_total += len(parts)

            good = [p for p in parts if any(c in PASHTO_CHARS for c in p)]
            sents_kept += len(good)

            if good:
                docs_kept += 1
                fout.write(f"{docs_kept}\t{'\\n'.join(good)}\n")

    return docs_kept, docs_total, sents_kept, sents_total


def main():
    if len(sys.argv) < 2:
        print("Usage: filter_pashto.py <corpus_dir>", file=sys.stderr)
        print("  Filters <corpus_dir>/pus/sentences.txt in place.", file=sys.stderr)
        sys.exit(1)

    corpus_dir = Path(sys.argv[1])
    pus_dir = corpus_dir / "pus"

    # Accept sentences_madlad.txt (MADLAD format) or sentences.txt (Leipzig format)
    pus_file = None
    for candidate in ("sentences_madlad.txt", "sentences.txt"):
        if (pus_dir / candidate).is_file():
            pus_file = pus_dir / candidate
            break

    if pus_file is None:
        print(f"Error: no sentence file found in {pus_dir}", file=sys.stderr)
        sys.exit(1)

    print(f"Filtering {pus_file} ...")
    docs_kept, docs_total, sents_kept, sents_total = filter_pashto(pus_file)
    docs_removed  = docs_total  - docs_kept
    sents_removed = sents_total - sents_kept
    print(f"  Documents: {docs_total:,} total  →  kept {docs_kept:,}  "
          f"removed {docs_removed:,} ({100 * docs_removed / max(docs_total, 1):.1f}%)")
    print(f"  Sentences: {sents_total:,} total  →  kept {sents_kept:,}  "
          f"removed {sents_removed:,} ({100 * sents_removed / max(sents_total, 1):.1f}%)")
    print(f"  Backup:    {pus_file.with_suffix('.txt.bak')}")


if __name__ == "__main__":
    main()
