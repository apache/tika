#!/usr/bin/env python3
"""
One-off script: convert Wikipedia sentence files into MADLAD-compatible format
and place them alongside MADLAD data as sentences_wikipedia.txt.

Wikipedia format (wikipedia-dumps/<lang>/sentences.txt):
    linenum\tsentence text       (one sentence per line, tab-prefixed)
    OR just: sentence text       (plain, no line number)

Output format (madlad/data/<lang>/sentences_wikipedia.txt):
    linenum\tsentence text       (tab-prefixed, matching MADLAD convention)

Special cases:
    - zh_yuewiki/sentences_zh_yue.txt → madlad/data/yue/sentences_wikipedia.txt
    - wikipedia-dumps/zho/sentences.txt → madlad/data/zho-trad/sentences_wikipedia.txt
      (zho Wikipedia stores Traditional Chinese; MADLAD zho is Simplified)
"""

import os
import sys
from pathlib import Path


def convert(src: Path, dst: Path, max_lines: int = 0):
    """Read src, write dst in tab-prefixed format."""
    dst.parent.mkdir(parents=True, exist_ok=True)
    written = 0
    with open(src, encoding="utf-8") as fin, \
         open(dst, "w", encoding="utf-8") as fout:
        for line in fin:
            line = line.rstrip("\n")
            if not line.strip():
                continue
            # If already tab-prefixed (linenum\ttext), keep the text part
            if "\t" in line:
                text = line.split("\t", 1)[1]
            else:
                text = line
            if not text.strip():
                continue
            written += 1
            fout.write(f"{written}\t{text}\n")
            if 0 < max_lines <= written:
                break
    return written


def main():
    home = Path.home()
    wiki_dir = home / "datasets" / "wikipedia-dumps"
    madlad_dir = home / "datasets" / "madlad" / "data"
    yue_file = home / "datasets" / "zh_yuewiki" / "sentences_zh_yue.txt"

    if not wiki_dir.exists():
        print(f"ERROR: {wiki_dir} not found")
        sys.exit(1)

    # Special case: yue (Cantonese Wikipedia)
    if yue_file.exists():
        dst = madlad_dir / "yue" / "sentences_wikipedia.txt"
        n = convert(yue_file, dst)
        print(f"  yue (zh_yuewiki): {n:,} sentences -> {dst}")
    else:
        print(f"  yue: SKIPPED ({yue_file} not found)")

    # Special case: zho Wikipedia = Traditional Chinese -> zho-trad
    zho_wiki = wiki_dir / "zho" / "sentences.txt"
    if zho_wiki.exists():
        dst = madlad_dir / "zho-trad" / "sentences_wikipedia.txt"
        n = convert(zho_wiki, dst)
        print(f"  zho-trad (zho wiki, Traditional): {n:,} sentences -> {dst}")

    # All other Wikipedia languages: put alongside MADLAD data
    skipped = []
    converted = 0
    for lang_dir in sorted(wiki_dir.iterdir()):
        if not lang_dir.is_dir():
            continue
        lang = lang_dir.name
        if lang == "zho":
            continue  # handled above as zho-trad

        src = lang_dir / "sentences.txt"
        if not src.exists():
            continue

        dst = madlad_dir / lang / "sentences_wikipedia.txt"
        if dst.exists():
            size_mb = dst.stat().st_size / 1_048_576
            if size_mb > 1:
                skipped.append(lang)
                continue

        n = convert(src, dst)
        size_mb = dst.stat().st_size / 1_048_576
        print(f"  {lang}: {n:,} sentences ({size_mb:.1f} MB) -> {dst}")
        converted += 1

    print(f"\nConverted: {converted}")
    if skipped:
        print(f"Skipped (already exist): {len(skipped)}")


if __name__ == "__main__":
    main()
