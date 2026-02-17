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
Download Leipzig Corpora Collection and write to Leipzig format
(lineNum\\tsentence) organized by ISO 639-3 language code.

This script downloads corpora directly from wortschatz-leipzig.de,
using the corpus index maintained at:
https://gist.github.com/imvladikon/70c35d6b1fb83635a024751667be0112

For each language, it selects the single largest available corpus
(preferring 100K–300K sized corpora for balance) and downloads it.
Multiple corpora per language are merged if --merge-all is set.

Usage:
    pip install requests
    python download_corpus.py <output_dir> [--max-per-lang N] [--merge-all]

Example:
    python download_corpus.py ~/datasets/lang-detect --max-per-lang 100000

Output structure:
    output_dir/
        eng/
            sentences.txt
        deu/
            sentences.txt
        ...

Each sentences.txt file is in Leipzig format: lineNum<TAB>sentence
"""

import argparse
import io
import json
import os
import sys
import tarfile
from collections import defaultdict
from pathlib import Path
from urllib.parse import urlparse

import requests

# Leipzig corpus index — maps data_id to URL and metadata
LEIPZIG_INDEX_URL = (
    "https://gist.githubusercontent.com/imvladikon/"
    "70c35d6b1fb83635a024751667be0112/raw/"
    "b91875489ccf7eb1ca8270e1138faf1db43952ec/leipzig_urls.json"
)

# Preferred corpus sizes, in priority order (largest practical first)
SIZE_PRIORITY = {"300K": 0, "100K": 1, "1M": 2, "30K": 3, "10K": 4}


def parse_size(size_str: str) -> int:
    """Convert size string like '100K', '1M' to an integer for sorting."""
    s = size_str.strip().upper()
    if s.endswith("M"):
        return int(s[:-1]) * 1_000_000
    elif s.endswith("K"):
        return int(s[:-1]) * 1_000
    return int(s)


def select_best_corpus(corpora: list[dict]) -> dict:
    """Select the best single corpus for a language.

    Prefers 100K size (large enough for good coverage, small enough to download quickly).
    Falls back to the largest available.
    """
    # Sort by size priority, then by year descending (newer is better)
    def sort_key(c):
        size = c.get("size", "10K")
        priority = SIZE_PRIORITY.get(size, 99)
        year = c.get("year", "0000")
        return (priority, -int(year) if year.isdigit() else 0)

    corpora.sort(key=sort_key)
    return corpora[0]


def download_and_extract_sentences(url: str, max_sentences: int) -> list[str]:
    """Download a Leipzig tar.gz and extract sentences from *-sentences.txt."""
    print(f"    Downloading {url} ...", end=" ", flush=True)
    resp = requests.get(url, timeout=(15, 120))  # (connect, read) timeouts
    resp.raise_for_status()
    print(f"({len(resp.content) / 1024 / 1024:.1f} MB)")

    sentences = []
    with tarfile.open(fileobj=io.BytesIO(resp.content), mode="r:gz") as tar:
        for member in tar.getmembers():
            if member.name.endswith("-sentences.txt"):
                f = tar.extractfile(member)
                if f is None:
                    continue
                for line in io.TextIOWrapper(f, encoding="utf-8"):
                    line = line.strip()
                    if not line:
                        continue
                    parts = line.split("\t", 1)
                    if len(parts) == 2:
                        sentences.append(parts[1])
                    if len(sentences) >= max_sentences:
                        break
                break  # Only one sentences file per archive

    return sentences


def parse_source_type(data_id: str) -> str:
    """Extract source type from a Leipzig data_id like 'eng_news_2023_1M'.

    Typical source types: news, web, wikipedia, newscrawl, community, mixed.
    """
    parts = data_id.split("_")
    if len(parts) >= 2:
        return parts[1].lower()
    return "unknown"


def interleave_by_source(corpora: list[dict]) -> list[dict]:
    """Reorder corpora to round-robin across source types for diversity.

    Within each source type, corpora are ordered by size descending then year
    descending so the best corpus of each type is picked first.
    """
    by_source: dict[str, list[dict]] = defaultdict(list)
    for c in corpora:
        src = parse_source_type(c.get("data_id", ""))
        by_source[src].append(c)

    # Within each source, sort by size desc then year desc
    for src in by_source:
        by_source[src].sort(
            key=lambda c: (-parse_size(c.get("size", "0")),
                           -int(c.get("year", "0")) if c.get("year", "0").isdigit() else 0)
        )

    # Round-robin across sources
    result = []
    iterators = {src: iter(lst) for src, lst in by_source.items()}
    while iterators:
        exhausted = []
        for src in sorted(iterators.keys()):
            try:
                result.append(next(iterators[src]))
            except StopIteration:
                exhausted.append(src)
        for src in exhausted:
            del iterators[src]

    return result


def download_and_write(output_dir: Path, max_per_lang: int, merge_all: bool):
    """Download corpora and write to Leipzig format."""
    print("Fetching corpus index...")
    resp = requests.get(LEIPZIG_INDEX_URL, timeout=30)
    resp.raise_for_status()
    index = resp.json()

    # Group corpora by language_short (ISO 639-3)
    by_lang: dict[str, list[dict]] = defaultdict(list)
    for data_id, record in index.items():
        lang = record.get("language_short", "").strip()
        if not lang:
            continue
        record["data_id"] = data_id
        by_lang[lang].append(record)

    print(f"Index has {len(index)} corpora across {len(by_lang)} languages")
    print(f"Output directory: {output_dir}")
    print(f"Max sentences per language: {max_per_lang:,}")
    print(f"Merge all corpora per language: {merge_all}")
    print()

    output_dir.mkdir(parents=True, exist_ok=True)
    lang_counts = {}
    source_stats: dict[str, dict[str, int]] = {}  # lang -> {source: count}
    failed = []

    for lang in sorted(by_lang.keys()):
        corpora = by_lang[lang]

        # Skip languages already downloaded at or near the cap.
        # If sentences.txt exists but is much smaller than max_per_lang AND
        # more corpora are available, re-download to fill in gaps from
        # network failures.
        lang_dir = output_dir / lang
        sentences_file = lang_dir / "sentences.txt"
        if sentences_file.is_file() and sentences_file.stat().st_size > 0:
            existing_lines = sum(1 for _ in open(sentences_file, encoding="utf-8"))
            # Consider "complete" if we hit 90% of cap OR the language only
            # has a small number of corpora (likely got everything available)
            total_available = sum(
                parse_size(c.get("size", "0")) for c in corpora
            )
            if existing_lines >= max_per_lang * 0.9 or existing_lines >= total_available * 0.9:
                print(f"  [{lang}] already downloaded ({existing_lines:,} sentences) — skipping")
                continue
            else:
                print(f"  [{lang}] incomplete ({existing_lines:,} sentences, "
                      f"~{total_available:,} available) — re-downloading")
                # Fall through to re-download

        print(f"  [{lang}] {len(corpora)} corpora available")

        all_sentences = []
        lang_sources: dict[str, int] = defaultdict(int)

        if merge_all:
            # Round-robin across source types for diversity
            ordered = interleave_by_source(corpora)
            for corpus in ordered:
                if len(all_sentences) >= max_per_lang:
                    break
                src = parse_source_type(corpus.get("data_id", ""))
                try:
                    remaining = max_per_lang - len(all_sentences)
                    sents = download_and_extract_sentences(
                        corpus["url"], remaining
                    )
                    before = len(all_sentences)
                    all_sentences.extend(sents)
                    lang_sources[src] += len(all_sentences) - before
                except Exception as e:
                    print(f"    WARN: Failed {corpus['data_id']}: {e}")
                    continue
        else:
            # Download single best corpus
            best = select_best_corpus(corpora)
            print(f"    Selected: {best['data_id']} ({best.get('size', '?')})")
            try:
                all_sentences = download_and_extract_sentences(
                    best["url"], max_per_lang
                )
                lang_sources[parse_source_type(best.get("data_id", ""))] = len(all_sentences)
            except Exception as e:
                print(f"    ERROR: Failed to download {best['data_id']}: {e}")
                failed.append(lang)
                continue

        if not all_sentences:
            print(f"    WARN: No sentences extracted for {lang}")
            failed.append(lang)
            continue

        # Truncate to max
        all_sentences = all_sentences[:max_per_lang]

        # Write Leipzig format
        lang_dir = output_dir / lang
        lang_dir.mkdir(exist_ok=True)
        out_file = lang_dir / "sentences.txt"
        with open(out_file, "w", encoding="utf-8") as f:
            for i, sent in enumerate(all_sentences, 1):
                f.write(f"{i}\t{sent}\n")

        lang_counts[lang] = len(all_sentences)
        source_stats[lang] = dict(lang_sources)
        src_summary = ", ".join(f"{s}:{c:,}" for s, c in sorted(lang_sources.items()))
        print(f"    Wrote {len(all_sentences):,} sentences ({src_summary})")

    # Summary
    print(f"\n{'=' * 60}")
    print(f"Download complete!")
    print(f"Languages: {len(lang_counts)}")
    print(f"Total sentences: {sum(lang_counts.values()):,}")
    if failed:
        print(f"Failed: {', '.join(failed)}")
    print()

    for lang, count in sorted(lang_counts.items(), key=lambda x: -x[1]):
        sources = source_stats.get(lang, {})
        src_summary = ", ".join(f"{s}:{c:,}" for s, c in sorted(sources.items()))
        print(f"  {lang:8s} {count:>10,}  ({src_summary})")


def main():
    parser = argparse.ArgumentParser(
        description="Download Leipzig Corpora Collection for language detection training"
    )
    parser.add_argument(
        "output_dir",
        type=Path,
        help="Directory to write per-language sentence files",
    )
    parser.add_argument(
        "--max-per-lang",
        type=int,
        default=100_000,
        help="Maximum sentences per language (default: 100000)",
    )
    parser.add_argument(
        "--merge-all",
        action="store_true",
        help="Download and merge ALL corpora per language (slow, more data)",
    )
    args = parser.parse_args()

    download_and_write(args.output_dir, args.max_per_lang, args.merge_all)


if __name__ == "__main__":
    main()
