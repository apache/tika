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
Collect Wikipedia sentences for language-detection training via HuggingFace datasets.

For each target language:
  1. Load Wikipedia via HF datasets (streaming, shuffled by buffer)
  2. Split articles into sentences with wtpsplit SaT
  3. Clean sentences (length, alpha ratio, wiki-artifact patterns)
  4. Filter out sentences fastText confidently predicts as a different top-10 language
     (mirrors filter_contamination.py; confusable pairs are respected)
  5. Exact-deduplicate within each language
  6. Write up to --max-per-lang sentences in Leipzig format (lineNum<TAB>sentence)
     to <output_dir>/<iso3>/sentences.txt

By default, all Wikipedia language editions available for --dump-date are collected.
Use --langs to restrict to a specific subset.

Usage:
    pip install datasets wtpsplit fasttext

    # Collect everything Wikipedia has for the default dump date:
    python collect_wikipedia.py ~/datasets/wikipedia/lang-detect

    # Restrict to a subset, override defaults:
    python collect_wikipedia.py ~/datasets/wikipedia/lang-detect \\
        --max-per-lang 500000 \\
        --langs eng fra deu spa \\
        --ft-model ~/datasets/madlad/lang-detect/lid.176.bin

Output structure:
    output_dir/
        eng/sentences.txt
        fra/sentences.txt
        ...

Each sentences.txt is in Leipzig format: lineNum<TAB>sentence
"""

import argparse
import logging
import re
import sys
from pathlib import Path

import fasttext

fasttext.FastText.eprint = lambda x: None  # suppress noisy load message

from datasets import get_dataset_config_names, load_dataset

log = logging.getLogger(__name__)

# ── fastText top-10 filter ────────────────────────────────────────────────────
# Keep in sync with filter_contamination.py.
TOP10_FT_TO_639_3 = {
    "__label__en": "eng",
    "__label__zh": "zho",
    "__label__es": "spa",
    "__label__ar": "ara",
    "__label__pt": "por",
    "__label__ru": "rus",
    "__label__fr": "fra",
    "__label__de": "deu",
    "__label__ja": "jpn",
    "__label__ko": "kor",
}

# Default threshold for top-10 language filtering.
# English gets a lower threshold because it's highly distinctive and bibliography
# entries in foreign Wikipedias often score 0.60-0.75 English confidence.
THRESHOLD = 0.80
THRESHOLD_EN = 0.65

# ── Sentence cleaning ─────────────────────────────────────────────────────────
MIN_CHARS = 50
MAX_CHARS = 500
MIN_ALPHA_RATIO = 0.5
_DIRTY = re.compile(
    r'https?://|www\.'          # URLs
    r'|{{|\[\[|\]\]'            # wiki markup
    r'|^\s*[|!]'                # table syntax
    r'|\(PDF\)'                 # PDF artifact
    r'|^\([a-z]{2,3}(?:,\s*[a-z]{2,3})*\)\s'  # cross-lang citation labels like (en) (en, fr)
    r'|\(:[a-z]{2,3}:'         # inline cross-wiki links like (:en: (:hi:
    r'|\bISBN\b|\bISSN\b'      # bibliography markers
    r'|Lorem ipsum'             # placeholder text
)


# ── Wikipedia language code mapping (ISO 639-3 → HF Wikipedia config code) ───
# HF Wikipedia dataset configs use Wikipedia's own language codes (mostly ISO 639-1).
# Add entries here for any language not yet covered.
ISO3_TO_WIKI = {
    "ace": "ace", "afr": "af",  "aka": "ak",  "amh": "am",  "ara": "ar",
    "arg": "an",  "asm": "as",  "ava": "av",  "aze": "az",  "bak": "ba",
    "bel": "be",  "ben": "bn",  "bis": "bi",  "bos": "bs",  "bre": "br",
    "bul": "bg",  "mya": "my",  "cat": "ca",  "ceb": "ceb", "che": "ce",
    "zho": "zh",  "chv": "cv",  "cor": "kw",  "cos": "co",  "hrv": "hr",
    "ces": "cs",  "dan": "da",  "div": "dv",  "nld": "nl",  "ell": "el",
    "eng": "en",  "epo": "eo",  "est": "et",  "ewe": "ee",  "fao": "fo",
    "fin": "fi",  "fra": "fr",  "fry": "fy",  "glg": "gl",  "kat": "ka",
    "deu": "de",  "grn": "gn",  "guj": "gu",  "hat": "ht",  "hau": "ha",
    "heb": "he",  "hin": "hi",  "hun": "hu",  "ina": "ia",  "ind": "id",
    "gle": "ga",  "ibo": "ig",  "ido": "io",  "isl": "is",  "ita": "it",
    "jpn": "ja",  "jav": "jv",  "kan": "kn",  "kaz": "kk",  "khm": "km",
    "kin": "rw",  "kir": "ky",  "kor": "ko",  "kur": "ku",  "lao": "lo",
    "lat": "la",  "lav": "lv",  "lin": "ln",  "lit": "lt",  "ltz": "lb",
    "mkd": "mk",  "mlg": "mg",  "msa": "ms",  "mal": "ml",  "mlt": "mt",
    "glv": "gv",  "mar": "mr",  "min": "min", "mon": "mn",  "nep": "ne",
    "new": "new", "nob": "nb",  "nno": "nn",  "oci": "oc",  "ori": "or",
    "oss": "os",  "pan": "pa",  "fas": "fa",  "pol": "pl",  "pus": "ps",
    "por": "pt",  "que": "qu",  "roh": "rm",  "ron": "ro",  "rus": "ru",
    "san": "sa",  "srd": "sc",  "snd": "sd",  "srp": "sr",  "gla": "gd",
    "sin": "si",  "slk": "sk",  "slv": "sl",  "som": "so",  "spa": "es",
    "sun": "su",  "swa": "sw",  "swe": "sv",  "tam": "ta",  "tel": "te",
    "tgk": "tg",  "tha": "th",  "bod": "bo",  "tuk": "tk",  "tgl": "tl",
    "tur": "tr",  "tat": "tt",  "uig": "ug",  "ukr": "uk",  "urd": "ur",
    "uzb": "uz",  "vie": "vi",  "cym": "cy",  "wol": "wo",  "xho": "xh",
    "yid": "yi",  "yor": "yo",  "zul": "zu",  "hbs": "sh",  "sco": "sco",
    "war": "war", "orm": "om",  "hye": "hy",  "mri": "mi",  "nde": "nd",
    "kal": "kl",
    # ── Languages in the fastText model with Wikipedia editions ──────────────
    "arz": "arz",  "ast": "ast",  "bar": "bar",  "bcl": "bcl",
    "bpy": "bpy",  "bxr": "bxr",  "ckb": "ckb",  "diq": "diq",
    "dsb": "dsb",  "dty": "dty",  "eml": "eml",  "frr": "frr",
    "gsw": "als",  # Alemannic German — Wikipedia uses "als" (wrong ISO but established)
    "hsb": "hsb",  "ilo": "ilo",  "jbo": "jbo",
    "kpv": "kv",   # Komi-Zyrian — Wikipedia uses "kv"
    "krc": "krc",  "lmo": "lmo",  "lrc": "lrc",  "mai": "mai",
    "mhr": "mhr",  "mrj": "mrj",  "mwl": "mwl",  "mzn": "mzn",
    "nah": "nah",  "nan": "nan",  "nap": "nap",  "nds": "nds",
    "pam": "pam",  "pfl": "pfl",  "pms": "pms",  "pnb": "pnb",
    "rue": "rue",  "sah": "sah",  "scn": "scn",
    "vec": "vec",  "vep": "vep",  "vls": "vls",  "wuu": "wuu",
    "xal": "xal",  "xmf": "xmf",  "yue": "yue",
    # ── Wikipedia editions without fastText coverage ──────────────────────────
    "lzh": "zh-classical",  # Classical Chinese
    "sgs": "bat-smg",       # Samogitian
    "rup": "roa-rup",       # Aromanian
    "vro": "fiu-vro",       # Võro
    "cbk": "cbk-zam",       # Chavacano
    # ── Previously unmapped: real languages now covered ───────────────────────
    "abk": "ab",   "ady": "ady",  "alt": "alt",  "ami": "ami",
    "ang": "ang",  "anp": "anp",  "arc": "arc",  "ary": "ary",
    "atj": "atj",  "avk": "avk",  "awa": "awa",  "aym": "ay",
    "azb": "azb",  "ban": "ban",  "bho": "bh",   "bjn": "bjn",
    "blk": "blk",  "bam": "bm",   "bug": "bug",  "cdo": "cdo",
    "cha": "ch",   "chr": "chr",  "chy": "chy",  "cre": "cr",
    "crh": "crh",  "csb": "csb",  "chu": "cu",   "dag": "dag",
    "din": "din",  "dzo": "dz",   "eus": "eu",   "ext": "ext",
    "fat": "fat",  "ful": "ff",   "fij": "fj",   "fon": "fon",
    "frp": "frp",  "fur": "fur",  "gag": "gag",  "gan": "gan",
    "gcr": "gcr",  "glk": "glk",  "gom": "gom",  "gor": "gor",
    "got": "got",  "guc": "guc",  "gur": "gur",  "guw": "guw",
    "hak": "hak",  "haw": "haw",  "hif": "hif",  "hyw": "hyw",
    "ile": "ie",   "ipk": "ik",   "inh": "inh",  "iku": "iu",
    "jam": "jam",  "kaa": "kaa",  "kab": "kab",  "kbd": "kbd",
    "kbp": "kbp",  "kcg": "kcg",  "kon": "kg",   "kik": "ki",
    "koi": "koi",  "kas": "ks",   "ksh": "ksh",  "lad": "lad",
    "lbe": "lbe",  "lez": "lez",  "lfn": "lfn",  "lug": "lg",
    "lim": "li",   "lij": "lij",  "lld": "lld",  "ltg": "ltg",
    "mad": "mad",  "mdf": "mdf",  "mni": "mni",  "mnw": "mnw",
    "myv": "myv",  "nia": "nia",  "nov": "nov",  "nqo": "nqo",
    "nrm": "nrm",  "nso": "nso",  "nav": "nv",   "nya": "ny",
    "olo": "olo",  "pag": "pag",  "pap": "pap",  "pcd": "pcd",
    "pcm": "pcm",  "pdc": "pdc",  "pli": "pi",   "pih": "pih",
    "pnt": "pnt",  "pwn": "pwn",  "rmy": "rmy",  "run": "rn",
    "sat": "sat",  "sme": "se",   "sag": "sg",   "shi": "shi",
    "shn": "shn",  "skr": "skr",  "smo": "sm",   "smn": "smn",
    "sna": "sn",   "sqi": "sq",   "srn": "srn",  "ssw": "ss",
    "sot": "st",   "stq": "stq",  "szl": "szl",  "szy": "szy",
    "tay": "tay",  "tcy": "tcy",  "tet": "tet",  "tir": "ti",
    "tly": "tly",  "tsn": "tn",   "ton": "to",   "tpi": "tpi",
    "trv": "trv",  "tso": "ts",   "tum": "tum",  "twi": "tw",
    "tah": "ty",   "tyv": "tyv",  "udm": "udm",  "ven": "ve",
    "vol": "vo",   "wln": "wa",   "zha": "za",   "zea": "zea",
    # ── Additional Wikipedia editions identified as missing ───────────────────
    "hmn": "hmn",  # Hmong — Wikipedia edition exists; also supplemented by MADLAD
    "hil": "hil",  # Hiligaynon — Wikipedia edition exists
}

# Reverse mapping: Wikipedia/ISO 639-1 code → ISO 639-3.
# Start from the auto-derived inverse, then apply explicit overrides for cases
# where a wiki code is ambiguous or the automatic choice would be wrong.
WIKI_TO_ISO3: dict[str, str] = {v: k for k, v in ISO3_TO_WIKI.items()}

# Explicit overrides — document intentional decisions here.
_WIKI_TO_ISO3_OVERRIDES: dict[str, str] = {
    # Generic Norwegian Wikipedia (no.wikipedia.org) is written in Bokmål.
    # nob (nb) has ~600k articles; nno (nn) has ~160k. Use the dominant form.
    # Pass "--langs nno" explicitly if you want Nynorsk (nn.wikipedia.org).
    "no": "nob",
    # Serbo-Croatian (sh.wikipedia.org) — map to the combined hbs code.
    "sh": "hbs",
    # Hyphenated wiki codes that can't be auto-derived from ISO3_TO_WIKI.
    # These need explicit entries so get_all_wikipedia_langs() can pick them up.
    "zh-min-nan": "nan",   # Min Nan — alternate HF code alongside bare "nan"
    "zh-yue":     "yue",   # Cantonese — alternate HF code alongside bare "yue"
    # be-tarask / be-x-old: both are Belarusian Taraškievica orthography (iso3=bel).
    # Collision with primary "be" → each gets its own directory via fallback naming.
    "be-tarask":  "bel",
    "be-x-old":   "bel",
    # nds-nl (Low Saxon in Netherlands) shares iso3 with nds (Low German).
    # Collision handled by fallback naming → downloads to nds_nl/.
    "nds-nl":     "nds",
}
WIKI_TO_ISO3.update(_WIKI_TO_ISO3_OVERRIDES)


def resolve_lang(code: str) -> str | None:
    """Accept either an ISO 639-3 code ('fra') or a wiki/ISO 639-1 code ('fr').
    Returns the canonical ISO 639-3 code, or None if unrecognised."""
    if code in ISO3_TO_WIKI:
        return code
    if code in WIKI_TO_ISO3:
        return WIKI_TO_ISO3[code]
    return None


def get_all_wikipedia_langs(dump_date: str) -> list[tuple[str, str]]:
    """Query HF for all Wikipedia configs matching dump_date and return
    (iso3, wiki_code) pairs for every edition we can map to ISO 639-3.
    Unmapped wiki codes are logged at DEBUG level and skipped.
    """
    log.info(f"Fetching available Wikipedia configs from HuggingFace ...")
    try:
        all_configs = get_dataset_config_names("wikimedia/wikipedia")
    except Exception as e:
        log.error(f"Could not fetch Wikipedia config list: {e}")
        return []

    # Simple English is English — skip it rather than downloading a duplicate.
    _SKIP_WIKI_CODES = {"simple"}

    prefix = f"{dump_date}."
    claimed: dict[str, str] = {}   # iso3 → output_name that already owns it
    langs: list[tuple[str, str, str]] = []  # (output_name, wiki_code, iso3)

    for config in sorted(all_configs):
        if not config.startswith(prefix):
            continue
        wiki_code = config[len(prefix):]
        if wiki_code in _SKIP_WIKI_CODES:
            continue

        iso3 = WIKI_TO_ISO3.get(wiki_code)
        if iso3 is None:
            # No ISO 639-3 mapping yet — download anyway using sanitized wiki
            # code as directory name. Rename/merge after reviewing the data.
            output_name = wiki_code.replace("-", "_")
            iso3 = output_name   # dummy iso3: fasttext filter won't fire
            log.debug(f"No ISO 639-3 mapping for '{wiki_code}' — downloading to '{output_name}/'")
        elif iso3 in claimed:
            # Two wiki editions share an iso3 — keep both in distinct directories.
            output_name = wiki_code.replace("-", "_")
            log.warning(
                f"wiki '{wiki_code}' maps to iso3 '{iso3}' already claimed by "
                f"'{claimed[iso3]}' — downloading to '{output_name}/'. "
                f"Resolve mapping after download."
            )
        else:
            output_name = iso3
            claimed[iso3] = output_name

        langs.append((output_name, wiki_code, iso3))

    log.info(f"Found {len(langs)} Wikipedia editions for {dump_date}")

    return langs

# ── Confusables (single source of truth: confusables.txt) ────────────────────
_CONFUSABLES_TXT = (
    Path(__file__).parent
    / "../../main/resources/org/apache/tika/langdetect/charsoup/confusables.txt"
).resolve()


def _load_confusables(path: Path) -> dict[str, set[str]]:
    cmap: dict[str, set[str]] = {}
    if not path.exists():
        log.warning(f"confusables.txt not found: {path} — confusable check disabled")
        return cmap
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#"):
                group = set(line.split(","))
                for lang in group:
                    cmap[lang] = group
    return cmap


_CONFUSABLE_MAP = _load_confusables(_CONFUSABLES_TXT)


def is_confusable(lang_a: str, lang_b: str) -> bool:
    g = _CONFUSABLE_MAP.get(lang_a)
    return g is not None and lang_b in g


def should_remove(ft_model, iso3: str, text: str) -> bool:
    """Return True if fastText confidently predicts a different top-10 language."""
    labels, probs = ft_model.predict(text.replace("\n", " "), k=1)
    ft_iso3 = TOP10_FT_TO_639_3.get(labels[0])
    threshold = THRESHOLD_EN if ft_iso3 == "eng" else THRESHOLD
    return (
        ft_iso3 is not None
        and ft_iso3 != iso3
        and float(probs[0]) >= threshold
        and not is_confusable(iso3, ft_iso3)
    )


# ── Sentence cleaning ─────────────────────────────────────────────────────────

def clean(sentence: str) -> str | None:
    s = " ".join(sentence.split())  # normalize all whitespace including embedded \n
    if not (MIN_CHARS <= len(s) <= MAX_CHARS):
        return None
    if _DIRTY.search(s):
        return None
    if sum(c.isalpha() for c in s) / len(s) < MIN_ALPHA_RATIO:
        return None
    return s


# ── Per-language collection ───────────────────────────────────────────────────

# Large Wikipedias have many shards; a bigger buffer ensures the within-shard
# window covers more than just the first slice we'll actually consume.
_LARGE_WIKI = {
    "en", "de", "fr", "es", "it", "pt", "nl", "pl", "ru",
    "sv", "zh", "ja", "ar", "uk", "vi", "ko", "fa",
}


def _shuffle_buffer(wiki_code: str) -> int:
    return 50_000 if wiki_code in _LARGE_WIKI else 10_000


def split_paragraphs(text: str) -> list[str]:
    """Split Wikipedia article text into sentence-sized chunks.

    Split on every newline so section headers become their own short candidates
    and get filtered by MIN_CHARS in clean(). Lines with = markers are stripped
    immediately. Long lines are split at sentence boundaries.
    """
    chunks = []
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        if len(line) <= MAX_CHARS:
            chunks.append(line)
        else:
            parts = re.split(r'(?<=[.!?])\s+', line)
            current = ""
            for part in parts:
                if not current:
                    current = part
                elif len(current) + 1 + len(part) <= MAX_CHARS:
                    current += " " + part
                else:
                    chunks.append(current)
                    current = part
            if current:
                chunks.append(current)
    return chunks


def collect(output_name: str, iso3: str, wiki_code: str, ft_model,
            target: int, dump_date: str) -> list[str]:  # noqa: E501
    config = f"{dump_date}.{wiki_code}"
    log.info(f"[{output_name}] Loading wikipedia/{config} (streaming, shuffled) ...")

    try:
        ds = (
            load_dataset("wikimedia/wikipedia", config, split="train",
                         streaming=True)
            .shuffle(buffer_size=_shuffle_buffer(wiki_code), seed=42)
        )
    except Exception as e:
        log.warning(f"[{output_name}] Could not load {config}: {e}")
        return []

    collected: list[str] = []
    seen: set[str] = set()
    articles_read = removed_clean = removed_ft = dupes = 0

    for article in ds:
        if len(collected) >= target:
            break
        text = article.get("text", "")
        if not text:
            continue
        articles_read += 1

        for sent in split_paragraphs(text):
            s = clean(sent)
            if s is None:
                removed_clean += 1
                continue
            if s in seen:
                dupes += 1
                continue
            if should_remove(ft_model, iso3, s):
                removed_ft += 1
                continue
            seen.add(s)
            collected.append(s)
            if len(collected) >= target:
                break

    log.info(
        f"[{output_name}] {len(collected):,} collected | {articles_read:,} articles | "
        f"dropped: clean={removed_clean:,}  ft={removed_ft:,}  dupes={dupes:,}"
    )
    return collected


# ── Output ────────────────────────────────────────────────────────────────────

def write_sentences(out_dir: Path, iso3: str, sentences: list[str]) -> None:
    lang_dir = out_dir / iso3
    lang_dir.mkdir(parents=True, exist_ok=True)
    out_file = lang_dir / "sentences.txt"
    with open(out_file, "w", encoding="utf-8") as f:
        for i, sent in enumerate(sentences, 1):
            f.write(f"{i}\t{sent}\n")
    log.info(f"[{iso3}] → {out_file}")


# ── Main ──────────────────────────────────────────────────────────────────────

def main() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
        datefmt="%H:%M:%S",
    )

    parser = argparse.ArgumentParser(
        description="Collect Wikipedia sentences for language-detection training"
    )
    parser.add_argument(
        "output_dir", type=Path,
        help="Root directory to write per-language sentence files",
    )
    parser.add_argument(
        "--langs", nargs="+", default=None, metavar="LANG",
        help="Language codes to collect, ISO 639-3 or wiki/ISO 639-1 (e.g. eng or en). "
             "Default: all Wikipedia editions available for --dump-date.",
    )
    parser.add_argument(
        "--max-per-lang", type=int, default=500_000,
        help="Maximum sentences per language (default: 500000)",
    )
    parser.add_argument(
        "--dump-date", default="20231101",
        help="Wikipedia dump date used in HF dataset config, e.g. 20231101 (default: 20231101)",
    )
    parser.add_argument(
        "--ft-model", type=Path,
        default=Path("~/datasets/madlad/lang-detect/lid.176.bin").expanduser(),
        help="Path to fastText lid.176.bin",
    )
    parser.add_argument(
        "--skip-existing", action="store_true",
        help="Skip languages that already have a non-empty sentences.txt",
    )
    args = parser.parse_args()

    if not args.ft_model.exists():
        log.error(f"fastText model not found: {args.ft_model}")
        log.error(
            "Download with:\n"
            "  curl -L -o ~/datasets/madlad/lang-detect/lid.176.bin "
            "https://dl.fbaipublicfiles.com/fasttext/supervised-models/lid.176.bin"
        )
        sys.exit(1)

    log.info(f"Loading fastText model: {args.ft_model}")
    ft_model = fasttext.load_model(str(args.ft_model))


    args.output_dir.mkdir(parents=True, exist_ok=True)

    missing_map: list[str] = []
    skipped: list[str] = []
    failed: list[str] = []
    counts: dict[str, int] = {}

    # Build (iso3, wiki_code) work list
    if args.langs is None:
        work = get_all_wikipedia_langs(args.dump_date)
    else:
        work = []
        for raw in args.langs:
            iso3 = resolve_lang(raw)
            if iso3 is None:
                log.warning(f"[{raw}] Unrecognised language code — skipping")
                missing_map.append(raw)
            else:
                wiki_code = ISO3_TO_WIKI[iso3]
                work.append((iso3, wiki_code, iso3))  # output_name == iso3 for explicit --langs

    log.info(f"Processing {len(work)} languages, up to {args.max_per_lang:,} sentences each")

    for output_name, wiki_code, iso3 in work:
        out_file = args.output_dir / output_name / "sentences.txt"
        if args.skip_existing and out_file.exists() and out_file.stat().st_size > 0:
            log.info(f"[{output_name}] Already exists — skipping")
            skipped.append(output_name)
            continue

        sentences = collect(output_name, iso3, wiki_code, ft_model,
                            args.max_per_lang, args.dump_date)
        if not sentences:
            failed.append(output_name)
            continue

        write_sentences(args.output_dir, output_name, sentences)
        counts[output_name] = len(sentences)

    print(f"\n{'=' * 60}")
    print(f"Wikipedia collection complete")
    print(f"Collected:          {len(counts)} languages, "
          f"{sum(counts.values()):,} total sentences")
    if skipped:
        print(f"Skipped (existing): {len(skipped)}")
    if missing_map:
        print(f"No wiki mapping:    {', '.join(missing_map)}")
    if failed:
        print(f"Failed/empty:       {', '.join(failed)}")
    print()
    for iso3, n in sorted(counts.items(), key=lambda x: -x[1]):
        print(f"  {iso3:<8s} {n:>10,}")


if __name__ == "__main__":
    main()
