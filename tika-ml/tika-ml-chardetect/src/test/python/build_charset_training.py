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
Build charset-detection training, devtest, and test data from:

  1. MADLAD-400 per-language sentence files  (--madlad-dir, default ~/datasets/madlad/data)
  2. Cantonese Wikipedia plain-text sentences (--zh-yue-file,
     default ~/datasets/zh_yuewiki/sentences_zh_yue.txt)
     Used for Big5 and Big5-HKSCS (Traditional Chinese).

Pipeline per language
---------------------
  1. Load up to --max-source-per-lang sentences from sentences_madlad.txt
     (or sentences_zh_yue.txt for the "yue" virtual language).
  2. Apply fastText contamination filter (optional but recommended):
       remove sentences that fastText confidently (>= 0.80) assigns to one of
       the top-10 internet languages but the directory label says otherwise.
       Top-10: English, Spanish, French, German, Russian, Portuguese, Chinese,
               Arabic, Japanese, Korean.
  3. Shuffle with --seed
  4. Split 80 / 10 / 10 into train / devtest / test pools at source-sentence
     level so no sentence crosses splits.
  5. For each applicable charset, encode each pool up to the cap and write
     a [uint16-BE length][raw bytes] gzipped binary file.

Charset design decisions
------------------------
  Dropped (replaced by Windows equivalent):
    ISO-8859-1, ISO-8859-2, ISO-8859-4, ISO-8859-5, ISO-8859-6,
    ISO-8859-7, ISO-8859-8, ISO-8859-9, ISO-8859-13, ISO-8859-15
  Kept (no Windows equivalent):
    ISO-8859-3  (Maltese — ħ, ġ, ċ, ż not representable in any Windows charset)
  Distinct Cyrillic variants retained:
    KOI8-R, KOI8-U, IBM855, IBM866, x-mac-cyrillic
  Superset-only policy (subset encodings dropped from training):
    Big5-HKSCS only  (superset of Big5 — files labelled Big5 decode safely as HKSCS)
    GB18030 only     (superset of GBK and GB2312 — byte patterns are identical for
                      the common character subset; the model cannot distinguish them)
    Shift_JIS trained via cp932 codec (Windows-31J superset — Java's Shift_JIS
                      resolves to CP932 internally; training on strict JIS X 0208
                      would miss NEC/IBM extension characters in real Windows files)
    windows-874 replaces TIS-620 (superset; Thai chars 0xA1–0xFB are identical,
                      Charset.forName("TIS-620") not guaranteed on all JVMs)
  Trained in ML model using stride-2 bigram features:
    UTF-16-LE/BE, UTF-32-LE/BE — generated from all MADLAD languages so the
                      model sees Arabic, Hebrew, Greek, Japanese, Korean, Thai,
                      Chinese, Russian, etc. in wide Unicode encodings.
                      BOM-free codecs used; U+FEFF stripped from source text.
  Handled structurally (not by the ML model):
    US-ASCII, ISO-2022-JP/KR/CN — structural gates in MojibusterEncodingDetector
    HZ-GB-2312 — dropped: Java has no HZ Charset and returning a wrong decode
                 charset (GB18030) would silently corrupt content
  Not supported (no Python codec):
    EUC-TW — JDK-only extension, vanishingly rare in practice (superseded by
             Big5/UTF-8). Falls through to ICU4J/Universal as fallback.

Encoding quality gates
----------------------
  Round-trip verification: encode with errors='strict', decode back, verify
    the result matches the original. Any character that can't be encoded
    exactly causes the sentence to be skipped entirely. This replaces the
    old encode-survival ratio heuristic and catches all silent failures.
  High-byte ratio: encoded chunk must have enough bytes >= 0x80 to be
    discriminative (thresholds vary by encoding family).

Traditional Chinese (Big5, Big5-HKSCS)
------------------------------------------------
  Sourced from the Cantonese Wikipedia dump (zh_yuewiki), extracted to
  ~/datasets/zh_yuewiki/sentences_zh_yue.txt (~940K sentences).
  The "yue" virtual language in LANG_CHARSETS maps to Big5 and Big5-HKSCS.
  All three splits (train/devtest/test) are proper held-out partitions —
  no in-sample evaluation.

  EUC-TW is omitted: Python has no euc_tw codec (JDK-only extension) and
  the encoding is vanishingly rare in practice, superseded by Big5/UTF-8.

Output structure
----------------
  <output_dir>/
    train/   UTF-8.bin.gz, windows-1252.bin.gz, ...
    devtest/ UTF-8.bin.gz, ...
    test/    UTF-8.bin.gz, ...
    manifest.json

Caps (configurable):
  --train-cap    20000   encoded samples per charset
  --devtest-cap   2000   encoded samples per charset
  --test-cap      5000   encoded samples per charset

Usage
-----
  python build_charset_training.py [options]

  python build_charset_training.py \\
      --madlad-dir  ~/datasets/madlad/data \\
      --zh-yue-file ~/datasets/zh_yuewiki/sentences_zh_yue.txt \\
      --output-dir  ~/datasets/madlad/charset-detect2 \\
      --fasttext-model ~/datasets/madlad/lid.176.bin
"""

import argparse
import gzip
import json
import os
import random
import struct
import sys
import unicodedata
from collections import defaultdict
from pathlib import Path

# ---------------------------------------------------------------------------
# Optional ebcdic dependency — registers cp420 (IBM420) and other EBCDIC
# codecs not available in the standard CPython codec library.
# Install with: pip install ebcdic
# ---------------------------------------------------------------------------
try:
    import ebcdic as _ebcdic  # noqa: F401 — import for side-effect (codec registration)
    EBCDIC_AVAILABLE = True
except ImportError:
    EBCDIC_AVAILABLE = False

# ---------------------------------------------------------------------------
# Optional fastText dependency
# ---------------------------------------------------------------------------

try:
    import fasttext as _fasttext
    _fasttext.FastText.eprint = lambda *a, **kw: None  # suppress stderr noise
    FASTTEXT_AVAILABLE = True
except ImportError:
    FASTTEXT_AVAILABLE = False

# ---------------------------------------------------------------------------
# Charset → Python codec name
# ---------------------------------------------------------------------------

CHARSET_CODEC: dict[str, str] = {
    "UTF-8":          "utf-8",
    # UTF-16/32 variants without BOM — detected by WideUnicodeDetector before
    # the ML model runs.  Included as eval-only (STRUCTURAL_ONLY_CHARSETS) so
    # EvalCharsetDetectors can measure full-pipeline coverage.
    "UTF-16-LE":      "utf-16-le",
    "UTF-16-BE":      "utf-16-be",
    "UTF-32-LE":      "utf-32-le",
    "UTF-32-BE":      "utf-32-be",
    "US-ASCII":       "ascii",
    # CP932 (Windows-31J) is trained under the IANA name Shift_JIS because
    # Java's Charset.forName("Shift_JIS") resolves to CP932 internally.
    # Training on Python's strict "shift-jis" codec would miss the NEC/IBM
    # extension characters (0x87–0x9F, 0xE0–0xEA lead-byte ranges) that real
    # Windows Shift-JIS files use. The superset covers everything the subset does.
    "Shift_JIS":      "cp932",
    "EUC-JP":         "euc-jp",
    "ISO-2022-JP":    "iso-2022-jp",
    "EUC-KR":         "euc-kr",
    "ISO-2022-KR":    "iso-2022-kr",
    "ISO-2022-CN":    "iso2022_cn",
    # GB18030 is the superset of GBK and GB2312; training only on GB18030
    # avoids teaching the model to distinguish encodings that share identical
    # byte patterns for the common character subset. Files labelled GB2312 or
    # GBK in the wild are safely decoded as GB18030.
    "GB18030":        "gb18030",
    # Big5-HKSCS is the superset of plain Big5; same rationale as GB18030.
    "Big5-HKSCS":     "big5hkscs",
    # EUC-TW omitted: Python has no euc_tw codec (JDK-only extension).
    # EUC-TW is vanishingly rare in practice, superseded by Big5/UTF-8.
    # Files encountered in the wild will fall through to ICU4J/Universal as
    # fallback detectors.
    # ISO-8859-3 kept: no Windows equivalent (Maltese)
    "ISO-8859-3":     "iso-8859-3",
    # Windows single-byte charsets (ISO-8859-X equivalents dropped)
    "windows-1250":   "cp1250",
    "windows-1251":   "cp1251",
    "windows-1252":   "cp1252",
    "windows-1253":   "cp1253",
    "windows-1254":   "cp1254",
    "windows-1255":   "cp1255",
    "windows-1256":   "cp1256",
    "windows-1257":   "cp1257",
    "windows-1258":   "cp1258",
    # Distinct Cyrillic variants (no Windows equivalent)
    "KOI8-R":         "koi8-r",
    "KOI8-U":         "koi8-u",
    "IBM855":         "cp855",
    "IBM866":         "cp866",
    "x-mac-cyrillic": "mac_cyrillic",
    # Thai — windows-874 (CP874) is the superset of TIS-620; Thai characters
    # occupy 0xA1–0xFB in both. Charset.forName("TIS-620") is not guaranteed
    # on all JVMs; windows-874 is. Training on cp874 covers all real Thai files.
    "windows-874":    "cp874",
    # EBCDIC
    "IBM500":         "cp500",
    "IBM424-ltr":     "cp424",
    "IBM424-rtl":     "cp424",
    "IBM420-ltr":     "cp420",
    "IBM420-rtl":     "cp420",
}

# ---------------------------------------------------------------------------
# Language → applicable charsets
# ISO-8859-X variants with Windows equivalents are dropped.
# Only ISO-8859-3 is retained (Maltese).
# ---------------------------------------------------------------------------

LANG_CHARSETS: dict[str, list[str]] = {
    # Western European
    "eng": ["US-ASCII", "windows-1252", "IBM500"],
    "deu": ["windows-1252", "IBM500"],
    "fra": ["windows-1252", "IBM500"],
    "ita": ["windows-1252", "IBM500"],
    "spa": ["windows-1252", "IBM500"],
    "nld": ["windows-1252", "IBM500"],
    "por": ["windows-1252"],
    "dan": ["windows-1252"],
    "swe": ["windows-1252"],
    "nob": ["windows-1252"],
    "fin": ["windows-1252"],
    "isl": ["windows-1252"],
    "cat": ["windows-1252"],
    "glg": ["windows-1252"],
    "eus": ["windows-1252"],
    "afr": ["windows-1252"],
    "swh": ["windows-1252"],
    "ind": ["windows-1252"],
    "msa": ["windows-1252"],
    # Baltic
    "lav": ["windows-1257"],
    "lit": ["windows-1257"],
    "est": ["windows-1257"],
    # Southern European — ISO-8859-3 kept for Maltese (no Windows equivalent)
    "mlt": ["ISO-8859-3"],
    "tur": ["windows-1254"],
    # Central / Eastern European
    "ces": ["windows-1250"],
    "pol": ["windows-1250"],
    "hrv": ["windows-1250"],
    "slk": ["windows-1250"],
    "slv": ["windows-1250"],
    "hun": ["windows-1250"],
    "ron": ["windows-1250"],
    "bos": ["windows-1250"],
    "sqi": ["windows-1250"],
    # Cyrillic — keep all distinct encodings
    "rus": ["windows-1251", "KOI8-R", "IBM855", "IBM866", "x-mac-cyrillic"],
    "ukr": ["windows-1251", "KOI8-U", "IBM855", "x-mac-cyrillic"],
    "bul": ["windows-1251", "IBM855", "x-mac-cyrillic"],
    "bel": ["windows-1251", "IBM855"],
    "mkd": ["windows-1251"],
    "srp": ["windows-1251"],
    # Arabic
    "ara": ["windows-1256", "IBM420-ltr", "IBM420-rtl"],
    "urd": ["windows-1256"],
    "fas": ["windows-1256"],
    "pus": ["windows-1256"],
    # Hebrew
    "heb": ["windows-1255", "IBM424-ltr", "IBM424-rtl"],
    # Greek
    "ell": ["windows-1253"],
    # Vietnamese
    "vie": ["windows-1258"],
    # Japanese
    "jpn": ["Shift_JIS", "EUC-JP", "ISO-2022-JP"],
    "zho": ["GB18030", "ISO-2022-CN"],
    # Korean
    "kor": ["EUC-KR", "ISO-2022-KR"],
    # Thai
    "tha": ["windows-874"],
    # Traditional Chinese — sourced from Cantonese Wikipedia (zh_yuewiki),
    # not MADLAD (which has no Cantonese/Traditional Chinese in Han script).
    # "yue" is a virtual language key handled by load_plaintext_sentences().
    # Big5-HKSCS only (superset of plain Big5).
    "yue": ["Big5-HKSCS"],
}

# Charsets added for every language in LANG_CHARSETS.
# UTF-16/32 are included here so the ML model sees them encoded in many scripts
# (Arabic, Hebrew, Greek, Japanese, Korean, Thai, Chinese, Russian, etc.) rather
# than only Chinese.  The stride-2 bigram features in ByteNgramFeatureExtractor
# give the model direct code-unit visibility into UTF-16/32 structure.
# BOM-free codecs (utf-16-le, utf-16-be, utf-32-le, utf-32-be) are used, and
# U+FEFF is stripped from source text, so the model never sees BOM bytes.
UNICODE_CHARSETS = [
    "UTF-8",
    "UTF-16-LE", "UTF-16-BE",
    "UTF-32-LE", "UTF-32-BE",
]

# These charsets produce zero high bytes (all content < 0x80), so the ML
# feature extractor sees no signal.  They are detected by structural gates
# in MojibusterEncodingDetector before the model is ever called:
#   - US-ASCII  → checkAscii()      → returns UTF-8
#   - ISO-2022* → detectIso2022()   → returns ISO-2022-JP/KR/CN
#
# We still generate devtest/test files so EvalCharsetDetectors can measure
# full-pipeline accuracy (with structural gates enabled), but we skip train
# files so these classes don't pollute the ML model with zero-feature samples.
#
# UTF-16/32 are NOT in this set — they are now trained in the ML model using
# stride-2 bigram features which capture code-unit structure directly.
STRUCTURAL_ONLY_CHARSETS = frozenset({
    "US-ASCII", "ISO-2022-JP", "ISO-2022-KR", "ISO-2022-CN",
})

# Virtual language key used in LANG_CHARSETS for Traditional Chinese.
# Sentences are loaded from --zh-yue-file (Cantonese Wikipedia) rather than
# a MADLAD per-language directory.
YUE_LANG = "yue"

# ---------------------------------------------------------------------------
# fastText contamination filter
# ---------------------------------------------------------------------------

TOP10_FASTTEXT = {"en", "es", "fr", "de", "ru", "pt", "zh", "ar", "ja", "ko"}
FASTTEXT_THRESHOLD = 0.80

# ISO 639-3 → ISO 639-1 for languages in LANG_CHARSETS
ISO3_TO_ISO1: dict[str, str] = {
    "eng": "en", "spa": "es", "fra": "fr", "deu": "de",
    "rus": "ru", "por": "pt", "zho": "zh", "ara": "ar",
    "jpn": "ja", "kor": "ko",
    "ita": "it", "nld": "nl", "pol": "pl", "tur": "tr",
    "ell": "el", "heb": "he", "ukr": "uk", "ces": "cs",
    "ron": "ro", "hun": "hu", "bul": "bg", "hrv": "hr",
    "fin": "fi", "swe": "sv", "dan": "da", "nob": "no",
    "vie": "vi", "tha": "th", "ind": "id", "msa": "ms",
    "urd": "ur", "fas": "fa", "pus": "ps", "afr": "af",
    "swh": "sw", "lav": "lv", "lit": "lt", "est": "et",
    "mlt": "mt", "cat": "ca", "glg": "gl", "eus": "eu",
    "bel": "be", "mkd": "mk", "srp": "sr", "bos": "bs",
    "slk": "sk", "slv": "sl", "sqi": "sq", "isl": "is",
    "kor": "ko",
}


def apply_fasttext_filter(sentences: list[str], lang: str,
                          model) -> list[str]:
    """Batch-filter sentences using fastText contamination detection.

    Removes sentences that fastText confidently (>= FASTTEXT_THRESHOLD) assigns
    to a top-10 internet language that differs from the directory's language.
    Uses fastText batch prediction for speed (~10x faster than per-sentence calls).
    """
    if not sentences:
        return sentences
    expected = ISO3_TO_ISO1.get(lang, "")
    # fastText requires no newlines; clean in batch
    clean = [s.replace("\n", " ").strip() for s in sentences]
    # Batch predict: returns (list_of_label_lists, list_of_prob_arrays)
    all_labels, all_probs = model.predict(clean, k=1)
    kept = []
    for sent, labels, probs in zip(sentences, all_labels, all_probs):
        if not labels:
            kept.append(sent)
            continue
        ft_label = labels[0].replace("__label__", "")
        prob = float(probs[0])
        if (prob >= FASTTEXT_THRESHOLD
                and ft_label in TOP10_FASTTEXT
                and ft_label != expected):
            continue  # contaminated
        kept.append(sent)
    return kept


def load_fasttext_model(model_path: Path):
    """Load fastText model, suppressing noisy stderr output."""
    if not FASTTEXT_AVAILABLE:
        return None
    return _fasttext.load_model(str(model_path))

# ---------------------------------------------------------------------------
# High-byte ratio thresholds
# ---------------------------------------------------------------------------

_HIGH_BYTE_EXEMPT = frozenset({
    "ascii", "utf-16-le", "utf-16-be", "utf-32-le", "utf-32-be",
    "iso-2022-jp", "iso-2022-kr", "iso2022_cn",
})
_HIGH_BYTE_CJK = frozenset({
    "big5hkscs", "euc-jp", "euc-kr",
    "gb18030", "cp932",
})
MIN_HIGH_BYTE_CJK  = 0.20
MIN_HIGH_BYTE_UTF8 = 0.05
MIN_HIGH_BYTE_SBCS = 0.02

RTL_CHARSETS = {"IBM424-rtl", "IBM420-rtl"}

# CP1258 (Vietnamese) uses combining diacritical marks (NFD-style).
# MADLAD is NFC, so we must decompose before encoding and recompose before
# the drop-count comparison.
NFD_CHARSETS = {"windows-1258"}

# IBM420 (Arabic EBCDIC) encodes only the 28 basic Arabic letters plus a
# small set of punctuation, digits, and presentation forms.  Modern Arabic
# Unicode text (especially from web corpora like MADLAD) contains:
#   • Harakat / tashkeel (U+064B–U+065F, U+0610–U+061A, etc.) — not in IBM420
#   • إ (U+0625, alef with hamza below) and ٱ (U+0671, alef wasla) — not in IBM420
# Mainframe Arabic text was written without combining diacritics; stripping
# them and normalising the two missing alef forms produces valid training data.
# The drop-count is measured against the pre-processed text so MAX_DROPPED_CHARS
# still catches genuine failures (emoji, unsupported Latin-extended chars, etc.).
STRIP_ARABIC_DIACRITICS_CHARSETS = {"IBM420-ltr", "IBM420-rtl"}

# Arabic combining mark codepoint ranges to strip for IBM420:
#   U+0610–U+061A  Arabic extended combining (salat, etc.)
#   U+064B–U+065F  Harakat (fatha, damma, kasra, shadda, sukun, tanwin, etc.)
#   U+06D6–U+06E4  Quranic annotation signs (combining)
#   U+06E7–U+06E8  Combining above / below
#   U+06EA–U+06ED  Combining letters
_ARABIC_DIACRITIC_RANGES = (
    (0x0610, 0x061A),
    (0x064B, 0x065F),
    (0x06D6, 0x06E4),
    (0x06E7, 0x06E8),
    (0x06EA, 0x06ED),
)

# IBM420 only encodes the 28 basic Arabic letters plus a small set of
# punctuation/digits.  Several common Unicode Arabic letters are absent:
#   U+0625 إ  ARABIC LETTER ALEF WITH HAMZA BELOW  → ا  (U+0627)
#   U+0671 ٱ  ARABIC LETTER ALEF WASLA             → ا  (U+0627)
# These are normalised to their plain alef base before encoding, matching
# the simplified Arabic orthography used on mainframes.
_IBM420_ALEF_MAP: dict[str, str] = {
    "\u0625": "\u0627",  # إ → ا
    "\u0671": "\u0627",  # ٱ → ا
}


def _prepare_for_ibm420(text: str) -> str:
    """Strip Arabic diacritics and normalise alef variants for IBM420."""
    text = "".join(
        c for c in text
        if not any(lo <= ord(c) <= hi for lo, hi in _ARABIC_DIACRITIC_RANGES)
    )
    return "".join(_IBM420_ALEF_MAP.get(c, c) for c in text)

# Maximum number of characters that may be silently dropped during encoding
# (unencodable chars removed by errors='ignore') plus corrupt sequences
# (U+FFFD produced on decode) before a sentence is rejected.
# Set at 3: allows one or two typographic characters (curly quotes, em-dash)
# to be dropped without rejecting the whole sentence, while still discarding
# sentences where substantial content is missing.
MAX_DROPPED_CHARS = 3

# ---------------------------------------------------------------------------
# Encoding
# ---------------------------------------------------------------------------

def encode_chunk(text: str, charset: str, codec: str,
                 target_bytes: int) -> bytes | None:
    """Encode text to a chunk of ~target_bytes with lenient round-trip check.

    Quality gates applied in order:
      1. Encode with errors='ignore' (drops unencodable chars silently)
      2. Decode result with errors='replace' (corrupt sequences → U+FFFD)
      3. Reject if (chars dropped + U+FFFD count) > MAX_DROPPED_CHARS
      4. Reject if chunk has insufficient high bytes for its encoding family

    Using errors='ignore' rather than errors='strict' avoids discarding
    otherwise-good sentences that contain a single typographic character
    (curly quote, em-dash, ellipsis) not representable in the target charset.
    The drop count gate still catches sentences where most content is lost.
    """
    # Strip U+FEFF (BOM / zero-width no-break space) unconditionally.
    # The model must never see BOM bytes — BOMDetector owns that signal at
    # inference time and strips BOMs before bytes reach the model.
    text = text.replace("\ufeff", "")
    if not text:
        return None

    if charset in RTL_CHARSETS:
        text = text[::-1]

    # IBM420 (Arabic EBCDIC): strip harakat and normalise alef variants before
    # encoding.  Mainframe Arabic text was never written with combining
    # diacritics, and certain hamza forms (إ U+0625, ٱ U+0671) absent from the
    # IBM420 codepage are normalised to plain alef (ا U+0627).  The drop-count
    # is measured against the pre-processed text so MAX_DROPPED_CHARS still
    # catches genuine encoding failures (emoji, Latin-extended, etc.).
    if charset in STRIP_ARABIC_DIACRITICS_CHARSETS:
        text = _prepare_for_ibm420(text)

    # CP1258 needs NFD decomposition; compare after recomposing back to NFC
    norm_text = unicodedata.normalize("NFD", text) if charset in NFD_CHARSETS else text

    try:
        encoded_full = norm_text.encode(codec, errors="ignore")
    except LookupError:
        return None
    if not encoded_full:
        return None

    try:
        decoded_raw = encoded_full.decode(codec, errors="replace")
    except (UnicodeDecodeError, LookupError):
        return None

    # Recompose decoded output for fair character-count comparison
    decoded = unicodedata.normalize("NFC", decoded_raw) if charset in NFD_CHARSETS else decoded_raw
    n_dropped = len(text) - len(decoded)
    n_corrupt = decoded.count("\ufffd")
    if n_dropped + n_corrupt > MAX_DROPPED_CHARS:
        return None

    chunk = encoded_full[:target_bytes] if len(encoded_full) >= target_bytes \
            else encoded_full

    # High-byte ratio check
    if codec not in _HIGH_BYTE_EXEMPT:
        high = sum(1 for b in chunk if b >= 0x80)
        if codec in _HIGH_BYTE_CJK:
            min_ratio = MIN_HIGH_BYTE_CJK
        elif codec == "utf-8":
            min_ratio = MIN_HIGH_BYTE_UTF8
        else:
            min_ratio = MIN_HIGH_BYTE_SBCS
        if high < len(chunk) * min_ratio:
            return None

    return chunk

# ---------------------------------------------------------------------------
# Sentence loading
# ---------------------------------------------------------------------------

def _clean_madlad_text(text: str) -> str:
    """Normalise a raw MADLAD sentence.

    MADLAD stores multi-paragraph source documents as a single sentence with
    embedded literal \\n / \\r / \\t escape sequences (two-character sequences,
    not actual control characters).  Replace them with a single space so they
    don't become unencodable backslash characters in EBCDIC charsets (e.g.
    IBM420, which has no backslash in its mapping) or spurious punctuation in
    single-byte charsets that do encode backslash.

    U+FEFF (BOM / zero-width no-break space) is stripped here so it can never
    propagate into any encoded sample; the model must never see BOM bytes.
    """
    text = text.replace("\ufeff", "")
    text = text.replace("\\n", " ").replace("\\r", "").replace("\\t", " ")
    return " ".join(text.split())  # collapse runs of whitespace


def load_madlad_sentences(lang_dir: Path, max_sentences: int) -> list[str]:
    """Load up to max_sentences from sentences_madlad.txt (lineNum\\ttext format)."""
    sentences = []
    txt = lang_dir / "sentences_madlad.txt"
    if not txt.exists():
        return sentences
    try:
        with open(txt, encoding="utf-8", errors="replace") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                tab = line.find("\t")
                text = line[tab + 1:] if tab >= 0 else line
                text = _clean_madlad_text(text)
                if text:
                    sentences.append(text)
                if len(sentences) >= max_sentences:
                    break
    except OSError:
        pass
    return sentences


def load_plaintext_sentences(path: Path, max_sentences: int) -> list[str]:
    """Load up to max_sentences from a plain text file (one sentence per line)."""
    sentences = []
    if not path.exists():
        print(f"  WARNING: file not found: {path}", file=sys.stderr)
        return sentences
    try:
        with open(path, encoding="utf-8", errors="replace") as f:
            for line in f:
                text = line.strip()
                if text:
                    sentences.append(text)
                if len(sentences) >= max_sentences:
                    break
    except OSError as e:
        print(f"  WARNING: could not read {path}: {e}", file=sys.stderr)
    return sentences

# ---------------------------------------------------------------------------
# Source-level splitting
# ---------------------------------------------------------------------------

def split_pool(sentences: list[str],
               seed: int) -> dict[str, list[str]]:
    """Shuffle and split 80 / 10 / 10 into train / devtest / test."""
    rng = random.Random(seed)
    s = list(sentences)
    rng.shuffle(s)
    n = len(s)
    n_train   = int(n * 0.80)
    n_devtest = (n - n_train) // 2
    return {
        "train":   s[:n_train],
        "devtest": s[n_train : n_train + n_devtest],
        "test":    s[n_train + n_devtest :],
    }

# ---------------------------------------------------------------------------
# Build one charset file for one split
# ---------------------------------------------------------------------------

def build_split_file(
    sentences: list[str],
    charset: str,
    codec: str,
    out_file: Path,
    cap: int,
    min_chunk: int,
    max_chunk: int,
    rng: random.Random,
) -> int:
    """Encode sentences and write up to cap samples to out_file.
    Returns the number of samples written."""
    out_file.parent.mkdir(parents=True, exist_ok=True)
    written = 0
    with gzip.open(out_file, "wb") as gz:
        for sent in sentences:
            if written >= cap:
                break
            target = rng.randint(min_chunk, max_chunk)
            chunk = encode_chunk(sent, charset, codec, target)
            if chunk is None:
                continue
            gz.write(struct.pack(">H", len(chunk)))
            gz.write(chunk)
            written += 1
    return written

# ---------------------------------------------------------------------------
# Codec availability check
# ---------------------------------------------------------------------------

def probe_codec(charset: str, codec: str) -> bool:
    try:
        "hello".encode(codec)
        return True
    except LookupError:
        hint = (" (install the 'ebcdic' package: pip install ebcdic)"
                if codec.startswith("cp4") and not EBCDIC_AVAILABLE else "")
        print(f"  WARNING: codec '{codec}' for {charset} not available — skipping{hint}",
              file=sys.stderr)
        return False

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

DEFAULT_MADLAD_DIR   = Path.home() / "datasets" / "madlad" / "data"
DEFAULT_ZH_YUE_FILE  = Path.home() / "datasets" / "zh_yuewiki" / "sentences_zh_yue.txt"
DEFAULT_OUTPUT_DIR   = Path.home() / "datasets" / "madlad" / "charset-detect2"
DEFAULT_FASTTEXT     = Path.home() / "datasets" / "madlad" / "lid.176.bin"
DEFAULT_MAX_SOURCE   = 200_000
DEFAULT_TRAIN_CAP    =  20_000
DEFAULT_DEVTEST_CAP  =   2_000
DEFAULT_TEST_CAP     =   5_000
DEFAULT_MIN_CHUNK    =     64
DEFAULT_MAX_CHUNK    =  1_024
DEFAULT_SEED         =     42


def main():
    parser = argparse.ArgumentParser(
        description="Build charset-detection train/devtest/test data",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("--madlad-dir",     type=Path, default=DEFAULT_MADLAD_DIR)
    parser.add_argument("--zh-yue-file",   type=Path, default=DEFAULT_ZH_YUE_FILE,
                        help="Plain-text Cantonese Wikipedia sentences (one per line); "
                             "used for Big5 and Big5-HKSCS")
    parser.add_argument("--output-dir",     type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--fasttext-model", type=Path, default=DEFAULT_FASTTEXT,
                        help="Path to lid.176.bin; omit to skip contamination filter")
    parser.add_argument("--max-source-per-lang", type=int, default=DEFAULT_MAX_SOURCE,
                        metavar="N", help="Max source sentences loaded per language")
    parser.add_argument("--train-cap",   type=int, default=DEFAULT_TRAIN_CAP)
    parser.add_argument("--devtest-cap", type=int, default=DEFAULT_DEVTEST_CAP)
    parser.add_argument("--test-cap",    type=int, default=DEFAULT_TEST_CAP)
    parser.add_argument("--min-chunk",   type=int, default=DEFAULT_MIN_CHUNK)
    parser.add_argument("--max-chunk",   type=int, default=DEFAULT_MAX_CHUNK)
    parser.add_argument("--seed",        type=int, default=DEFAULT_SEED)
    parser.add_argument("--charsets",    nargs="+", metavar="CS",
                        help="Process only these charsets (default: all)")
    args = parser.parse_args()

    rng = random.Random(args.seed)

    # fastText filter (optional)
    ft_model = None
    if args.fasttext_model and args.fasttext_model.exists():
        if FASTTEXT_AVAILABLE:
            ft_model = load_fasttext_model(args.fasttext_model)
            print(f"fastText filter: {args.fasttext_model}")
        else:
            print("fastText filter: disabled (fasttext package not installed)")
    else:
        print("fastText filter: disabled (model not found)")

    caps = {"train": args.train_cap, "devtest": args.devtest_cap,
            "test": args.test_cap}

    # Determine which charsets to build
    target_charsets: set[str] = set(args.charsets) if args.charsets else (
        set(CHARSET_CODEC.keys())
    )
    all_charsets = {cs for cs in target_charsets
                    if cs in CHARSET_CODEC and probe_codec(cs, CHARSET_CODEC[cs])}

    # -----------------------------------------------------------------------
    # Step 2 + 3: For each charset, load only the languages it needs, encode,
    # then free memory.  Never hold more than one charset's sentence pool in RAM.
    # -----------------------------------------------------------------------
    print(f"\n=== Encoding {len(all_charsets)} charsets ===")
    print(f"  Caps: train={args.train_cap:,}  "
          f"devtest={args.devtest_cap:,}  test={args.test_cap:,}")
    print(f"  Chunk size: {args.min_chunk}–{args.max_chunk} bytes")
    print(f"  Output: {args.output_dir}\n")

    manifest: dict[str, dict] = {}
    split_names = ["train", "devtest", "test"]

    for charset in sorted(all_charsets):
        codec = CHARSET_CODEC[charset]
        print(f"  {charset}", flush=True)

        split_counts: dict[str, int] = {}

        # Determine contributing languages for this charset
        is_unicode = charset in UNICODE_CHARSETS
        contributing = sorted(
            lang for lang, csets in LANG_CHARSETS.items()
            if is_unicode or charset in csets
        )

        # Load sentences for each contributing language.
        # Per-language load cap: we need roughly train_cap*6/n_langs sentences
        # total to survive encoding rejections; the 80/10/10 split means
        # train gets 80% of loaded data.  Use max_source_per_lang as ceiling.
        n_langs = max(1, len(contributing))
        load_cap = min(
            args.max_source_per_lang,
            max(5_000, (args.train_cap * 10) // n_langs),
        )

        lang_pools: dict[str, dict[str, list[str]]] = {}
        for lang in contributing:
            if lang == YUE_LANG:
                # Traditional Chinese: load from Cantonese Wikipedia dump
                raw = load_plaintext_sentences(args.zh_yue_file, load_cap)
                if not raw:
                    continue
                lang_pools[lang] = split_pool(raw, args.seed)
                pools = lang_pools[lang]
                print(f"    load {lang} (zh_yuewiki): {len(raw):>6,} → split "
                      f"{len(pools['train']):>5,}/{len(pools['devtest']):>4,}"
                      f"/{len(pools['test']):>4,}", flush=True)
            else:
                lang_dir = args.madlad_dir / lang
                if not lang_dir.is_dir():
                    continue
                raw = load_madlad_sentences(lang_dir, load_cap)
                if not raw:
                    continue
                before = len(raw)
                if ft_model is not None:
                    raw = apply_fasttext_filter(raw, lang, ft_model)
                lang_pools[lang] = split_pool(raw, args.seed)
                removed = before - len(raw)
                pools = lang_pools[lang]
                print(f"    load {lang}: {before:>6,} → split "
                      f"{len(pools['train']):>5,}/{len(pools['devtest']):>4,}"
                      f"/{len(pools['test']):>4,}"
                      + (f"  (-{removed} filtered)" if removed else ""),
                      flush=True)

        structural_only = charset in STRUCTURAL_ONLY_CHARSETS
        if structural_only:
            print(f"    (structural-only: skipping train, generating devtest/test only)")

        for split in split_names:
            # Skip train split for structural-only charsets: the ML model
            # sees zero features for these encodings; structural gates in
            # MlEncodingDetector handle them before the model is called.
            if structural_only and split == "train":
                split_counts[split] = 0
                continue

            cap = caps[split]
            per_lang = max(200, (cap * 4) // max(1, len(lang_pools)))
            sents: list[str] = []
            for lang, pools in lang_pools.items():
                sents.extend(pools[split][:per_lang])
            rng_split = random.Random(args.seed + hash(charset + split))
            rng_split.shuffle(sents)

            out_file = args.output_dir / split / f"{charset}.bin.gz"
            n = build_split_file(
                sents, charset, codec, out_file,
                cap, args.min_chunk, args.max_chunk,
                random.Random(args.seed + hash(charset + split + "enc")),
            )
            split_counts[split] = n
            print(f"    {split:7s}: {n:>6,} samples")

        # Release the sentence pools for this charset before moving on
        del lang_pools

        manifest[charset] = {
            "codec":           codec,
            "structural_only": charset in STRUCTURAL_ONLY_CHARSETS,
            "samples":         split_counts,
        }

    # -----------------------------------------------------------------------
    # Step 5: Write manifest
    # -----------------------------------------------------------------------
    manifest_path = args.output_dir / "manifest.json"
    args.output_dir.mkdir(parents=True, exist_ok=True)
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)

    total = {s: sum(v["samples"].get(s, 0) for v in manifest.values())
             for s in split_names}
    print(f"\nDone. {len(manifest)} charsets.")
    print(f"  train:   {total['train']:>8,} total samples")
    print(f"  devtest: {total['devtest']:>8,} total samples")
    print(f"  test:    {total['test']:>8,} total samples")
    print(f"  Manifest: {manifest_path}")


if __name__ == "__main__":
    main()
