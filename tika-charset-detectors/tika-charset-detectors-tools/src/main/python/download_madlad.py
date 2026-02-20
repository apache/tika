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
Download MADLAD-400 clean corpus from Hugging Face and write to
Leipzig TSV format (lineNum\\tsentence) organized by ISO 639-3 code.

MADLAD-400 (Magnusson et al., 2023) is a massively multilingual web
corpus derived from Common Crawl covering 419 languages.  This script
streams the "_clean_" gzipped JSONL shards directly from the Hub,
    splits each document on newlines into sentence-sized units, applies
a minimum-length filter (50 chars by default), and reservoir-samples
up to --max-per-lang units per language.

The 50-character minimum is important: short web fragments ("OK", "Yes",
"Click here") are nearly language-neutral and degrade detector accuracy.

MADLAD uses ISO 639-1 two-letter codes for major languages (en, de, fr …)
and ISO 639-3 three-letter codes for others.  This script maps the
two-letter codes to ISO 639-3 so the output directories match the
Leipzig corpus tree used by TrainLanguageModel.

Usage:
    pip install huggingface_hub
    python download_madlad.py <output_dir> [options]

Examples:
    # Test with a few languages first:
    python download_madlad.py ~/datasets/leipzig2 --langs en de fr ja zh

    # Full download (~1 TB of source data, takes many hours):
    python download_madlad.py ~/datasets/leipzig2

Output structure (coexists with Leipzig data):
    output_dir/
        eng/
            sentences_madlad.txt   (lineNum<TAB>paragraph)
        deu/
            sentences_madlad.txt
        ...

Files are named sentences_madlad.txt so they sit alongside Leipzig's
sentences.txt.  CorpusReader.readLanguageDir globs *.txt, so both are
included automatically — no Java changes needed.
"""

import argparse
import gzip
import json
import random
import sys
import time
from pathlib import Path

try:
    from huggingface_hub import HfFileSystem
except ImportError:
    sys.exit(
        "ERROR: 'huggingface_hub' not found.\n"
        "Run: pip install huggingface_hub"
    )

DATASET_PATH = "datasets/allenai/MADLAD-400/data"
DEFAULT_MAX_PER_LANG = 2_000_000
DEFAULT_MAX_STREAM = 10_000_000
DEFAULT_MIN_CHARS = 50
DEFAULT_SEED = 42

_last_log_time: float = 0.0


def log(msg: str, end: str = "\n") -> None:
    """Print msg with elapsed seconds since the previous log call."""
    global _last_log_time
    now = time.monotonic()
    elapsed = now - _last_log_time if _last_log_time else 0.0
    _last_log_time = now
    suffix = f"  [{elapsed:.1f}s]" if elapsed >= 0.1 else ""
    print(f"{msg}{suffix}", end=end, flush=True)

# Map MADLAD ISO 639-1 two-letter codes → ISO 639-3 three-letter codes
# used by our Leipzig corpus and TrainLanguageModel.
# Three-letter codes already in MADLAD are passed through unchanged.
ISO1_TO_ISO3 = {
    "af": "afr", "am": "amh", "ar": "ara", "as": "asm",
    "az": "aze", "ba": "bak", "be": "bel", "bg": "bul",
    "bn": "ben", "bo": "bod", "br": "bre", "bs": "bos",
    "ca": "cat", "cs": "ces", "cy": "cym", "da": "dan",
    "de": "deu", "el": "ell", "en": "eng", "eo": "epo",
    "es": "spa", "et": "est", "eu": "eus", "fa": "fas",
    "fi": "fin", "fr": "fra", "fy": "fry", "ga": "gle",
    "gd": "gla", "gl": "glg", "gu": "guj", "ha": "hau",
    "he": "heb", "hi": "hin", "hr": "hrv", "ht": "hat",
    "hu": "hun", "hy": "hye", "id": "ind", "ig": "ibo",
    "is": "isl", "it": "ita", "ja": "jpn", "ka": "kat",
    "kk": "kaz", "km": "khm", "kn": "kan", "ko": "kor",
    "ku": "kur", "ky": "kir", "lb": "ltz", "lo": "lao",
    "lt": "lit", "lv": "lav", "mg": "mlg", "mk": "mkd",
    "ml": "mal", "mn": "mon", "mr": "mar", "ms": "msa",
    "mt": "mlt", "my": "mya", "ne": "nep", "nl": "nld",
    "no": "nob", "or": "ori", "pa": "pan", "pl": "pol",
    "ps": "pus", "pt": "por", "ro": "ron", "ru": "rus",
    "rw": "kin", "sd": "snd", "si": "sin", "sk": "slk",
    "sl": "slv", "sm": "smo", "sn": "sna", "so": "som",
    "sq": "sqi", "sr": "srp", "st": "sot", "su": "sun",
    "sv": "swe", "sw": "swh", "ta": "tam", "te": "tel",
    "tg": "tgk", "th": "tha", "tk": "tuk", "tl": "tgl",
    "tr": "tur", "tt": "tat", "ug": "uig", "uk": "ukr",
    "ur": "urd", "uz": "uzb", "vi": "vie", "xh": "xho",
    "yi": "ydd", "yo": "yor", "zh": "zho", "zu": "zul",
}

# Same merge map as TrainLanguageModel.java — keeps MADLAD and Leipzig
# data under the same canonical code.
LANG_MERGE_MAP = {
    "azj": "aze", "ekk": "est", "pes": "fas", "zsm": "msa",
    "nor": "nob", "plt": "mlg", "cmn": "zho", "lvs": "lav",
    "gug": "grn", "quz": "que", "swa": "swh", "yid": "ydd",
}

# Language codes to skip entirely (noise shards, transliteration, etc.)
SKIP_CODES = {"zxx_xx_dtynoise"}


def to_iso3(madlad_code: str) -> str | None:
    """Convert a MADLAD language code to an ISO 639-3 code, or None to skip."""
    if madlad_code in SKIP_CODES:
        return None
    # Drop script-tagged variants like zh_Latn — no Leipzig equivalent
    if "_" in madlad_code:
        return None
    iso3 = ISO1_TO_ISO3.get(madlad_code, madlad_code)  # pass-through if already 3-letter
    return LANG_MERGE_MAP.get(iso3, iso3)


def iter_paragraphs(fs: "HfFileSystem", gz_path: str, min_chars: int):
    """Stream sentences from one gzipped JSONL shard.

    Each MADLAD record is a full web document.  Splitting on newlines
    yields sentence-sized units (web pages use newlines between sentences,
    nav breadcrumbs, and titles — the min_chars filter removes the noise).
    """
    try:
        with fs.open(gz_path, "rb") as raw:
            with gzip.open(raw, "rt", encoding="utf-8", errors="replace") as gz:
                for line in gz:
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        doc = json.loads(line)
                    except json.JSONDecodeError:
                        continue
                    text = doc.get("text", "")
                    for para in text.split("\n"):
                        para = para.strip()
                        if (len(para) >= min_chars
                                and "\t" not in para
                                and "\r" not in para):
                            yield para
    except Exception as e:
        log(f"    WARN: error reading {gz_path}: {e}")


def reservoir_sample_capped(iterable, k: int, max_stream: int,
                            rng: random.Random) -> list:
    """Reservoir-sample up to k items from the first max_stream items of iterable.

    Stops reading once max_stream sentences have been seen, giving diversity
    across the corpus without streaming the full (potentially 50M+) dataset.
    For small languages where total sentences < max_stream, all sentences are
    considered and the result is a uniform random sample of up to k of them.
    """
    reservoir = []
    for i, item in enumerate(iterable):
        if i >= max_stream:
            break
        if len(reservoir) < k:
            reservoir.append(item)
        else:
            j = rng.randint(0, i)
            if j < k:
                reservoir[j] = item
    return reservoir


def process_language(
    fs: "HfFileSystem",
    madlad_code: str,
    canonical: str,
    clean_shards: list[str],
    output_dir: Path,
    max_per_lang: int,
    max_stream: int,
    min_chars: int,
    rng: random.Random,
) -> int:
    lang_dir = output_dir / canonical
    out_file = lang_dir / "sentences_madlad.txt"

    if out_file.exists() and out_file.stat().st_size > 0:
        existing = sum(1 for _ in open(out_file, encoding="utf-8"))
        log(f"  {canonical}: already exists ({existing:,} lines) — skipping")
        return existing

    def shard_sentences(shard, shard_idx):
        """Yield sentences from one shard, printing a count line whether we
        finish the shard or are abandoned mid-way by take()."""
        shard_name = shard.split("/")[-1]
        log(f"    [{shard_idx}/{len(clean_shards)}] {shard_name} ...", end=" ")
        count = 0
        try:
            for para in iter_paragraphs(fs, shard, min_chars):
                count += 1
                yield para
        finally:
            log(f"{count:,} sentences")

    def all_paragraphs():
        for shard_idx, shard in enumerate(clean_shards, 1):
            yield from shard_sentences(shard, shard_idx)

    paragraphs = reservoir_sample_capped(
        all_paragraphs(), max_per_lang, max_stream, rng)

    if not paragraphs:
        log(f"  {canonical}: no sentences after filtering (min_chars={min_chars})")
        return 0

    rng.shuffle(paragraphs)
    lang_dir.mkdir(parents=True, exist_ok=True)
    with open(out_file, "w", encoding="utf-8") as f:
        for i, text in enumerate(paragraphs, 1):
            f.write(f"{i}\t{text}\n")

    log(f"  {canonical}: wrote {len(paragraphs):,} sentences from {len(clean_shards)} shard(s)")
    return len(paragraphs)


def main():
    parser = argparse.ArgumentParser(
        description="Download MADLAD-400 clean subset to Leipzig TSV format",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "output_dir",
        type=Path,
        help="Root directory to write per-language sentence files",
    )
    parser.add_argument(
        "--max-per-lang",
        type=int,
        default=DEFAULT_MAX_PER_LANG,
        metavar="N",
        help="Maximum sentences to keep per language (reservoir sample target)",
    )
    parser.add_argument(
        "--max-stream",
        type=int,
        default=DEFAULT_MAX_STREAM,
        metavar="N",
        help="Maximum sentences to read per language before stopping (reservoir sample pool)",
    )
    parser.add_argument(
        "--min-chars",
        type=int,
        default=DEFAULT_MIN_CHARS,
        metavar="N",
        help="Minimum paragraph length in characters",
    )
    parser.add_argument(
        "--langs",
        nargs="+",
        metavar="LANG",
        help="Process only these codes (MADLAD or ISO 639-3; default: all)",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=DEFAULT_SEED,
        help="Random seed for reproducible reservoir sampling",
    )
    args = parser.parse_args()

    rng = random.Random(args.seed)
    fs = HfFileSystem()

    log(f"Listing languages in {DATASET_PATH} ...")
    lang_dirs = fs.ls(DATASET_PATH, detail=False)
    log(f"  {len(lang_dirs)} language directories found")

    # Build map: madlad_code → (canonical_iso3, [clean_shard_paths])
    lang_plan: dict[str, tuple[str, list[str]]] = {}
    for lang_dir in sorted(lang_dirs):
        madlad_code = lang_dir.split("/")[-1]
        canonical = to_iso3(madlad_code)
        if canonical is None:
            continue
        shards = sorted(
            p for p in fs.ls(lang_dir, detail=False)
            if "_clean_" in p and p.endswith(".jsonl.gz")
        )
        if shards:
            lang_plan[madlad_code] = (canonical, shards)

    if args.langs:
        requested = set(args.langs)
        lang_plan = {
            code: (canon, shards)
            for code, (canon, shards) in lang_plan.items()
            if code in requested or canon in requested
        }
        found = set(lang_plan.keys()) | {c for c, _ in lang_plan.values()}
        missing = requested - found
        if missing:
            log(f"WARNING: not found in MADLAD-400: {sorted(missing)}")

    log(f"Processing {len(lang_plan)} language(s)")
    log(f"  max-per-lang : {args.max_per_lang:,}")
    log(f"  max-stream   : {args.max_stream:,}")
    log(f"  min-chars    : {args.min_chars}")
    log(f"  seed         : {args.seed}")
    log(f"  output       : {args.output_dir}")
    print()

    args.output_dir.mkdir(parents=True, exist_ok=True)

    total = 0
    counts: dict[str, int] = {}
    failed: list[str] = []

    for i, (madlad_code, (canonical, shards)) in enumerate(sorted(lang_plan.items()), 1):
        label = f"{madlad_code} → {canonical}" if madlad_code != canonical else madlad_code
        log(f"[{i}/{len(lang_plan)}] {label}  ({len(shards)} clean shard(s))")
        n = process_language(
            fs, madlad_code, canonical, shards,
            args.output_dir, args.max_per_lang, args.max_stream, args.min_chars, rng,
        )
        if n == 0 and not (args.output_dir / canonical / "sentences_madlad.txt").exists():
            failed.append(canonical)
        counts[canonical] = counts.get(canonical, 0) + n
        total += n

    print(f"\n{'=' * 60}")
    log(f"Done.  Languages: {len(counts)}  Total sentences: {total:,}")
    if failed:
        log(f"Failed / empty: {', '.join(sorted(failed))}")
    print()
    for lang, count in sorted(counts.items(), key=lambda x: -x[1])[:30]:
        print(f"  {lang:8s} {count:>12,}")
    if len(counts) > 30:
        print(f"  ... and {len(counts) - 30} more")


if __name__ == "__main__":
    main()
