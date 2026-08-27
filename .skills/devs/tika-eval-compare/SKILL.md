---
name: tika-eval-compare
description: >
  Compare extracts from two Tika builds over a corpus to detect regressions
  in content, encoding, exceptions, and embedded-document handling. Use for
  "compare before/after extracts", "eval this change against the corpus".
---

<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

Local override: `$TIKA_SKILLS_LOCAL/tika-eval-compare/LOCAL.md` (default `~/.tika-skills`),
read after this file, wins on conflict.

# tika-eval: Compare Before/After Extracts

Compare the output of two versions of Tika against a corpus of files
to detect regressions in content extraction, encoding, exceptions, and
embedded document handling.

## Before You Start

Ask the user for:

1. **Working directory** — where to put builds, extracts, eval db, and
   reports (`<workdir>` below).  All artifacts go here.
2. **Number of threads** (`-n`) — default is 2.  Use `-n 6` for faster
   runs when parse time comparison is not needed.  When comparing parse
   times between A and B, use the same `-n` for both.
3. **Run reports?** — whether to auto-generate the HTML/Excel reports
   and `summary.md` at the end (the `-r` flag on tika-eval Compare).

## Prerequisites

- Two tika-app builds (a "before" and an "after"), each as an unzipped
  zip archive containing `tika-app-*.jar`, `lib/`, and `plugins/`.
- A corpus of input files (a directory tree).
- tika-eval-app, built from `tika-eval/tika-eval-app` (use the zip).
  The bare `target/tika-eval-app-*.jar` is thin (no bundled deps) and dies with
  `NoClassDefFoundError: ...GzipCompressorOutputStream` (esp. with `-r`). Always
  run the jar from the unzipped `target/*.zip` dir, which carries its `lib/`.
- **Enable MD5 digesting** in both configs so tika-eval can match
  embedded documents by content hash (not just index position).
  Add to the config JSON:
  ```json
  "parse-context": {
    "commons-digester-factory": {
      "digests": [
        { "algorithm": "MD5" }
      ]
    }
  }
  ```
  Note: `parse-context` is a JSON **object**, not an array.

## Step 1 — Generate Extracts

Run each tika-app version against the same input corpus.  The batch
mode is triggered automatically when the first positional argument is
a directory:

```bash
java -jar <before>/tika-app-*.jar <input-dir> <extracts-a-dir>
java -jar <after>/tika-app-*.jar  <input-dir> <extracts-b-dir>
```

To use a custom config (e.g., SAX vs DOM parsers, deleted content,
macros), pass `--config=<file.json>` **before** the input/output dirs:

```bash
java -jar <tika-app>/tika-app-*.jar --config=dom-config.json <input-dir> <extracts-a-dir>
java -jar <tika-app>/tika-app-*.jar --config=sax-config.json <input-dir> <extracts-b-dir>
```

Each run walks the input directory recursively and writes one
`.json` file per input file (recursive metadata + XHTML content,
equivalent to `tika-app -J`).  The directory structure mirrors
the input.

### Provenance + crash ledger (preferred)

`run-batch.sh` (next to this file) wraps the same invocation and records what
ran, so an extract set can be tied to a build after the fact and a crashed
file is distinguishable from one that parsed to nothing:

```bash
.skills/devs/tika-eval-compare/run-batch.sh --app <before> --input <input-dir> --extracts <extracts-a-dir> --note baseline
.skills/devs/tika-eval-compare/run-batch.sh --app <after>  --input <input-dir> --extracts <extracts-b-dir> --note candidate [--config cfg.json]
```

It writes `run-info-<run.id>.json` and (4.1+ only) `crashes-<run.id>.jsonl`
into `<extracts>/.run-info/`; Compare picks them up from there by default
(`-ra/-rb`, `-pa/-pb` override). A 3.x baseline gets run-info but no ledger.

### Notes

- Do NOT pass `-n <N>` as a trailing argument — it confuses the
  async mode auto-detection.  If you need to control parallelism,
  use `-i <input-dir> -o <output-dir> -n <N>` with explicit flags.
- Default parallelism is 2 forked JVM clients.  Use `-n 6` for faster
  runs when parse time comparison is not needed.  When comparing parse
  times, keep `-n` the same for both A and B.
- Default timeout is 30 000 ms per file.

## Step 2 — Run tika-eval Compare

Unzip the tika-eval-app zip, then run:

```bash
java -jar <tika-eval>/tika-eval-app-*.jar Compare \
  -a <extracts-a-dir> \
  -b <extracts-b-dir> \
  -d <db-path> \
  -r \
  -rd <reports-dir>
```

| Flag | Description |
|------|-------------|
| `-a` | Directory of "before" extracts (required) |
| `-b` | Directory of "after" extracts (required) |
| `-d` | H2 database path (temp file if omitted) |
| `-r` | Auto-run Report + tgz the reports dir (`<reportsDir>.tgz`) after Compare |
| `-rd` | Reports output directory (default: `reports`) |
| `-z` | Gzip the H2 db (`<db>.mv.db.gz`) after Compare for transfer; requires `-d` (no-op + warning for a temp db). Combine with `-r` to package both. |
| `-n` | Number of worker threads |
| `-pa`/`-pb` | jsonl ledger for A/B (default: `<extracts>/.run-info/crashes-*.jsonl`); fills `containers.pipes_status_a/b` |
| `-ra`/`-rb` | run-info json for A/B (default: `<extracts>/.run-info/run-info-*.json`); lands in `run_info_a/b`. Refused if its `run.id` is not in the matching `-pa`/`-pb` file name |

With `-pa`/`-pb`, `summary.md` and `exceptions/extract_exceptions_by_pipes_status_*.xlsx`
split `NO_EXTRACT_FILE` into `CRASH` (OOM/TIMEOUT), `LOST_TO_WORKER_RESTART`
(shared-server collateral; retry once, alone), `NO_PIPES_RECORD`, and
`NO_PIPES_REPORT_SUPPLIED`. A crash status *with* an extract present is a
success whose status was lost — listed separately, not a failure.

## Step 3 — Review Results

Reports are written as Excel `.xlsx` files under the reports
directory, plus a `summary.md` with key metrics:

- **Content Quality (Dice Coefficient)** — similarity between A and B
  per mime type.  Mean dice < 0.95 warrants investigation.
- **OOV / Languageness Changes** — increased out-of-vocabulary rate
  or decreased languageness z-score may indicate encoding regressions.
- **Content Length Ratio Outliers** — files where B is >2× or <0.5×
  the length of A.
- **Exception Changes** — new exceptions in B or fixed exceptions.
- **Embedded Document Count Changes** — gained/lost attachments.
- **Content Regressions** — lowest-dice files with token-level diffs.
- **Content Lost / Gained** — files that went empty↔non-empty.

### Interpreting Results

| Metric | Good | Investigate |
|--------|------|-------------|
| Mean dice (same mime) | ≥ 0.95 | < 0.90 |
| New exceptions in B | 0 | > 0 — every one needs explanation |
| Embedded doc count losses | 0 | > 0 — investigate by mime type |
| OOV delta | < 0.05 | > 0.10 |
| Content length ratio | 0.5–2.0 | > 5× or < 0.2× |
| Exception count | ≤ A | > A |
| Total files (B) vs (A) | equal or higher | lower — missing embedded docs |

### Encoding-detection evals

For charset/encoding-detector changes, the summary reports don't cover it — query
the db directly (see the **tika-eval-h2-query** skill). The detected encoding is in
the `ENCODINGS_A`/`ENCODINGS_B` tables (`DETECTED_ENCODING`, `ENCODING_DETECTOR`,
`DECLARED_METADATA`), **not** `PROFILES`. Key signals: per-encoding counts (e.g. CJK
total), A→B flips by direction, and OOV on the flipped files (a flip that *worsens*
OOV is a regression; one that *improves* it is a fix). Pair on `ID`; map back to the
source file via `PROFILES_*.FILE_NAME` (the content hash).

### CRITICAL: Review Checklist

The purpose of tika-eval is to find regressions BEFORE a release. After
reading summary.md, report each of these to the user:

1. **New exceptions**: Exact count. If > 0, investigate stack traces.
   Every new exception is a bug.
2. **Total files delta**: "Total files (A)" vs "(B)". If B < A, embedded
   documents are being lost — aggregate the losses by child mime type.
3. **Embedded doc count changes**: Report losers from the summary table.
4. **Dice scores**: Flag any mean < 0.99 for OOXML or < 0.95 for others.
5. **Content length outliers**: Flag ratio > 3x or < 0.3x.
6. **Fixed exceptions**: Report the count.

**Do not summarize results as "looks good" based on dice scores alone.**
Dice measures text similarity, not attachment completeness. Always check
the Total files delta.

**After fixing regressions, re-run the full eval.** A fix for one format
may not cover another. Verify the numbers moved and no new issues appeared.

## Building from Source

```bash
# tika-app
./mvnw clean install -pl tika-app -am -Pfast \
  -Dmaven.repo.local=$(pwd)/.local_m2_repo

# tika-eval-app
./mvnw clean install -pl tika-eval/tika-eval-app -am -Pfast \
  -Dmaven.repo.local=$(pwd)/.local_m2_repo
```

The zip artifacts are in `<module>/target/<module>-*.zip`.

## Example: Comparing MSG parsing changes

```bash
# Download the "before" snapshot
curl -o /tmp/tika-app-before.zip <snapshot-url>
unzip -qo /tmp/tika-app-before.zip -d /tmp/tika-app-before

# Build the "after" from local changes
./mvnw clean install -pl tika-app -am -Pfast \
  -Dmaven.repo.local=$(pwd)/.local_m2_repo
unzip -qo tika-app/target/tika-app-*.zip -d /tmp/tika-app-after

# Generate extracts
java -jar /tmp/tika-app-before/tika-app-*.jar <corpus> /tmp/extracts-a
java -jar /tmp/tika-app-after/tika-app-*.jar  <corpus> /tmp/extracts-b

# Build and run tika-eval
./mvnw clean install -pl tika-eval/tika-eval-app -am -Pfast \
  -Dmaven.repo.local=$(pwd)/.local_m2_repo
unzip -qo tika-eval/tika-eval-app/target/tika-eval-app-*.zip -d /tmp/tika-eval

java -jar /tmp/tika-eval/tika-eval-app-*.jar Compare \
  -a /tmp/extracts-a -b /tmp/extracts-b \
  -d /tmp/eval-db -r -rd /tmp/eval-reports

# Review
cat /tmp/eval-reports/summary.md
```
