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

"""Post-processing filter: remove title-case anomalies per language.

For each language, compute the distribution of "uppercase word ratio"
(fraction of non-first alphabetic words starting with uppercase).
Sentences above mean + N*sigma are likely title-case bibliography entries
or other non-prose noise. The threshold adapts per language, so German's
naturally high noun-capitalization baseline doesn't cause false positives.

Usage:
    python3 filter_uppercase.py ~/datasets/wikipedia-dumps
    python3 filter_uppercase.py ~/datasets/wikipedia-dumps --sigma 2.5 --min-sentences 1000
"""

import argparse
import logging
import re
import statistics
from pathlib import Path

log = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s",
                    datefmt="%H:%M:%S")


def uppercase_ratio(sentence: str) -> float:
    """Fraction of non-first alphabetic words starting with uppercase."""
    words = sentence.split()
    if len(words) <= 1:
        return 0.0
    non_first = [w for w in words[1:] if w[0].isalpha()]
    if not non_first:
        return 0.0
    return sum(1 for w in non_first if w[0].isupper()) / len(non_first)


def read_sentences(path: Path) -> list[tuple[str, str]]:
    """Return list of (line_prefix, sentence) from a sentences.txt file."""
    result = []
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            tab = line.find("\t")
            if tab < 0:
                continue
            prefix = line[:tab]
            if not prefix.strip().isdigit():
                continue
            result.append((prefix, line[tab + 1:].rstrip("\n")))
    return result


def filter_lang(lang_dir: Path, sigma: float, min_sentences: int, dry_run: bool) -> dict:
    sentences_file = lang_dir / "sentences.txt"
    if not sentences_file.exists():
        return {}

    pairs = read_sentences(sentences_file)
    if len(pairs) < min_sentences:
        log.info(f"{lang_dir.name}: only {len(pairs)} sentences, skipping uppercase filter")
        return {"lang": lang_dir.name, "total": len(pairs), "removed": 0, "skipped": True}

    ratios = [uppercase_ratio(s) for _, s in pairs]
    mean = statistics.mean(ratios)
    stdev = statistics.stdev(ratios) if len(ratios) > 1 else 0.0
    threshold = mean + sigma * stdev
    # Cap threshold at 1.0, floor at 0.5 so we never filter normal prose
    threshold = max(0.5, min(1.0, threshold))

    keep = [(p, s) for (p, s), r in zip(pairs, ratios) if r <= threshold]
    removed = len(pairs) - len(keep)

    log.info(
        f"{lang_dir.name}: mean={mean:.3f} stdev={stdev:.3f} "
        f"threshold={threshold:.3f}  removed {removed}/{len(pairs)}"
    )

    if removed > 0 and not dry_run:
        with open(sentences_file, "w", encoding="utf-8") as f:
            for i, (_, s) in enumerate(keep, 1):
                f.write(f"{i}\t{s}\n")

    return {
        "lang": lang_dir.name,
        "total": len(pairs),
        "removed": removed,
        "mean": mean,
        "stdev": stdev,
        "threshold": threshold,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dump_dir", type=Path)
    parser.add_argument("--sigma", type=float, default=2.0,
                        help="Stdevs above mean to set threshold (default: 2.0)")
    parser.add_argument("--min-sentences", type=int, default=500,
                        help="Skip filter for languages with fewer sentences (default: 500)")
    parser.add_argument("--dry-run", action="store_true",
                        help="Report what would be removed without modifying files")
    args = parser.parse_args()

    lang_dirs = sorted(d for d in args.dump_dir.iterdir() if d.is_dir())
    results = []
    for lang_dir in lang_dirs:
        r = filter_lang(lang_dir, args.sigma, args.min_sentences, args.dry_run)
        if r:
            results.append(r)

    total_removed = sum(r.get("removed", 0) for r in results)
    total_sentences = sum(r.get("total", 0) for r in results)
    langs_touched = sum(1 for r in results if r.get("removed", 0) > 0)

    print(f"\n{'DRY RUN — ' if args.dry_run else ''}Summary:")
    print(f"  Languages processed : {len(results)}")
    print(f"  Languages with removals: {langs_touched}")
    print(f"  Total removed       : {total_removed:,} / {total_sentences:,} "
          f"({100 * total_removed / max(total_sentences, 1):.2f}%)")

    if args.dry_run and total_removed > 0:
        print("\nTop languages by removal count:")
        top = sorted(results, key=lambda r: r.get("removed", 0), reverse=True)[:20]
        for r in top:
            if r.get("removed", 0) == 0:
                break
            print(f"  {r['lang']:12s}  removed={r['removed']:6,}  "
                  f"mean={r.get('mean', 0):.3f}  threshold={r.get('threshold', 0):.3f}")


if __name__ == "__main__":
    main()
