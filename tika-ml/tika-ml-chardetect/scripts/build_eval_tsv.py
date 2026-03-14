#!/usr/bin/env python3
"""
Convert flores-200 dev/ and devtest/ into tab-separated lang\ttext files
compatible with CompareDetectors.

Language codes are kept as the full Flores xxx_Yyyy form (e.g. ace_Arab,
ace_Latn, zho_Hans, zho_Hant). Multi-script languages are NOT merged.
CompareDetectors normalises xxx_Yyyy → xxx at eval time.

Outputs:
  flores200_dev.tsv       – dev split only      ( 997 sentences / lang)
                            Use for iterative development checks.
  flores200_devtest.tsv   – devtest split only  (1012 sentences / lang)
                            Use ONLY for final sign-off on a model candidate.

The two splits are genuinely disjoint (separate source directories).
Do not combine them; doing so would contaminate the final held-out test.
"""

import pathlib

ROOT = pathlib.Path(__file__).parent / "flores200_dataset"


def build_tsv(split_dir: pathlib.Path, out_path: pathlib.Path) -> None:
    rows = []
    skipped = 0
    for fpath in sorted(split_dir.iterdir()):
        # filename is  xxx_Yyyy.devtest  or  xxx_Yyyy.dev
        lang = fpath.stem          # keep full code, e.g. ace_Arab
        with fpath.open(encoding="utf-8") as f:
            for raw in f:
                text = raw.strip()
                if len(text) < 10:
                    skipped += 1
                    continue
                rows.append(f"{lang}\t{text}")
    with out_path.open("w", encoding="utf-8") as f:
        f.write("\n".join(rows))
        f.write("\n")
    lang_count = len({r.split("\t", 1)[0] for r in rows})
    print(f"  {out_path.name}: {len(rows):,} sentences, {lang_count} lang-script combos"
          + (f", {skipped} short lines skipped" if skipped else ""))


print("Building flores-200 eval TSVs ...")
build_tsv(ROOT / "dev",     ROOT.parent / "flores200_dev.tsv")
build_tsv(ROOT / "devtest", ROOT.parent / "flores200_devtest.tsv")
print("Done.")
