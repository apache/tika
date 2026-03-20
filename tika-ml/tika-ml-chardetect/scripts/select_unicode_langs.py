#!/usr/bin/env python3
"""
Select languages for Unicode charset training (UTF-8/16/32).

Strategy:
  1. Sample text from each language directory, tally Unicode blocks.
  2. Classify each language by its dominant (most frequent) block.
  3. For every non-Latin block, include the top language by char count.
  4. For Latin-dominant languages, randomly sample ~20.
  5. Always include LANG_CHARSETS languages (they have legacy mappings).
  6. Write the final list to unicode_langs.txt.
"""

import random
import sys
from collections import defaultdict
from pathlib import Path

SAMPLE_LINES = 500
MIN_BLOCK_CHARS = 50
LATIN_SAMPLE_SIZE = 20
SEED = 42

BLOCKS = {}


def _init_blocks():
    raw = """
    0000..007F Basic Latin
    0080..00FF Latin-1 Supplement
    0100..024F Latin Extended-A/B
    0250..02AF IPA Extensions
    0370..03FF Greek and Coptic
    0400..04FF Cyrillic
    0500..052F Cyrillic Supplement
    0530..058F Armenian
    0590..05FF Hebrew
    0600..06FF Arabic
    0700..074F Syriac
    0780..07BF Thaana
    07C0..07FF NKo
    0800..083F Samaritan
    0900..097F Devanagari
    0980..09FF Bengali
    0A00..0A7F Gurmukhi
    0A80..0AFF Gujarati
    0B00..0B7F Oriya
    0B80..0BFF Tamil
    0C00..0C7F Telugu
    0C80..0CFF Kannada
    0D00..0D7F Malayalam
    0D80..0DFF Sinhala
    0E00..0E7F Thai
    0E80..0EFF Lao
    0F00..0FFF Tibetan
    1000..109F Myanmar
    10A0..10FF Georgian
    1100..11FF Hangul Jamo
    1200..137F Ethiopic
    13A0..13FF Cherokee
    1400..167F Unified Canadian Aboriginal Syllabics
    1680..169F Ogham
    16A0..16FF Runic
    1780..17FF Khmer
    1800..18AF Mongolian
    1900..194F Limbu
    1950..197F Tai Le
    1A00..1A1F Buginese
    1A20..1AAF Tai Tham
    1B00..1B7F Balinese
    1B80..1BBF Sundanese
    1BC0..1BFF Batak
    1C00..1C4F Lepcha
    1C50..1C7F Ol Chiki
    1C80..1C8F Cyrillic Extended-C
    2C00..2C5F Glagolitic
    2D30..2D7F Tifinagh
    2D80..2DDF Ethiopic Extended
    3040..309F Hiragana
    30A0..30FF Katakana
    3100..312F Bopomofo
    3130..318F Hangul Compatibility Jamo
    4E00..9FFF CJK Unified Ideographs
    A000..A48F Yi Syllables
    A4D0..A4FF Lisu
    A500..A63F Vai
    A640..A69F Cyrillic Extended-B
    A6A0..A6FF Bamum
    A800..A82F Syloti Nagri
    A880..A8DF Saurashtra
    A900..A92F Kayah Li
    A980..A9DF Javanese
    AA00..AA5F Cham
    AA80..AADF Tai Viet
    AB70..ABBF Cherokee Supplement
    AC00..D7AF Hangul Syllables
    10300..1032F Old Italic
    10330..1034F Gothic
    10400..1044F Deseret
    10450..1047F Shavian
    10480..104AF Osmanya
    10900..1091F Phoenician
    11000..1107F Brahmi
    11100..1114F Chakma
    11180..111DF Sharada
    11480..114DF Tirhuta
    11580..115FF Siddham
    11600..1165F Modi
    11680..116CF Takri
    11700..1174F Ahom
    11800..1184F Dogra
    16800..16A3F Bamum Supplement
    16A40..16A6F Mro
    16AD0..16AFF Bassa Vah
    16B00..16B8F Pahawh Hmong
    16F00..16F9F Miao
    1E100..1E14F Nyiakeng Puachue Hmong
    1E2C0..1E2FF Wancho
    1E800..1E8DF Mende Kikakui
    1E900..1E95F Adlam
    """
    for line in raw.strip().split('\n'):
        line = line.strip()
        if not line:
            continue
        rng, name = line.split(' ', 1)
        start_s, end_s = rng.split('..')
        BLOCKS[name.strip()] = (int(start_s, 16), int(end_s, 16))


_init_blocks()

LATIN_BLOCKS = {"Basic Latin", "Latin-1 Supplement", "Latin Extended-A/B",
                "IPA Extensions"}


def get_block(cp: int) -> str:
    for name, (start, end) in BLOCKS.items():
        if start <= cp <= end:
            return name
    return "Other"


def sample_blocks(lang_dir: Path) -> dict[str, int]:
    block_counts: dict[str, int] = defaultdict(int)
    lines_read = 0
    for filename in ["sentences_madlad.txt", "sentences_wikipedia.txt"]:
        p = lang_dir / filename
        if not p.exists():
            continue
        with open(p, encoding="utf-8") as f:
            for line in f:
                if lines_read >= SAMPLE_LINES:
                    break
                parts = line.rstrip("\n").split("\t", 1)
                text = parts[1] if len(parts) > 1 else parts[0]
                for ch in text:
                    cp = ord(ch)
                    if cp <= 0x20:
                        continue
                    block_counts[get_block(cp)] += 1
                lines_read += 1
        if lines_read >= SAMPLE_LINES:
            break
    return dict(block_counts)


def data_size_mb(lang_dir: Path) -> float:
    total = 0
    for f in ["sentences_madlad.txt", "sentences_wikipedia.txt"]:
        p = lang_dir / f
        if p.exists():
            total += p.stat().st_size
    return total / 1_048_576


def dominant_block(blocks: dict[str, int]) -> str:
    """Return the most frequent non-'Other' block."""
    best = "Other"
    best_count = 0
    for block, count in blocks.items():
        if block == "Other":
            continue
        if count > best_count:
            best = block
            best_count = count
    return best


def is_latin_dominant(blocks: dict[str, int]) -> bool:
    """True if the dominant block is a Latin block."""
    dom = dominant_block(blocks)
    return dom in LATIN_BLOCKS


def main():
    data_dir = Path.home() / "datasets" / "madlad" / "data"
    out_file = data_dir / "unicode_langs.txt"

    print("Analyzing languages...")
    lang_blocks: dict[str, dict[str, int]] = {}
    lang_sizes: dict[str, float] = {}
    for d in sorted(data_dir.iterdir()):
        if not d.is_dir():
            continue
        lang = d.name
        blocks = sample_blocks(d)
        if blocks:
            lang_blocks[lang] = blocks
            lang_sizes[lang] = data_size_mb(d)

    print(f"  Analyzed {len(lang_blocks)} languages\n")

    # Classify: Latin-dominant vs non-Latin-dominant
    latin_langs = []
    nonlatin_langs = []
    for lang, blocks in lang_blocks.items():
        if is_latin_dominant(blocks):
            latin_langs.append(lang)
        else:
            nonlatin_langs.append(lang)

    print(f"  Latin-dominant: {len(latin_langs)}")
    print(f"  Non-Latin-dominant: {len(nonlatin_langs)}\n")

    selected = set()

    # --- Non-Latin languages ---
    # For each non-Latin block, find the best representative language
    # (highest char count in that block). Include ALL non-Latin-dominant
    # languages since each brings a distinct script signal.
    block_best: dict[str, list[tuple[str, int]]] = defaultdict(list)
    for lang in nonlatin_langs:
        for block, count in lang_blocks[lang].items():
            if block in LATIN_BLOCKS or block == "Other":
                continue
            if count >= MIN_BLOCK_CHARS:
                block_best[block].append((lang, count))

    for block in block_best:
        block_best[block].sort(key=lambda x: -x[1])

    print("Non-Latin block representatives (top per block):")
    for block in sorted(block_best.keys()):
        top = block_best[block][0]
        selected.add(top[0])
        others = [f"{l}({c:,})" for l, c in block_best[block][1:3]]
        others_str = f"  also: {', '.join(others)}" if others else ""
        print(f"  {block:45s} {top[0]:8s} ({top[1]:>9,} chars){others_str}")

    # Also include ALL non-Latin-dominant languages — they each bring
    # distinct byte patterns even if they share a block (e.g. multiple
    # Cyrillic languages have different byte distributions)
    for lang in nonlatin_langs:
        selected.add(lang)

    # --- Latin-dominant languages: random sample ---
    rng = random.Random(SEED)
    rng.shuffle(latin_langs)
    latin_sample = latin_langs[:LATIN_SAMPLE_SIZE]
    for lang in latin_sample:
        selected.add(lang)

    print(f"\nLatin-dominant: sampled {len(latin_sample)} "
          f"of {len(latin_langs)}:")
    for lang in sorted(latin_sample):
        print(f"  {lang:12s} {lang_sizes.get(lang, 0):8.1f} MB")

    # --- Always include LANG_CHARSETS languages ---
    lang_charsets = [
        "eng", "deu", "fra", "ita", "spa", "nld", "por", "dan", "swe",
        "nob", "fin", "isl", "cat", "glg", "eus", "afr", "swh", "ind",
        "msa", "lav", "lit", "est", "mlt", "tur", "ces", "pol", "hrv",
        "slk", "slv", "hun", "ron", "bos", "sqi", "rus", "ukr", "bul",
        "bel", "mkd", "srp", "ara", "urd", "fas", "pus", "heb", "ell",
        "vie", "jpn", "zho", "kor", "tha", "yue", "zho-trad",
    ]
    added_from_charsets = 0
    for lang in lang_charsets:
        if lang in lang_blocks and lang not in selected:
            selected.add(lang)
            added_from_charsets += 1

    print(f"\nAdded {added_from_charsets} LANG_CHARSETS languages "
          f"not already selected")

    # --- Summary ---
    print(f"\n{'='*60}")
    print(f"Total selected: {len(selected)} languages\n")

    # Count blocks covered
    all_blocks = set()
    for lang in selected:
        for block, count in lang_blocks.get(lang, {}).items():
            if block not in LATIN_BLOCKS and block != "Other":
                if count >= MIN_BLOCK_CHARS:
                    all_blocks.add(block)
    print(f"Non-Latin Unicode blocks covered: {len(all_blocks)}\n")

    for lang in sorted(selected):
        dom = dominant_block(lang_blocks.get(lang, {}))
        size = lang_sizes.get(lang, 0)
        print(f"  {lang:12s} {size:8.1f} MB  dominant: {dom}")

    # Write
    with open(out_file, "w") as f:
        f.write("# Languages for Unicode charset training (UTF-8/16/32)\n")
        f.write("# Generated by select_unicode_langs.py\n")
        f.write("# One language code per line\n")
        for lang in sorted(selected):
            f.write(f"{lang}\n")

    print(f"\nWrote {len(selected)} languages to {out_file}")


if __name__ == "__main__":
    main()
