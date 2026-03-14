#!/usr/bin/env python3
"""
Download MADLAD-400 clean subset, split documents into line-sized
chunks, reservoir sample up to N lines per language, and write
to Leipzig-compatible directory layout (lang/sentences.txt).

Usage:
    python download_madlad.py <output_dir> [--max-per-lang 500000]
                              [--min-chars 20] [--max-chars 500]
                              [--seed 42]
                              [--languages en,fr,de]
                              [--skip-existing]

Output structure:
    output_dir/
        eng/sentences.txt
        fra/sentences.txt
        ...
    output_dir/download.log

Each sentences.txt contains one text chunk per line (no tab prefix,
no numbering — matches Leipzig format that our training pipeline
expects).

MADLAD-400 uses BCP-47 / ISO 639-1 codes. This script maps them
to ISO 639-3 to match our model's label space.
"""

import argparse
import gzip
import json
import logging
import os
import random
import sys
import time

from huggingface_hub import HfApi, hf_hub_download

MAX_RETRIES = 5
INITIAL_BACKOFF = 30  # seconds

# ---- MADLAD code -> ISO 639-3 mapping ----
MADLAD_TO_ISO3 = {
    "af": "afr", "am": "amh", "ar": "ara", "as": "asm",
    "ay": "aym", "az": "aze", "ba": "bak", "be": "bel",
    "bg": "bul", "bm": "bam", "bn": "ben", "bo": "bod",
    "br": "bre", "bs": "bos", "ca": "cat", "ce": "che",
    "co": "cos", "cr-Latn": "crl", "cs": "ces", "cv": "chv",
    "cy": "cym", "da": "dan", "de": "deu", "dv": "div",
    "dz": "dzo", "ee": "ewe", "el": "ell", "en": "eng",
    "eo": "epo", "es": "spa", "et": "est", "eu": "eus",
    "fa": "fas", "fi": "fin", "fil": "tgl", "fj": "fij",
    "fo": "fao", "fr": "fra", "fy": "fry", "ga": "gle",
    "gd": "gla", "gl": "glg", "gn": "grn", "gu": "guj",
    "ha": "hau", "hi": "hin", "hr": "hrv", "ht": "hat",
    "hu": "hun", "hy": "hye", "id": "ind", "ig": "ibo",
    "ilo": "ilo", "is": "isl", "it": "ita", "iu": "iku",
    "he": "heb", "iw": "heb", "ja": "jpn", "jv": "jav", "ka": "kat",
    "kg": "kon", "kk": "kaz", "kl": "kal", "km": "khm",
    "kn": "kan", "ko": "kor", "ku": "kur", "kw": "cor",
    "ky": "kir", "la": "lat", "lb": "ltz", "lg": "lug",
    "ln": "lin", "lo": "lao", "lt": "lit", "lv": "lav",
    "mg": "mlg", "mi": "mri", "mk": "mkd", "ml": "mal",
    "mn": "mon", "mr": "mar", "ms": "msa", "mt": "mlt",
    "my": "mya", "ne": "nep", "nl": "nld", "no": "nob",
    "nv": "nav", "ny": "nya", "oc": "oci", "om": "orm",
    "or": "ori", "os": "oss", "pa": "pan", "pl": "pol",
    "ps": "pus", "pt": "por", "qu": "que", "rm": "roh",
    "rn": "run", "ro": "ron", "ru": "rus", "rw": "kin",
    "sa": "san", "sd": "snd", "se": "sme", "sg": "sag",
    "si": "sin", "sk": "slk", "sl": "slv", "sm": "smo",
    "sn": "sna", "so": "som", "sq": "sqi", "sr": "srp",
    "ss": "ssw", "st": "sot", "su": "sun", "sv": "swe",
    "sw": "swh", "ta": "tam", "te": "tel", "tg": "tgk",
    "th": "tha", "ti": "tir", "tk": "tuk", "to": "ton",
    "tr": "tur", "ts": "tso", "tt": "tat", "ug": "uig",
    "uk": "ukr", "ur": "urd", "uz": "uzb", "ve": "ven",
    "vi": "vie", "vo": "vol", "wa": "wln", "wo": "wol",
    "xh": "xho", "yi": "yid", "yo": "yor", "zh": "zho",
    "zu": "zul",
    "ace": "ace", "ach": "ach", "ady": "ady", "ak": "aka",
    "alt": "alt", "arn": "arn", "arz": "arz",
    "ban": "ban", "bar": "bar", "bcl": "bcl",
    "ber-Latn": "kab", "bho": "bho", "bik": "bcl",
    "bua": "bua", "ceb": "ceb", "chm": "mhr",
    "ckb": "ckb", "crh": "crh", "csb": "csb",
    "dyu": "dyu", "eml": "eml", "ext": "ext",
    "fon": "fon", "frp": "frp", "gag": "gag",
    "gom": "gom", "gsw": "gsw", "haw": "haw",
    "hif": "hif", "hil": "hil", "hmn": "hmn",
    "ibb": "ibb", "iba": "iba", "kab": "kab",
    "kac": "kac", "kbd": "kbd", "kha": "kha",
    "kjh": "kjh", "krc": "krc", "kri": "kri",
    "kum": "kum", "lrc": "lrc", "ltg": "ltg",
    "lus": "lus", "mai": "mai", "mak": "mak",
    "mdf": "mdf", "min": "min", "mrj": "mrj",
    "myv": "myv", "new": "new", "nso": "nso",
    "pag": "pag", "pap": "pap", "pms": "pms",
    "rom": "rom", "sah": "sah", "scn": "scn",
    "sco": "sco", "seh": "seh", "shn": "shn",
    "stq": "stq", "szl": "szl", "tet": "tet",
    "tiv": "tiv", "tyv": "tyv", "udm": "udm",
    "vec": "vec", "war": "war", "xal": "xal",
    "zza": "zza",
    "az-RU": "azb", "kaa": "kaa", "kaa-Latn": "kaa",
    "nan-Latn-TW": "nan", "nog": "nog",
}

SKIP_LANGS = {
    "zxx-xx-dtynoise", "tlh",
    "el-Latn", "bg-Latn", "ru-Latn",
    "hi-Latn", "te-Latn", "ta-Latn", "ml-Latn",
    "kn-Latn", "gom-Latn",
}


def map_lang(madlad_code):
    if madlad_code in SKIP_LANGS:
        return None
    return MADLAD_TO_ISO3.get(madlad_code, madlad_code)


def list_clean_shards(madlad_code):
    """List clean JSONL.gz shard paths for a language.
    Handles hyphen/underscore variants in directory names."""
    api = HfApi()
    # MADLAD uses underscores in paths but hyphens in docs
    variants = [madlad_code, madlad_code.replace("-", "_")]
    for variant in variants:
        prefix = f"data/{variant}"
        clean_suffix = "_clean_"
        shards = []
        try:
            for entry in api.list_repo_tree(
                    "allenai/MADLAD-400", repo_type="dataset",
                    path_in_repo=prefix):
                name = (entry.path if hasattr(entry, 'path')
                        else str(entry))
                if clean_suffix in name and name.endswith(".jsonl.gz"):
                    shards.append(name)
        except Exception:
            continue
        if shards:
            return sorted(shards)
    return []


def process_shard(shard_path, max_lines, min_chars, max_chars,
                  rng, reservoir, seen):
    """
    Download one shard, reservoir sample full documents.
    Documents are kept intact with literal \\n separators preserved,
    matching the format of existing sentences_madlad.txt files.
    Returns updated (reservoir, seen).
    """
    local = hf_hub_download(
        "allenai/MADLAD-400", shard_path,
        repo_type="dataset")

    with gzip.open(local, "rt", encoding="utf-8") as f:
        for raw_line in f:
            doc = json.loads(raw_line)
            text = doc.get("text", "")
            if not text:
                continue
            text = text.strip()
            if len(text) < min_chars:
                continue
            seen += 1
            if len(reservoir) < max_lines:
                reservoir.append(text)
            else:
                j = rng.randint(0, seen - 1)
                if j < max_lines:
                    reservoir[j] = text

            if len(reservoir) >= max_lines and seen >= max_lines * 10:
                break

    return reservoir, seen


def get_available_languages():
    """Get list of available MADLAD-400 language codes."""
    api = HfApi()
    langs = set()
    for entry in api.list_repo_tree(
            "allenai/MADLAD-400", repo_type="dataset",
            path_in_repo="data"):
        name = entry.path if hasattr(entry, 'path') else str(entry)
        code = name.replace("data/", "")
        if code and "/" not in code:
            langs.add(code)
    return sorted(langs)


def main():
    parser = argparse.ArgumentParser(
        description="Download MADLAD-400 clean subset for "
                    "language detection training")
    parser.add_argument("output_dir",
                        help="Output directory for lang/sentences.txt")
    parser.add_argument("--max-per-lang", type=int, default=500_000,
                        help="Max lines per language (default 500000)")
    parser.add_argument("--min-chars", type=int, default=20,
                        help="Min line length in chars (default 20)")
    parser.add_argument("--max-chars", type=int, default=500,
                        help="Max line length in chars (default 500)")
    parser.add_argument("--seed", type=int, default=42,
                        help="Random seed (default 42)")
    parser.add_argument("--languages", type=str, default=None,
                        help="Comma-separated list of MADLAD lang codes "
                             "to download (default: all)")
    parser.add_argument("--skip-existing", action="store_true",
                        help="Skip languages that already have "
                             "sentences.txt")
    parser.add_argument("--list-languages", action="store_true",
                        help="List available languages and exit")
    args = parser.parse_args()

    os.makedirs(args.output_dir, exist_ok=True)
    log_file = os.path.join(args.output_dir, "download.log")
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(message)s",
        handlers=[
            logging.FileHandler(log_file),
            logging.StreamHandler()
        ])
    log = logging.getLogger(__name__)

    if args.list_languages:
        log.info("Fetching available languages...")
        langs = get_available_languages()
        log.info(f"Available: {len(langs)} languages")
        for lang in sorted(langs):
            iso = map_lang(lang)
            marker = " [skip]" if iso is None else ""
            log.info(f"  {lang:>15} -> {iso}{marker}")
        return

    if args.languages:
        madlad_langs = [l.strip()
                        for l in args.languages.split(",")]
    else:
        log.info("Fetching language list from HuggingFace...")
        madlad_langs = sorted(get_available_languages())
        log.info(f"Found {len(madlad_langs)} languages")

    lang_plan = []
    for ml in madlad_langs:
        iso = map_lang(ml)
        if iso is None:
            log.info(f"  Skipping {ml} (in skip list)")
            continue
        lang_plan.append((ml, iso))

    log.info(f"Will download {len(lang_plan)} languages "
             f"(max {args.max_per_lang:,} lines each)")

    total_start = time.time()
    total_downloaded = 0
    total_skipped = 0
    failed = []

    for i, (madlad_code, iso_code) in enumerate(lang_plan):
        lang_dir = os.path.join(args.output_dir, iso_code)
        out_file = os.path.join(lang_dir, "sentences_madlad.txt")

        if args.skip_existing and os.path.exists(out_file):
            log.info(f"[{i+1}/{len(lang_plan)}] {madlad_code} -> "
                     f"{iso_code}: skipping (exists)")
            total_skipped += 1
            continue

        log.info(f"[{i+1}/{len(lang_plan)}] {madlad_code} -> "
                 f"{iso_code}: downloading...")
        lang_start = time.time()

        try:
            shards = list_clean_shards(madlad_code)
            if not shards:
                log.warning(f"  {iso_code}: no clean shards found")
                continue

            # Shuffle shards and pick enough to fill the reservoir.
            # ~18 qualifying lines per doc, ~5k docs per shard
            # => ~90k lines per shard.  Be generous.
            lang_rng = random.Random(args.seed + hash(madlad_code))
            lang_rng.shuffle(shards)
            lines_per_shard = 50_000  # conservative estimate
            needed_shards = max(
                1, args.max_per_lang // lines_per_shard + 1)
            selected = shards[:min(needed_shards, len(shards))]
            log.info(f"  {len(shards)} clean shards, "
                     f"using {len(selected)}")

            reservoir = []
            seen = 0
            rng = random.Random(args.seed)

            for si, shard in enumerate(selected):
                reservoir, seen = process_shard(
                    shard, args.max_per_lang, args.min_chars,
                    args.max_chars, rng, reservoir, seen)
                log.info(f"    shard {si+1}/{len(selected)}: "
                         f"{len(reservoir):,} lines, "
                         f"{seen:,} seen")
                if (len(reservoir) >= args.max_per_lang
                        and seen >= args.max_per_lang * 10):
                    break

            if not reservoir:
                log.warning(f"  {iso_code}: no lines passed filter")
                continue

            # Final shuffle
            lang_rng.shuffle(reservoir)

            os.makedirs(lang_dir, exist_ok=True)
            with open(out_file, "w", encoding="utf-8") as f:
                for i, line in enumerate(reservoir, 1):
                    f.write(f"{i}\t{line}\n")

            elapsed = time.time() - lang_start
            log.info(f"  {iso_code}: wrote {len(reservoir):,} lines "
                     f"(from {seen:,} candidates) "
                     f"[{elapsed:.1f}s]")
            total_downloaded += 1

        except Exception as e:
            elapsed = time.time() - lang_start
            log.error(f"  {iso_code}: FAILED after {elapsed:.1f}s: "
                      f"{e}")
            failed.append((madlad_code, iso_code, str(e)))

    total_elapsed = time.time() - total_start
    log.info(f"\nDone! {total_downloaded} downloaded, "
             f"{total_skipped} skipped, {len(failed)} failed "
             f"[{total_elapsed/60:.1f} min]")
    if failed:
        log.info("Failed languages:")
        for mc, ic, err in failed:
            log.info(f"  {mc} -> {ic}: {err}")


if __name__ == "__main__":
    main()
