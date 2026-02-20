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
Build charset-detection training data from a Leipzig-format corpus directory
(e.g., one produced by download_madlad.py or download_corpus.py).

For each language, sentences are encoded in every applicable legacy charset
plus UTF-8, UTF-16-LE, and UTF-16-BE.  The charset set mirrors what
Tika's ICU4J-based Icu4jEncodingDetector supports:

  Unicode   : UTF-8, UTF-16-LE, UTF-16-BE, UTF-32-LE, UTF-32-BE
  Multibyte : Shift_JIS, EUC-JP, ISO-2022-JP, EUC-KR, ISO-2022-KR,
              EUC-TW, GB18030, GB2312, GBK, Big5, Big5-HKSCS, HZ
  SBCS      : ISO-8859-1/2/3/4/5/6/7/8/9/13/15,
              windows-1250/1251/1252/1253/1254/1255/1256/1257/1258,
              KOI8-R, KOI8-U, IBM855, IBM866, x-mac-cyrillic,
              TIS-620
  EBCDIC    : IBM500 (Western European),
              IBM420-ltr / IBM420-rtl (Arabic logical/visual order),
              IBM424-ltr / IBM424-rtl (Hebrew logical/visual order)

Output format (one gzipped binary file per charset):
  <output_dir>/
    utf-8.bin.gz
    windows-1251.bin.gz
    ibm500.bin.gz
    ...
  <output_dir>/manifest.json

Each .bin.gz contains back-to-back length-prefixed records:
  [uint16 big-endian: byte length N][N raw bytes]

Chunk size is randomly selected between --min-chunk and --max-chunk bytes
to simulate real-world detection scenarios (short headers vs. long bodies).
Only chunks where >= MIN_ENCODE_RATIO of characters survive encoding are
kept, which filters out texts with too many unencodable codepoints.

Usage:
    python build_charset_training.py <corpus_dir> <output_dir> [options]

Example:
    python build_charset_training.py ~/datasets/leipzig2 ~/datasets/charset_train
"""

import argparse
import gzip
import json
import os
import random
import struct
import sys
from collections import defaultdict
from pathlib import Path

# ---------------------------------------------------------------------------
# Charset → Python codec name
# ---------------------------------------------------------------------------

CHARSET_CODEC: dict[str, str] = {
    "UTF-8":         "utf-8",
    "UTF-16-LE":     "utf-16-le",
    "UTF-16-BE":     "utf-16-be",
    "UTF-32-LE":     "utf-32-le",
    "UTF-32-BE":     "utf-32-be",
    # juniversalchardet detects US-ASCII as a distinct charset
    "US-ASCII":      "ascii",
    "Shift_JIS":     "shift-jis",
    "EUC-JP":        "euc-jp",
    "ISO-2022-JP":   "iso-2022-jp",
    "EUC-KR":        "euc-kr",
    "ISO-2022-KR":   "iso-2022-kr",
    # ISO-2022-CN: Chinese simplified via escape sequences (juniversalchardet)
    # Python codec name varies: 'iso2022_cn' or 'iso-2022-cn'
    "ISO-2022-CN":   "iso2022_cn",
    "GB18030":       "gb18030",
    "GB2312":        "gb2312",
    "GBK":           "gbk",
    "Big5":          "big5",
    "Big5-HKSCS":    "big5hkscs",
    "EUC-TW":        "euc_tw",
    # HZ-GB-2312 is what juniversalchardet calls this; Python codec is 'hz'
    "HZ":            "hz",
    "ISO-8859-1":    "iso-8859-1",
    "ISO-8859-2":    "iso-8859-2",
    "ISO-8859-3":    "iso-8859-3",
    "ISO-8859-4":    "iso-8859-4",
    "ISO-8859-5":    "iso-8859-5",
    "ISO-8859-6":    "iso-8859-6",
    "ISO-8859-7":    "iso-8859-7",
    "ISO-8859-8":    "iso-8859-8",
    "ISO-8859-9":    "iso-8859-9",
    "ISO-8859-13":   "iso-8859-13",
    "ISO-8859-15":   "iso-8859-15",
    "windows-1250":  "cp1250",
    "windows-1251":  "cp1251",
    "windows-1252":  "cp1252",
    "windows-1253":  "cp1253",
    "windows-1254":  "cp1254",
    "windows-1255":  "cp1255",
    "windows-1256":  "cp1256",
    "windows-1257":  "cp1257",
    "windows-1258":  "cp1258",
    "KOI8-R":        "koi8-r",
    "KOI8-U":        "koi8-u",
    "IBM855":        "cp855",
    "IBM866":        "cp866",
    "x-mac-cyrillic": "mac_cyrillic",
    "TIS-620":       "cp874",
    "IBM500":        "cp500",
    "IBM424-ltr":    "cp424",   # Hebrew EBCDIC, logical (left-to-right) order
    "IBM424-rtl":    "cp424",   # Hebrew EBCDIC, visual (right-to-left) order — text reversed before encoding
    "IBM420-ltr":    "cp420",   # Arabic EBCDIC, logical order (may not be available on all platforms)
    "IBM420-rtl":    "cp420",   # Arabic EBCDIC, visual order — text reversed before encoding
}

# ---------------------------------------------------------------------------
# Language → applicable legacy charsets (UTF-* added for all langs below)
# ---------------------------------------------------------------------------
# Keys are ISO 639-3 codes matching Leipzig/MADLAD corpus directories.
# UTF-8/16/32 are always appended — list only legacy charsets here.

LANG_CHARSETS: dict[str, list[str]] = {
    # --- Western European ---
    # US-ASCII is included for English since it is the primary ASCII source;
    # pure-ASCII bytes from other Western European texts also contribute via
    # the quality gate (non-ASCII chars are silently dropped by encode).
    "eng": ["US-ASCII", "ISO-8859-1", "ISO-8859-15", "windows-1252", "IBM500"],
    "deu": ["ISO-8859-1", "ISO-8859-15", "windows-1252", "IBM500"],
    "fra": ["ISO-8859-1", "ISO-8859-15", "windows-1252", "IBM500"],
    "ita": ["ISO-8859-1", "ISO-8859-15", "windows-1252", "IBM500"],
    "spa": ["ISO-8859-1", "ISO-8859-15", "windows-1252", "IBM500"],
    "nld": ["ISO-8859-1", "ISO-8859-15", "windows-1252", "IBM500"],
    "por": ["ISO-8859-1", "ISO-8859-15", "windows-1252"],
    "dan": ["ISO-8859-1", "ISO-8859-15", "windows-1252"],
    "swe": ["ISO-8859-1", "ISO-8859-15", "windows-1252"],
    "nob": ["ISO-8859-1", "ISO-8859-15", "windows-1252"],
    "nno": ["ISO-8859-1", "ISO-8859-15", "windows-1252"],
    "fin": ["ISO-8859-1", "ISO-8859-15", "windows-1252"],
    "isl": ["ISO-8859-1", "windows-1252"],
    "cat": ["ISO-8859-1", "ISO-8859-15", "windows-1252"],
    "glg": ["ISO-8859-1", "ISO-8859-15", "windows-1252"],
    "eus": ["ISO-8859-1", "ISO-8859-15", "windows-1252"],
    "afr": ["ISO-8859-1", "windows-1252"],
    "swh": ["ISO-8859-1", "windows-1252"],
    "swa": ["ISO-8859-1", "windows-1252"],
    "ind": ["ISO-8859-1", "windows-1252"],
    "msa": ["ISO-8859-1", "windows-1252"],
    # --- Baltic (ISO-8859-4/13 + windows-1257) ---
    "lav": ["ISO-8859-4", "ISO-8859-13", "windows-1257"],
    "lit": ["ISO-8859-4", "ISO-8859-13", "windows-1257"],
    "est": ["ISO-8859-4", "ISO-8859-13", "windows-1257"],
    # --- Southern European (ISO-8859-3) ---
    "mlt": ["ISO-8859-3"],
    "tur": ["ISO-8859-3", "ISO-8859-9", "windows-1254"],
    # --- Central / Eastern European ---
    "ces": ["ISO-8859-2", "windows-1250"],
    "pol": ["ISO-8859-2", "windows-1250"],
    "hrv": ["ISO-8859-2", "windows-1250"],
    "slk": ["ISO-8859-2", "windows-1250"],
    "slv": ["ISO-8859-2", "windows-1250"],
    "hun": ["ISO-8859-2", "windows-1250"],
    "ron": ["ISO-8859-2", "windows-1250"],
    "bos": ["ISO-8859-2", "windows-1250"],
    "sqi": ["ISO-8859-2", "windows-1250"],
    # --- Cyrillic ---
    "rus": ["ISO-8859-5", "windows-1251", "KOI8-R", "IBM855", "IBM866", "x-mac-cyrillic"],
    "ukr": ["ISO-8859-5", "windows-1251", "KOI8-U", "IBM855", "x-mac-cyrillic"],
    "bul": ["ISO-8859-5", "windows-1251", "IBM855", "x-mac-cyrillic"],
    "bel": ["ISO-8859-5", "windows-1251", "IBM855"],
    "mkd": ["ISO-8859-5", "windows-1251"],
    "srp": ["ISO-8859-5", "windows-1251"],
    # --- Arabic script ---
    "ara": ["ISO-8859-6", "windows-1256", "IBM420-ltr", "IBM420-rtl"],
    "urd": ["windows-1256"],
    "fas": ["windows-1256"],
    "prs": ["windows-1256"],
    "pus": ["windows-1256"],
    # --- Hebrew ---
    "heb": ["ISO-8859-8", "windows-1255", "IBM424-ltr", "IBM424-rtl"],
    # --- Greek ---
    "ell": ["ISO-8859-7", "windows-1253"],
    # --- Vietnamese ---
    "vie": ["windows-1258"],
    # --- Japanese ---
    "jpn": ["Shift_JIS", "EUC-JP", "ISO-2022-JP"],
    # --- Chinese (simplified: GB18030/GBK/GB2312/HZ/ISO-2022-CN; traditional: Big5/Big5-HKSCS/EUC-TW) ---
    "zho": ["GB18030", "GBK", "GB2312", "HZ", "ISO-2022-CN", "Big5", "Big5-HKSCS", "EUC-TW"],
    "wuu": ["GB18030", "GBK", "GB2312", "HZ"],
    "yue": ["Big5", "Big5-HKSCS", "EUC-TW"],
    "nan": ["Big5", "Big5-HKSCS", "EUC-TW"],
    # --- Korean ---
    "kor": ["EUC-KR", "ISO-2022-KR"],
    # --- Thai ---
    "tha": ["TIS-620"],
}

UNICODE_CHARSETS = ["UTF-8", "UTF-16-LE", "UTF-16-BE", "UTF-32-LE", "UTF-32-BE"]

# ---------------------------------------------------------------------------
# Confusable charset groups
# ---------------------------------------------------------------------------
# Two charsets are confusable when a significant fraction of real-world byte
# sequences are identical under both encodings, making it impossible (or
# unreasonable) to penalise a detector for choosing either member of the group.
#
# Rules of thumb used here:
#   • SBCS pairs that share a common base and differ only in C1 control range
#     (0x80–0x9F) or in a small set of punctuation/currency code points.
#   • Superset/subset chains where the subset encoding is fully valid under
#     the superset decoder (e.g. GB2312 ⊂ GBK ⊂ GB18030).
#   • Unicode pairs that are byte-for-byte indistinguishable without a BOM
#     (UTF-16-LE vs UTF-16-BE).
#   • EBCDIC LTR/RTL variants that use the same underlying code page.
#
# NOTE: The X-ISO-10646-UCS-4-3412 / X-ISO-10646-UCS-4-2143 byte orders are
# omitted — Java does not support them (see juniversalchardet Constants.java).
# ---------------------------------------------------------------------------
# Symmetric confusable groups
# ---------------------------------------------------------------------------
# Two charsets are *symmetrically confusable* when a significant fraction of
# real-world byte sequences are identical under both encodings, and neither
# direction of confusion is "safer" than the other.

SYMMETRIC_GROUPS: list[frozenset[str]] = [
    # Western European: differ only in 8 code points (€, Š/š, Ž/ž, Œ/œ, Ÿ)
    # and in bytes 0x80–0x9F (C1 range printable in windows-1252, control in ISO).
    frozenset({"ISO-8859-1", "ISO-8859-15", "windows-1252"}),

    # Central / Eastern European — same C1-range relationship
    frozenset({"ISO-8859-2", "windows-1250"}),

    # Cyrillic — windows-1251 extends ISO-8859-5 in the C1 range
    frozenset({"ISO-8859-5", "windows-1251"}),

    # KOI8 family — KOI8-R and KOI8-U share all Cyrillic letters and differ
    # only in four Ukrainian-specific characters (ї, є, і, ґ).
    frozenset({"KOI8-R", "KOI8-U"}),

    # Arabic — same C1-range relationship
    frozenset({"ISO-8859-6", "windows-1256"}),

    # Greek — same C1-range relationship
    frozenset({"ISO-8859-7", "windows-1253"}),

    # Hebrew — same C1-range relationship
    frozenset({"ISO-8859-8", "windows-1255"}),

    # Turkish / Latin-5 — same C1-range relationship
    frozenset({"ISO-8859-9", "windows-1254"}),

    # Baltic — ISO-8859-4 and ISO-8859-13 share most characters; both are
    # subsumed by windows-1257 in the printable range.
    frozenset({"ISO-8859-4", "ISO-8859-13", "windows-1257"}),

    # UTF-16 without BOM: the two byte orders are indistinguishable at the
    # byte level unless the content contains characters that reveal endianness.
    frozenset({"UTF-16-LE", "UTF-16-BE"}),

    # Hebrew EBCDIC: same IBM424 code page, differ only in text-reversal convention.
    frozenset({"IBM424-ltr", "IBM424-rtl"}),

    # Arabic EBCDIC: same IBM420 code page, differ only in text-reversal convention.
    frozenset({"IBM420-ltr", "IBM420-rtl"}),
]

# ---------------------------------------------------------------------------
# Directional superset / subset chains
# ---------------------------------------------------------------------------
# key = child (subset), value = direct parent (superset).
#
# Predicting the *superset* when the actual is the *subset* is a lenient match:
# the superset decoder handles all subset byte sequences correctly.
#
# Predicting the *subset* when the actual is the *superset* is NOT lenient:
# the subset decoder may corrupt characters that only exist in the superset.
#
#   GB2312 ⊂ GBK ⊂ GB18030
#   Big5   ⊂ Big5-HKSCS

SUPERSET_OF: dict[str, str] = {
    "GB2312":   "GBK",
    "GBK":      "GB18030",
    "Big5":     "Big5-HKSCS",
}

# ---------------------------------------------------------------------------
# Combined group list (for probability collapsing during inference)
# ---------------------------------------------------------------------------
# Includes both symmetric groups and one flat set per superset chain.
# Direction is irrelevant for collapsing — we just want to pool probability
# mass among related charsets.

CONFUSABLE_GROUPS: list[frozenset[str]] = list(SYMMETRIC_GROUPS) + [
    frozenset({"GB2312", "GBK", "GB18030"}),
    frozenset({"Big5", "Big5-HKSCS"}),
]

# Fast lookup: charset → its symmetric group frozenset (None if not symmetric)
_SYMMETRIC_MAP: dict[str, frozenset[str]] = {}
for _group in SYMMETRIC_GROUPS:
    for _cs in _group:
        _SYMMETRIC_MAP[_cs] = _group


def is_lenient_match(actual: str, predicted: str) -> bool:
    """Return True if predicting `predicted` when the true charset is `actual`
    is an acceptable ("lenient") result.

    - Exact match → always True.
    - Symmetric confusable group → True in both directions.
    - Superset chain: predicting an *ancestor* (superset) of `actual` → True.
      Predicting a *descendant* (subset) → False.
    """
    if actual == predicted:
        return True
    # Symmetric group check
    g = _SYMMETRIC_MAP.get(actual)
    if g is not None and predicted in g:
        return True
    # Walk up the superset chain of `actual`
    ancestor = SUPERSET_OF.get(actual)
    while ancestor is not None:
        if ancestor == predicted:
            return True
        ancestor = SUPERSET_OF.get(ancestor)
    return False

# Minimum fraction of characters that must survive encoding (ignore errors)
# for a chunk to be accepted.  Prevents garbage-filled SBCS samples.
MIN_ENCODE_RATIO = 0.85

DEFAULT_MIN_CHUNK = 64
DEFAULT_MAX_CHUNK = 1024
DEFAULT_MAX_PER_CHARSET = 500_000
DEFAULT_SEED = 42


def iter_sentences(lang_dir: Path):
    """Yield raw sentence strings from all *.txt files in a language dir."""
    for txt_file in sorted(lang_dir.glob("*.txt")):
        try:
            with open(txt_file, encoding="utf-8", errors="replace") as f:
                for line in f:
                    line = line.strip()
                    if not line:
                        continue
                    tab = line.find("\t")
                    text = line[tab + 1:] if tab >= 0 else line
                    if text:
                        yield text
        except OSError:
            pass


# Charsets that store text in visual/reversed order before encoding
RTL_CHARSETS = {"IBM424-rtl", "IBM420-rtl"}


def encode_chunk(text: str, codec: str, target_bytes: int,
                 charset: str = "") -> bytes | None:
    """Encode enough of `text` to produce ~target_bytes, or None if quality too low.

    For RTL EBCDIC variants the Unicode text is reversed before encoding to
    simulate visual storage order, matching what Tika's RTL recognizers expect.
    """
    if charset in RTL_CHARSETS:
        text = text[::-1]
    # Encode conservatively: try to get at least target_bytes
    encoded_full = text.encode(codec, errors="ignore")
    if not encoded_full:
        return None

    # Check encoding quality on the full text
    char_count = len(text)
    if char_count == 0:
        return None
    # Rough proxy: if encoded bytes << char count for SBCS, many were dropped
    # For multibyte (CJK) encodings this ratio can legitimately differ.
    if codec not in ("utf-8", "utf-16-le", "utf-16-be", "utf-32-le", "utf-32-be",
                     "shift-jis", "euc-jp", "iso-2022-jp", "euc-kr", "iso-2022-kr",
                     "gb18030", "big5"):
        # SBCS: each char → 1 byte. Ratio check.
        if len(encoded_full) < char_count * MIN_ENCODE_RATIO:
            return None

    if len(encoded_full) >= target_bytes:
        return encoded_full[:target_bytes]
    return encoded_full  # shorter than target but still valid


def build_charset_file(
    charset: str,
    codec: str,
    lang_dirs: list[tuple[str, Path]],
    out_file: Path,
    max_samples: int,
    min_chunk: int,
    max_chunk: int,
    rng: random.Random,
) -> int:
    """Write one gzipped binary file for `charset`, returning sample count."""
    # Collect candidate (lang, sentence) pairs from all contributing langs,
    # shuffled so samples interleave languages.
    candidates: list[tuple[str, str]] = []
    for lang, lang_dir in lang_dirs:
        for sent in iter_sentences(lang_dir):
            candidates.append((lang, sent))
            if len(candidates) >= max_samples * 10:  # cap memory
                break

    if not candidates:
        return 0

    rng.shuffle(candidates)

    out_file.parent.mkdir(parents=True, exist_ok=True)
    written = 0
    with gzip.open(out_file, "wb") as gz:
        for _lang, sent in candidates:
            if written >= max_samples:
                break
            target = rng.randint(min_chunk, max_chunk)
            try:
                chunk = encode_chunk(sent, codec, target, charset)
            except (LookupError, UnicodeError):
                continue
            if not chunk:
                continue
            gz.write(struct.pack(">H", len(chunk)))
            gz.write(chunk)
            written += 1

    return written


def probe_codec(charset: str, codec: str) -> bool:
    """Return True if Python can encode a Latin test string with this codec."""
    try:
        "hello world".encode(codec)
        return True
    except LookupError:
        print(f"  WARNING: codec '{codec}' for charset '{charset}' not available "
              f"on this platform — skipping")
        return False


def main():
    parser = argparse.ArgumentParser(
        description="Build charset-detection training data from Leipzig corpus",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "corpus_dir", type=Path,
        help="Root directory of Leipzig-format corpus (contains lang subdirs)",
    )
    parser.add_argument(
        "output_dir", type=Path,
        help="Directory to write per-charset .bin.gz files and manifest.json",
    )
    parser.add_argument(
        "--max-per-charset", type=int, default=DEFAULT_MAX_PER_CHARSET,
        metavar="N",
        help="Maximum training samples per charset",
    )
    parser.add_argument(
        "--min-chunk", type=int, default=DEFAULT_MIN_CHUNK,
        help="Minimum sample size in bytes",
    )
    parser.add_argument(
        "--max-chunk", type=int, default=DEFAULT_MAX_CHUNK,
        help="Maximum sample size in bytes",
    )
    parser.add_argument(
        "--charsets", nargs="+", metavar="CS",
        help="Process only these charsets (default: all)",
    )
    parser.add_argument(
        "--seed", type=int, default=DEFAULT_SEED,
        help="Random seed",
    )
    args = parser.parse_args()

    rng = random.Random(args.seed)

    # Build charset → [(lang, lang_dir)] index from corpus
    charset_langs: dict[str, list[tuple[str, Path]]] = defaultdict(list)

    for lang_dir in sorted(args.corpus_dir.iterdir()):
        if not lang_dir.is_dir():
            continue
        lang = lang_dir.name
        charsets = list(UNICODE_CHARSETS)  # all langs get Unicode
        charsets += LANG_CHARSETS.get(lang, [])
        for cs in charsets:
            charset_langs[cs].append((lang, lang_dir))

    # Filter to requested charsets
    if args.charsets:
        requested = set(args.charsets)
        charset_langs = {cs: v for cs, v in charset_langs.items() if cs in requested}

    # Remove charsets with unavailable codecs
    charset_langs = {
        cs: v for cs, v in charset_langs.items()
        if probe_codec(cs, CHARSET_CODEC[cs])
    }

    print(f"Charsets to build: {len(charset_langs)}")
    print(f"  max-per-charset : {args.max_per_charset:,}")
    print(f"  chunk size      : {args.min_chunk}–{args.max_chunk} bytes")
    print(f"  seed            : {args.seed}")
    print(f"  output          : {args.output_dir}")
    print()

    args.output_dir.mkdir(parents=True, exist_ok=True)

    manifest = {}
    total = 0

    for charset, lang_dirs in sorted(charset_langs.items()):
        codec = CHARSET_CODEC[charset]
        out_file = args.output_dir / f"{charset.lower().replace('_', '-')}.bin.gz"
        langs_contributing = [l for l, _ in lang_dirs]
        print(f"  {charset:20s} ({len(langs_contributing)} langs) → {out_file.name}")

        n = build_charset_file(
            charset, codec, lang_dirs, out_file,
            args.max_per_charset, args.min_chunk, args.max_chunk, rng,
        )
        print(f"    wrote {n:,} samples")
        manifest[charset] = {
            "file":     out_file.name,
            "codec":    codec,
            "samples":  n,
            "languages": langs_contributing,
        }
        total += n

    manifest_file = args.output_dir / "manifest.json"
    with open(manifest_file, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)

    print(f"\nDone. {len(manifest)} charsets, {total:,} total samples")
    print(f"Manifest: {manifest_file}")


if __name__ == "__main__":
    main()
