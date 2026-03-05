#!/usr/bin/env python3
"""
Diagnostic: report how many sentences in each non-Latin-script language pool
would be dropped at various script-consistency thresholds, without modifying anything.

Usage:
    python3 check_script_consistency.py <pool_dir> [--threshold 0.80]
"""

import sys
import unicodedata
from pathlib import Path

# ---------------------------------------------------------------------------
# Script-family detectors
# ---------------------------------------------------------------------------

def is_cjk(c: str) -> bool:
    cp = ord(c)
    return (
        0x4E00 <= cp <= 0x9FFF   # CJK Unified Ideographs
        or 0x3400 <= cp <= 0x4DBF  # CJK Extension A
        or 0x20000 <= cp <= 0x2A6DF  # CJK Extension B
        or 0xF900 <= cp <= 0xFAFF  # CJK Compatibility
        or 0x3040 <= cp <= 0x309F  # Hiragana
        or 0x30A0 <= cp <= 0x30FF  # Katakana
        or 0xAC00 <= cp <= 0xD7AF  # Hangul Syllables
        or 0x1100 <= cp <= 0x11FF  # Hangul Jamo
    )

def is_arabic(c: str) -> bool:
    cp = ord(c)
    return (
        0x0600 <= cp <= 0x06FF  # Arabic
        or 0x0750 <= cp <= 0x077F  # Arabic Supplement
        or 0xFB50 <= cp <= 0xFDFF  # Arabic Presentation Forms-A
        or 0xFE70 <= cp <= 0xFEFF  # Arabic Presentation Forms-B
    )

def is_cyrillic(c: str) -> bool:
    cp = ord(c)
    return 0x0400 <= cp <= 0x04FF or 0x0500 <= cp <= 0x052F

def is_devanagari(c: str) -> bool:
    cp = ord(c)
    return 0x0900 <= cp <= 0x097F

def is_georgian(c: str) -> bool:
    cp = ord(c)
    return 0x10A0 <= cp <= 0x10FF or 0x2D00 <= cp <= 0x2D2F

def is_armenian(c: str) -> bool:
    cp = ord(c)
    return 0x0530 <= cp <= 0x058F

def is_greek(c: str) -> bool:
    cp = ord(c)
    return 0x0370 <= cp <= 0x03FF or 0x1F00 <= cp <= 0x1FFF

def is_hebrew(c: str) -> bool:
    cp = ord(c)
    return 0x0590 <= cp <= 0x05FF or 0xFB1D <= cp <= 0xFB4F

def is_thai(c: str) -> bool:
    return 0x0E00 <= ord(c) <= 0x0E7F

def is_khmer(c: str) -> bool:
    return 0x1780 <= ord(c) <= 0x17FF

def is_myanmar(c: str) -> bool:
    return 0x1000 <= ord(c) <= 0x109F

def is_ethiopic(c: str) -> bool:
    return 0x1200 <= ord(c) <= 0x137F

def is_tibetan(c: str) -> bool:
    return 0x0F00 <= ord(c) <= 0x0FFF

def is_gurmukhi(c: str) -> bool:
    return 0x0A00 <= ord(c) <= 0x0A7F

def is_bengali(c: str) -> bool:
    return 0x0980 <= ord(c) <= 0x09FF

def is_telugu(c: str) -> bool:
    return 0x0C00 <= ord(c) <= 0x0C7F

def is_kannada(c: str) -> bool:
    return 0x0C80 <= ord(c) <= 0x0CFF

def is_malayalam(c: str) -> bool:
    return 0x0D00 <= ord(c) <= 0x0D7F

def is_sinhala(c: str) -> bool:
    return 0x0D80 <= ord(c) <= 0x0DFF

def is_lao(c: str) -> bool:
    return 0x0E80 <= ord(c) <= 0x0EFF

# ---------------------------------------------------------------------------
# Language → expected script detector
# ---------------------------------------------------------------------------

LANG_SCRIPT: dict[str, tuple[str, callable]] = {
    # CJK
    "zho": ("CJK",       is_cjk),
    "jpn": ("CJK",       is_cjk),
    "kor": ("CJK",       is_cjk),
    # Arabic
    "ara": ("Arabic",    is_arabic),
    "fas": ("Arabic",    is_arabic),
    "urd": ("Arabic",    is_arabic),
    "pus": ("Arabic",    is_arabic),
    "ckb": ("Arabic",    is_arabic),
    "uig": ("Arabic",    is_arabic),
    "snd": ("Arabic",    is_arabic),
    "pan": ("Gurmukhi",  is_gurmukhi),  # Punjabi uses Gurmukhi in MADLAD
    "kaz": ("Cyrillic",  is_cyrillic), # also uses Cyrillic in MADLAD
    # Cyrillic
    "rus": ("Cyrillic",  is_cyrillic),
    "ukr": ("Cyrillic",  is_cyrillic),
    "bul": ("Cyrillic",  is_cyrillic),
    "bel": ("Cyrillic",  is_cyrillic),
    "mkd": ("Cyrillic",  is_cyrillic),
    "srp": ("Cyrillic",  is_cyrillic),
    "bak": ("Cyrillic",  is_cyrillic),
    "tat": ("Cyrillic",  is_cyrillic),
    "sah": ("Cyrillic",  is_cyrillic),
    "chv": ("Cyrillic",  is_cyrillic),
    "bua": ("Cyrillic",  is_cyrillic),
    "kir": ("Cyrillic",  is_cyrillic),
    "myv": ("Cyrillic",  is_cyrillic),
    "mdf": ("Cyrillic",  is_cyrillic),
    "krc": ("Cyrillic",  is_cyrillic),
    "ava": ("Cyrillic",  is_cyrillic),
    "che": ("Cyrillic",  is_cyrillic),
    "oss": ("Cyrillic",  is_cyrillic),
    "kom": ("Cyrillic",  is_cyrillic),
    "udm": ("Cyrillic",  is_cyrillic),
    "kjh": ("Cyrillic",  is_cyrillic),
    "kum": ("Cyrillic",  is_cyrillic),
    "mrj": ("Cyrillic",  is_cyrillic),
    "chm": ("Cyrillic",  is_cyrillic),
    "inh": ("Cyrillic",  is_cyrillic),
    "kbd": ("Cyrillic",  is_cyrillic),
    "mon": ("Cyrillic",  is_cyrillic),
    # Devanagari
    "hin": ("Devanagari", is_devanagari),
    "mar": ("Devanagari", is_devanagari),
    "nep": ("Devanagari", is_devanagari),
    "san": ("Devanagari", is_devanagari),
    # Other distinct scripts
    "kat": ("Georgian",  is_georgian),
    "hye": ("Armenian",  is_armenian),
    "ell": ("Greek",     is_greek),
    "heb": ("Hebrew",    is_hebrew),
    "ydd": ("Hebrew",    is_hebrew),
    "tha": ("Thai",      is_thai),
    "khm": ("Khmer",     is_khmer),
    "mya": ("Myanmar",   is_myanmar),
    "amh": ("Ethiopic",  is_ethiopic),
    "tir": ("Ethiopic",  is_ethiopic),
    # Indic
    "pan": ("Gurmukhi",  is_gurmukhi),
    "ben": ("Bengali",   is_bengali),
    "asm": ("Bengali",   is_bengali),   # Assamese uses Bengali script
    "tel": ("Telugu",    is_telugu),
    "kan": ("Kannada",   is_kannada),
    "mal": ("Malayalam", is_malayalam),
    "sin": ("Sinhala",   is_sinhala),
    # Southeast Asian
    "lao": ("Lao",       is_lao),
}


def script_consistency(line: str, checker) -> float:
    """Fraction of letter characters matching the expected script."""
    letters = [c for c in line if c.isalpha()]
    if not letters:
        return 1.0
    return sum(1 for c in letters if checker(c)) / len(letters)


def check_pool_file(path: Path, script_name: str, checker,
                    threshold: float) -> tuple[int, int, list[float]]:
    """Returns (would_drop, total, consistency_samples)."""
    would_drop = 0
    total = 0
    purities = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line:
                continue
            total += 1
            p = script_consistency(line, checker)
            purities.append(p)
            if p < threshold:
                would_drop += 1
    return would_drop, total, purities


def main():
    if len(sys.argv) < 2:
        print("Usage: check_script_consistency.py <pool_dir> [--threshold 0.80]",
              file=sys.stderr)
        sys.exit(1)

    pool_dir = Path(sys.argv[1])
    threshold = 0.80
    for i, arg in enumerate(sys.argv[2:]):
        if arg == "--threshold" and i + 3 < len(sys.argv):
            threshold = float(sys.argv[i + 3])
        elif arg.startswith("--threshold="):
            threshold = float(arg.split("=")[1])

    print(f"Pool dir : {pool_dir}")
    print(f"Threshold: {threshold:.0%}  (drop sentences below this consistency)")
    print()
    print(f"{'Lang':<8}  {'Script':<12}  {'Total':>8}  {'Drop':>7}  {'Drop%':>7}  "
          f"{'MedConsistency':>10}  {'Min':>7}")
    print("-" * 72)

    results = []
    for lang, (script_name, checker) in sorted(LANG_SCRIPT.items()):
        pool_file = pool_dir / lang
        if not pool_file.exists():
            continue
        drop, total, purities = check_pool_file(
            pool_file, script_name, checker, threshold)
        if total == 0:
            continue
        purities.sort()
        median = purities[len(purities) // 2]
        min_p   = purities[0]
        results.append((lang, script_name, total, drop, drop / total,
                        median, min_p))

    results.sort(key=lambda r: r[4], reverse=True)  # sort by drop%
    for lang, script, total, drop, drop_pct, median, min_p in results:
        print(f"{lang:<8}  {script:<12}  {total:>8,}  {drop:>7,}  "
              f"{drop_pct:>6.1%}  {median:>10.1%}  {min_p:>6.1%}")

    print()
    total_drop = sum(r[3] for r in results)
    total_sents = sum(r[2] for r in results)
    print(f"Total sentences checked: {total_sents:,}")
    print(f"Would drop at {threshold:.0%}: {total_drop:,} "
          f"({total_drop/total_sents:.1%})")


if __name__ == "__main__":
    main()
