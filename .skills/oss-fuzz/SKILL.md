---
name: oss-fuzz
description: >
  Run Tika's OSS-Fuzz Jazzer targets locally against a working-tree checkout —
  build the image, build fuzzers from local source, fuzz a target, run a corpus
  as a regression pass, reproduce a crash, and add seeds. Use for "fuzz the
  OneNote parser", "run OneNoteParserFuzzer against these files", "reproduce an
  OSS-Fuzz crash", "fuzz my branch before merge".
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

# Tika OSS-Fuzz — local fuzzing

Tika is already in OSS-Fuzz as the **`apache-tika`** project (not `tika`).
It is Jazzer-based (coverage-guided, in-process JVM fuzzing), not the old
`tika-fuzzing` seed-mutation module (removed in TIKA-4506). The fuzz targets
and seed logic live in the **oss-fuzz** repo under
`projects/apache-tika/`, *not* in this repo:

- `project-parent/fuzz-targets/src/main/java/com/example/*Fuzzer.java` — one
  Jazzer target per parser family. Each calls `ParserFuzzer.parseOne(...)`
  (parse-from-bytes and parse-from-file) and swallows
  `TikaException | SAXException | IOException`; anything else — `Error`
  (OOM, StackOverflow), a hang, or an unexpected `RuntimeException` — is a
  finding.
- `build.sh` — builds `tika-app`, then the `fuzz-targets` module.
- `build_seeds.sh` — packs Tika's own unit-test files into
  `<Target>_seed_corpus.zip` by file extension.

Targets (as of this writing): `AudioVideoParsersFuzzer`,
`AutoDetectParserFuzzer`, `CompressorParserFuzzer`, `HtmlParserFuzzer`,
`ImageParsersFuzzer`, `JackcessParserFuzzer`, `OOXMLParserFuzzer`,
`OfficeParserFuzzer`, `OneNoteParserFuzzer`, `PDFParserFuzzer`,
`PackageParserFuzzer`, `RFC822ParserFuzzer`, `RTFParserFuzzer`,
`TextAndCSVParserFuzzer`, `XMLReaderUtilsFuzzer`. `ParserFuzzer` is the shared
helper, not a target (`build.sh` skips it).

Primary contact on the project is `tallison@apache.org`, so OSS-Fuzz crash
mail / ClusterFuzz notifications land in that inbox — check there for what
continuous fuzzing has already found before treating a bug as newly discovered.

## Prerequisites

- Docker daemon running (`docker ps`).
- A local oss-fuzz checkout: `git clone --depth 1 https://github.com/google/oss-fuzz`.
  All `helper.py` commands run from the oss-fuzz root. `$OSSFUZZ` below = that dir.
- `python3` (helper.py is Python).

## 1. Build the image (non-interactive)

`build_image` **prompts** to pull base images; a plain background/non-tty run
dies with `EOFError: EOF when reading a line`. Always pass `--no-pull` (or
`--pull` to force a refresh) and redirect stdin:

```bash
cd $OSSFUZZ
python3 infra/helper.py build_image --no-pull apache-tika < /dev/null
```

Pulls the `base-builder-jvm` image on first run (multi-GB); slow once, cached after.

## 2. Build fuzzers from a LOCAL checkout (the point of local dev)

Give `build_fuzzers` a source path and it mounts your working tree over the
Dockerfile's `git clone` of Tika — so it fuzzes uncommitted code (a branch
under review, a candidate cap-fix), not upstream `main`.

**Gotcha — the mount path is nested.** The Dockerfile clones Tika into
`$SRC/project-parent/tika`, but `helper.py` defaults a local mount to
`/src/tika` (basename of `main_repo`). The default lands in the wrong place and
the build silently uses the baked-in clone instead of your tree. Pin it:

```bash
python3 infra/helper.py build_fuzzers \
  --mount_path /src/project-parent/tika \
  apache-tika /home/<user>/path/to/tika
```

**Verify the mount actually took** before trusting any result — a wrong
`--mount_path` fails open (silently builds the baked-in upstream clone and
reports clean). The reliable check is a negative control: inject a guaranteed
compile error into a source file on the build path (e.g. a bare
`THIS_MUST_NOT_COMPILE` token in `OneNoteParser.java`), rebuild, and confirm the
build **fails at your file and line**. If it still succeeds, the mount is being
ignored. Then revert and rebuild clean. Note the failure surfaces as a
**spotless lint error** naming your file:line (`removeUnusedImports ... error:
<identifier> expected`), not a raw javac message — spotless runs first. Watch
the real exit code, not a wrapper's: `helper.py` prints
`ERROR:__main__:Building fuzzers failed.` and exits non-zero on failure.

Sanity-check the target exists after build:
`ls build/out/apache-tika/OneNoteParserFuzzer`.

## 3a. Regression pass over YOUR OWN corpus (run each file once)

The common ask — "run `OneNoteParserFuzzer` against these files" — is a
read-only regression pass: execute each of your inputs once, report crashes,
change nothing.

**Do NOT use `helper.py run_fuzzer --corpus-dir` for this.** That path is
destructive and does not run your files: the base-runner `run_fuzzer` wrapper
**clears the mounted corpus dir and unpacks the baked-in
`<Target>_seed_corpus.zip`** (Tika's own unit-test files) into it, then fuzzes
those. Point it at a 260-file corpus and it runs the ~12 seed files instead and
wipes your dir down to libFuzzer's minimized set. Two failure modes in one: a
false "no crash" (it ran the seeds, which never crash) and a destroyed corpus.

Instead, invoke the built target **binary directly**, bypassing the wrapper, with
your corpus as a libFuzzer positional arg and `-runs=0` (load corpus, run each
once, exit — no mutation). Keep the pristine corpus elsewhere and hand the
container a throwaway copy:

```bash
docker run --rm --platform linux/amd64 --shm-size=2g \
  -e FUZZING_ENGINE=libfuzzer -e SANITIZER=address \
  -v <throwaway-corpus-copy>:/corpus \
  -v $HOME/oss-fuzz/build/out/apache-tika:/out \
  -t gcr.io/oss-fuzz-base/base-runner:latest \
  bash -c '/out/OneNoteParserFuzzer -runs=0 -timeout=60 -rss_limit_mb=3600 /corpus'
```

Mount `/out` (the target script resolves its Jazzer jars and classpath from its
own dir). `Done N runs` should equal your file count — if it says ~12, you hit
the seed-corpus substitution above.

**Corpus must be a FLAT dir of files.** libFuzzer does not recurse into
subdirectories. A sharded/nested corpus (e.g. `onenote/a3/47/<sha>`) must be
flattened first:

```bash
mkdir -p flat && find <nested> -type f -exec cp {} flat/ \;
```

(sha-named blobs are already unique, so no collisions).

## 3b. Open-ended (mutational) fuzzing

Drop `-runs=0` and let Jazzer mutate from the corpus to hunt new paths. Use the
**direct-binary** invocation again, not `helper.py run_fuzzer --corpus-dir` — that
wrapper still clears your dir and substitutes the baked seed corpus (see 3a).
Mount a **writable** working copy of the corpus (libFuzzer writes new
coverage-increasing units back into it; keep the pristine corpus elsewhere) and
an artifact dir for any reproducer it finds:

```bash
docker run --rm --platform linux/amd64 --shm-size=2g \
  -e FUZZING_ENGINE=libfuzzer -e SANITIZER=address \
  -v <writable-corpus-copy>:/corpus -v <artifact-dir>:/artifacts \
  -v $HOME/oss-fuzz/build/out/apache-tika:/out \
  -t gcr.io/oss-fuzz-base/base-runner:latest \
  bash -c '/out/OneNoteParserFuzzer -max_total_time=1200 -timeout=60 \
           -rss_limit_mb=3600 -artifact_prefix=/artifacts/ /corpus'
```

(If you ever do route flags through `helper.py run_fuzzer`, they need a `--`
separator — `run_fuzzer ... OneNoteParserFuzzer -- -runs=0` — or argparse rejects
the leading-dash flags. The direct-binary form above avoids that entirely.)

Memory note: `build.sh` runs these targets at `-Xmx3000m -rss_limit_mb=3600` on
purpose — audio/video/image/onenote parsers hit `new byte[~Integer.MAX_VALUE]`
single-allocation OOMs. An OOM under ~2–3 GB is a real finding; if you see one
above the rss limit, the fix is a bound in the parser, not a bigger heap.

### The loop is whack-a-mole: find → fix → rebuild → re-fuzz

libFuzzer **halts on the first finding** — so one campaign yields one bug, and an
early crash/OOM/hang after a few hundred execs means the target barely explored
the space. That is not a clean bill of health; it is one mole.

**Do NOT paper over it with `-ignore_ooms` / `-ignore_crashes` / `-ignore_timeouts`
to "keep finding more."** A resource-exhaustion bug (unbounded allocation, runaway
recursion, hang) is a **wall**: every input that reaches it dies *there*, so the
code *after* that point never runs and its bugs stay invisible no matter how long
you fuzz. Ignoring the finding just burns cycles re-hitting the same wall.

The only approach that makes progress:

1. Fuzz until it halts on a finding; save the reproducer.
2. **Fix that bug** in the parser (bound the count/array, cap the recursion) so the
   input survives past it.
3. Rebuild fuzzers (step 2 above) against the fix.
4. Re-fuzz — mutations now proceed past the old wall and surface the next bug.

Corollary: a wall early in a shared entry point (e.g. an OOM in the legacy
`OneNotePtr` path) also blocks fuzzing of *sibling* code (the fsshttpb/MS-ONESTORE
path), because mutations that flip the format-routing bytes fall into the wall
before reaching the sibling. Fix the walls nearest the entry point first.

## 3c. Tuning heap, timeout, and RSS limit

The generated target wrapper (`build/out/apache-tika/<Target>`) bakes in
`--jvm_args="-Xmx3000m:-Xss1024k"` and `-rss_limit_mb=3600mb`, then appends
whatever args you pass (`$@`). So:

- **Per-input timeout** (hang cutoff): `-timeout=<sec>` — a libFuzzer flag, passes
  straight through. A single input exceeding it is reported as a slow-unit /
  timeout finding.
- **RSS OOM trigger**: `-rss_limit_mb=<N>` — libFuzzer flag, passes through; the
  process is killed (exit 71) when total RSS crosses it.
- **JVM heap**: append `--jvm_args=-Xmx<N>m:-Xss1024k`. The wrapper's `$@` lands
  *after* its own `--jvm_args`, and **Jazzer's last `--jvm_args` wins** (verified:
  appending `-Xmx1024m` makes the JVM throw at ~1 GB, printing
  `use '-Xmx921m' to reproduce`). **Testing at a lower heap (~1 GB) is the point** —
  it surfaces allocation-heavy parses far sooner than the 3 GB default, and turns
  a bare libFuzzer RSS-OOM (no stack) into a `java.lang.OutOfMemoryError` that
  Jazzer prints *with a Java stack trace*.

```bash
docker run --rm --platform linux/amd64 --shm-size=2g \
  -e FUZZING_ENGINE=libfuzzer -e SANITIZER=address \
  -v <corpus-or-artifact>:/in -v $HOME/oss-fuzz/build/out/apache-tika:/out \
  -t gcr.io/oss-fuzz-base/base-runner:latest \
  bash -c '/out/OneNoteParserFuzzer -timeout=60 -rss_limit_mb=3600 \
           --jvm_args=-Xmx1024m:-Xss1024k /in/<reproducer>'
```

**Caveat — the OOM stack is the straw, not always the cause.** Capping the heap
makes the JVM throw wherever it happens to run out; that frame is where the
collector gave up, not necessarily the runaway allocation. Use it as a *lead*:
walk up the stack and confirm the unbounded count/array in code, or take a heap
histogram (dominant object class) to find what actually accumulated. (Real
example: a heap-capped OOM surfaced at `PropertyValue.<init>`, but the cause was
an unbounded 32-bit count two frames up driving `Stream.generate(...).limit(val32)`.)

**Caveat — Jazzer splits `--jvm_args` on `:`.** So JVM options that themselves
contain a colon (`-XX:+HeapDumpOnOutOfMemoryError`, `-Xlog:gc`) can't go through
`--jvm_args`. Pass those via the `JAVA_TOOL_OPTIONS` env var instead
(`-e JAVA_TOOL_OPTIONS='-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/in'`),
which the embedded JVM reads directly.

## 4. Reproduce a specific crash

```bash
python3 infra/helper.py reproduce apache-tika OneNoteParserFuzzer <testcase-file>
```

Runs that one input against the built target. Rebuild fuzzers (step 2) against a
candidate fix and re-run to confirm the crash clears.

## 5. Add seeds / a new target

- **Seeds:** edit `projects/apache-tika/build_seeds.sh` — one `find ... -name
  '*.ext' | xargs zip -u <Target>_seed_corpus.zip` line per extension. Real,
  structurally-valid files matter far more than count: coverage-guided fuzzing
  needs a seed that already passes the parser's magic/structure checks to reach
  the interesting code (a OneNote seed must carry the `.one` GUID header). A
  private/real-document corpus stays local — do not commit it to oss-fuzz.
  - **Gathering a real corpus from Common Crawl:**
    [`commoncrawl-fetcher-lite`](https://github.com/tballison/commoncrawl-fetcher-lite)
    samples binary files out of Common Crawl by HTTP-declared and Tika-detected
    type (skipping truncated payloads) into an output directory — a fast way to
    assemble a large, format-specific, structurally-valid corpus for a target.
- **New target:** add `FooParserFuzzer.java` next to the others following the
  `ParserFuzzer.parseOne` + swallow-expected-exceptions pattern.

## Disclosure caveat (read before touching the public project)

The `apache-tika` project on Google's infra **auto-files bugs and discloses on
a 90-day timer.** For findings we are deliberately holding private — e.g. the
metadata-extractor HEIF/WebP/TIFF DoS bugs routed through ASF security / the
Tika PMC — keep the work **local**: do not push seeds or targets that reach an
undisclosed bug to the public oss-fuzz repo, and do not open the finding
upstream. `ImageParsersFuzzer` drives Tika → metadata-extractor, so a strong
image corpus can surface exactly those; verify a fix locally, but disclose
through the agreed channel, not by letting OSS-Fuzz file it. See the 4.0.1 TODO
(image-parser DoS items) for what is under embargo.

## Git policy

Editing files under a local `oss-fuzz` checkout is fine, but the same
never-commit/never-push default applies (see `.skills/dev/SKILL.md`): stage and
hand back a suggested message; the maintainer pushes to oss-fuzz.
