#!/usr/bin/env python3
"""
Evaluate fastText lid.176.bin on lang-tab-text eval files.

Produces the same metrics as the Java TrainLanguageModel / CompareDetectors
evaluation:
  macroF1, accuracy at @20/50/100/200/500/full chars, with wall-clock timing.
Worst-10 languages at @full and a per-language TSV are also written.

Usage:
    python3 eval_fasttext.py [file1.txt [file2.txt ...]]

Defaults to flores200_devtest.tsv in ~/datasets/flores-200/ if no
files are given.

The "fastText section" written to stdout is designed to be appended to the
Java CompareDetectors report so all four detectors appear in one document.
"""

import os
import sys
import statistics
import time
from collections import defaultdict

import fasttext
fasttext.FastText.eprint = lambda x: None   # suppress noisy load message

# ── Complete fastText lid.176 label → ISO 639-3 mapping ──────────────────
# fastText uses mostly ISO 639-1 (2-letter) codes; some are already 3-letter.
# This covers all 176 languages in lid.176.bin.
FT_TO_ISO3 = {
    "__label__ace": "ace",  "__label__af":  "afr",  "__label__als": "gsw",
    "__label__am":  "amh",  "__label__an":  "arg",  "__label__ar":  "ara",
    "__label__arz": "arz",  "__label__as":  "asm",  "__label__ast": "ast",
    "__label__av":  "ava",  "__label__az":  "aze",  "__label__azb": "azb",
    "__label__ba":  "bak",  "__label__bar": "bar",  "__label__bcl": "bcl",
    "__label__be":  "bel",  "__label__bg":  "bul",  "__label__bh":  "bho",
    "__label__bn":  "ben",  "__label__bo":  "bod",  "__label__bpy": "bpy",
    "__label__br":  "bre",  "__label__bs":  "bos",  "__label__bxr": "bxr",
    "__label__ca":  "cat",  "__label__cbk": "cbk",  "__label__ce":  "che",
    "__label__ceb": "ceb",  "__label__ckb": "ckb",  "__label__co":  "cos",
    "__label__cs":  "ces",  "__label__cv":  "chv",  "__label__cy":  "cym",
    "__label__da":  "dan",  "__label__de":  "deu",  "__label__diq": "diq",
    "__label__dsb": "dsb",  "__label__dty": "dty",  "__label__dv":  "div",
    "__label__el":  "ell",  "__label__eml": "eml",  "__label__en":  "eng",
    "__label__eo":  "epo",  "__label__es":  "spa",  "__label__et":  "est",
    "__label__eu":  "eus",  "__label__fa":  "fas",  "__label__fi":  "fin",
    "__label__fr":  "fra",  "__label__frr": "frr",  "__label__fy":  "fry",
    "__label__ga":  "gle",  "__label__gd":  "gla",  "__label__gl":  "glg",
    "__label__gn":  "grn",  "__label__gu":  "guj",  "__label__gv":  "glv",
    "__label__he":  "heb",  "__label__hi":  "hin",  "__label__hr":  "hrv",
    "__label__hsb": "hsb",  "__label__ht":  "hat",  "__label__hu":  "hun",
    "__label__hy":  "hye",  "__label__ia":  "ina",  "__label__id":  "ind",
    "__label__ilo": "ilo",  "__label__io":  "ido",  "__label__is":  "isl",
    "__label__it":  "ita",  "__label__ja":  "jpn",  "__label__jbo": "jbo",
    "__label__jv":  "jav",  "__label__ka":  "kat",  "__label__kk":  "kaz",
    "__label__km":  "khm",  "__label__kn":  "kan",  "__label__ko":  "kor",
    "__label__krc": "krc",  "__label__ku":  "kur",  "__label__kv":  "kpv",
    "__label__kw":  "cor",  "__label__ky":  "kir",  "__label__la":  "lat",
    "__label__lb":  "ltz",  "__label__lez": "lez",  "__label__li":  "lim",
    "__label__lmo": "lmo",  "__label__ln":  "lin",  "__label__lo":  "lao",
    "__label__lrc": "lrc",  "__label__lt":  "lit",  "__label__lv":  "lav",
    "__label__mai": "mai",  "__label__mg":  "mlg",  "__label__mhr": "mhr",
    "__label__min": "min",  "__label__mk":  "mkd",  "__label__ml":  "mal",
    "__label__mn":  "mon",  "__label__mr":  "mar",  "__label__mrj": "mrj",
    "__label__ms":  "msa",  "__label__mt":  "mlt",  "__label__mwl": "mwl",
    "__label__my":  "mya",  "__label__myv": "myv",  "__label__mzn": "mzn",
    "__label__nah": "nah",  "__label__nap": "nap",  "__label__nds": "nds",
    "__label__ne":  "nep",  "__label__new": "new",  "__label__nl":  "nld",
    "__label__nn":  "nno",  "__label__no":  "nor",  "__label__oc":  "oci",
    "__label__or":  "ori",  "__label__os":  "oss",  "__label__pa":  "pan",
    "__label__pam": "pam",  "__label__pfl": "pfl",  "__label__pl":  "pol",
    "__label__pms": "pms",  "__label__pnb": "pnb",  "__label__ps":  "pus",
    "__label__pt":  "por",  "__label__qu":  "que",  "__label__rm":  "roh",
    "__label__ro":  "ron",  "__label__ru":  "rus",  "__label__rue": "rue",
    "__label__sa":  "san",  "__label__sah": "sah",  "__label__sc":  "srd",
    "__label__scn": "scn",  "__label__sco": "sco",  "__label__sd":  "snd",
    "__label__si":  "sin",  "__label__sk":  "slk",  "__label__sl":  "slv",
    "__label__so":  "som",  "__label__sq":  "sqi",  "__label__sr":  "srp",
    "__label__su":  "sun",  "__label__sv":  "swe",  "__label__sw":  "swa",
    "__label__ta":  "tam",  "__label__te":  "tel",  "__label__tg":  "tgk",
    "__label__th":  "tha",  "__label__tk":  "tuk",  "__label__tl":  "tgl",
    "__label__tr":  "tur",  "__label__tt":  "tat",  "__label__tyv": "tyv",
    "__label__ug":  "uig",  "__label__uk":  "ukr",  "__label__ur":  "urd",
    "__label__uz":  "uzb",  "__label__vec": "vec",  "__label__vep": "vep",
    "__label__vi":  "vie",  "__label__vls": "vls",  "__label__vo":  "vol",
    "__label__wa":  "wln",  "__label__war": "war",  "__label__wuu": "wuu",
    "__label__xal": "xal",  "__label__xmf": "xmf",  "__label__yi":  "ydd",
    "__label__yo":  "yor",  "__label__yue": "yue",  "__label__zh":  "zho",
    "__label__zu":  "zul",  "__label__nan": "nan",  "__label__sh":  "hbs",
    "__label__be-tarask": "bel",
}

EVAL_LENGTHS = [20, 50, 100, 200, 500, None]   # None = full / untruncated


def ft_label_to_iso3(label: str) -> str:
    if label in FT_TO_ISO3:
        return FT_TO_ISO3[label]
    return label.replace("__label__", "")


def compute_metrics(true_labels, pred_labels):
    """Return (macroF1, medianF1, pctAbove90, nLangs, accuracy, per_lang_f1s)."""
    langs = set(true_labels)
    tp = defaultdict(int)
    fp = defaultdict(int)
    fn = defaultdict(int)
    correct = 0
    for t, p in zip(true_labels, pred_labels):
        if t == p:
            tp[t] += 1
            correct += 1
        else:
            fn[t] += 1
            fp[p] += 1

    f1s = {}
    for lang in langs:
        prec = tp[lang] / (tp[lang] + fp[lang]) if (tp[lang] + fp[lang]) > 0 else 0.0
        rec  = tp[lang] / (tp[lang] + fn[lang]) if (tp[lang] + fn[lang]) > 0 else 0.0
        f1s[lang] = 2 * prec * rec / (prec + rec) if (prec + rec) > 0 else 0.0

    vals = sorted(f1s.values())
    macro   = sum(vals) / len(vals) if vals else 0.0
    median  = statistics.median(vals) if vals else 0.0
    above90 = sum(1 for v in vals if v >= 0.90)
    accuracy = correct / len(true_labels) if true_labels else 0.0
    return macro, median, above90, len(langs), accuracy, f1s


def _run_length_table(model, sentences, section):
    """Evaluate model at each EVAL_LENGTHS and append table rows to section."""
    total = len(sentences)
    section.append(f"{'':6s}  {'── fastText ──':14s}  {'':8s}  {'':12s}  {'':10s}")
    section.append(f"{'Length':6s}  {'mF1':>6s} {'acc':>6s}  {'ms':>8s}  {'>=0.90/total':>12s}  {'sent/sec':>10s}")
    section.append("-" * 64)

    per_lang_f1s = {}
    for maxchars in EVAL_LENGTHS:
        tag = f"@{maxchars}" if maxchars is not None else "full"
        t0 = time.perf_counter()
        true_labels, pred_labels = [], []
        for true_lang, text in sentences:
            snippet = text[:maxchars] if maxchars is not None else text
            ft_labels, _ = model.predict(snippet.replace("\n", " "), k=1)
            true_labels.append(true_lang)
            pred_labels.append(ft_label_to_iso3(ft_labels[0]))
        elapsed_ms = int((time.perf_counter() - t0) * 1000)
        sps = total / (elapsed_ms / 1000) if elapsed_ms > 0 else 0

        macro, median, above90, nlang, acc, f1s = compute_metrics(
            true_labels, pred_labels)
        per_lang_f1s[tag] = f1s
        section.append(
            f"{tag:<6s}  {macro:6.4f} {acc:6.4f}  {elapsed_ms:>8,}  "
            f"{above90:>5d}/{nlang:<6d}  {sps:>10,.0f}")

    return per_lang_f1s


def evaluate_file(model, path, report_lines=None):
    label = os.path.basename(path)
    section = []
    section.append("")
    section.append("=" * 60)
    section.append(f"fastText evaluation: {label}")
    section.append("=" * 60)

    # Supported ISO 639-3 codes (values of FT_TO_ISO3, deduped)
    supported = set(FT_TO_ISO3.values())

    # Secondary-script Flores variants that are kept as xxx_Yyyy (not normalized to bare code),
    # so they appear as distinct evaluation classes. Mirrors CompareDetectors.FLORES_KEEP_SCRIPT_SUFFIX.
    flores_keep_suffix = {
        "ace_Arab",  # Acehnese in Jawi; MADLAD ace is Latin-script
        "arb_Latn",  # Romanized Arabic; distinct from Arabic-script arb
        "bjn_Arab",  # Banjar in Jawi; digital Banjar is primarily Latin-script
        "kas_Deva",  # Kashmiri in Devanagari; primary form is Nastaliq
        "knc_Latn",  # Kanuri in Latin; traditional script is Arabic
        "min_Arab",  # Minangkabau in Jawi; MADLAD min is Latin-script
        "taq_Tfng",  # Tamasheq in Tifinagh; digital text predominantly Latin
    }

    all_sentences = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            tab = line.find("\t")
            if tab < 0:
                continue
            raw_lang = line[:tab]
            if raw_lang in flores_keep_suffix:
                # Keep full xxx_Yyyy as a distinct class — don't normalize
                lang = raw_lang
            else:
                # Strip script suffix: zho_Hans → zho, eng_Latn → eng
                underscore = raw_lang.find("_")
                lang = raw_lang[:underscore] if underscore >= 0 else raw_lang
            all_sentences.append((lang, line[tab + 1:]))

    supported_sentences = [(l, t) for l, t in all_sentences if l in supported]
    supported_langs = len({l for l, _ in supported_sentences})

    section.append(f"Sentences (all):       {len(all_sentences):,}")
    section.append(f"Sentences (supported): {len(supported_sentences):,}  "
                   f"({supported_langs} languages supported by fastText)")
    section.append(f"  (fastText lid.176.bin, 1 thread via Python/ctypes — "
                   f"Java detectors use 12 threads; divide CharSoup sent/sec by ~12 for fair comparison)")

    # ── Strict table: all languages, unsupported score 0 ──
    section.append(f"\nStrict — all languages (unsupported score 0):")
    per_lang_f1s_all = _run_length_table(model, all_sentences, section)

    # ── Supported-only table ──
    section.append(f"\nfastText-supported languages only "
                   f"({supported_langs} langs, {len(supported_sentences):,} sentences):")
    per_lang_f1s_sup = _run_length_table(model, supported_sentences, section)
    section.append("")

    # Worst-10 at full (supported only)
    f1s_full = per_lang_f1s_sup["full"]
    section.append("Worst 10 languages — supported only (full text):")
    for lang, f1 in sorted(f1s_full.items(), key=lambda x: x[1])[:10]:
        section.append(f"  {lang:<8s}  F1={f1:.4f}")
    section.append("")

    text_out = "\n".join(section)
    print(text_out)
    if report_lines is not None:
        report_lines.append(text_out)

    # TSV: per-language F1 at every length (supported only)
    tsv_path = path.replace(".txt", "") + "-fasttext-per-lang.tsv"
    all_langs = sorted(f1s_full.keys())
    length_tags = [f"@{l}" if l is not None else "full" for l in EVAL_LENGTHS]
    with open(tsv_path, "w", encoding="utf-8") as t:
        t.write("lang\t" + "\t".join(f"f1_{tag}" for tag in length_tags) + "\n")
        for lang in all_langs:
            vals = "\t".join(
                f"{per_lang_f1s_sup[tag].get(lang, 0.0):.4f}" for tag in length_tags)
            t.write(f"{lang}\t{vals}\n")
    print(f"Per-language TSV: {tsv_path}")

    return per_lang_f1s_sup


def main():
    model_path = os.path.expanduser(
        "~/datasets/madlad/lang-detect/lid.176.bin")
    if not os.path.exists(model_path):
        print(f"ERROR: fastText model not found: {model_path}")
        sys.exit(1)

    # Optional: append fastText section to a pre-existing report file
    report_file = None
    positional = []
    for arg in sys.argv[1:]:
        if arg.startswith("--report="):
            report_file = arg[len("--report="):]
        else:
            positional.append(arg)

    print(f"Loading fastText model: {model_path}")
    model = fasttext.load_model(model_path)
    print("Loaded.\n")

    if positional:
        files = positional
    else:
        base = os.path.expanduser("~/datasets/flores-200")
        files = [os.path.join(base, "flores200_devtest.tsv")]

    report_lines = [] if report_file else None

    for path in files:
        if os.path.exists(path):
            evaluate_file(model, path, report_lines)
        else:
            print(f"[skipping — not found: {path}]")

    if report_file and report_lines:
        with open(report_file, "a", encoding="utf-8") as f:
            f.write("\n\n" + "\n".join(report_lines) + "\n")
        print(f"\nfastText section appended to: {report_file}")


if __name__ == "__main__":
    main()
