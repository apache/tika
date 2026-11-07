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

# tika-fuzzing

[Jazzer](https://github.com/CodeIntelligenceTesting/jazzer) `@FuzzTest` targets.
`AutoDetectParserFuzzTest` runs `AutoDetectParser` (via `RecursiveParserWrapper`) on
`TikaInputStream.get(byte[])`, `get(InputStream)` and `get(Path)`. The classpath holds
`tika-parsers-standard-package` plus `tika-parser-sqlite3-module`; add a parser module to
the pom to fuzz it. Jazzer gets no coverage from native code (sqlite-jdbc), and a native
crash kills the JVM.

The targets look for OOM, stack overflow, hangs, and exceptions other than
`TikaException`/`IOException`/`SAXException`. `CompositeParser` wraps parser
`RuntimeException`s in `TikaException`; add `-Dtika.fuzz.strict=true` to report those too.

| Property | Default | |
|---|---|---|
| `-Dtika.fuzz.timeout=<s>` | `60` | per-input parse timeout, all modes; a hang is reported with the stuck stack |
| `-Dtika.fuzz.xmx=<size>` | `3g` | test JVM heap (OSS-Fuzz uses `-Xmx3000m`) |
| `JAZZER_MAX_DURATION=<d>` | Jazzer's | length of a fuzzing session |

Do not use JUnit's `junit.jupiter.execution.timeout.*`: in fuzzing mode it ends the
whole session, not one input.

## Regression mode (every build)

`./mvnw clean test -pl tika-fuzzing` replays each method's inputs from
`src/test/resources/org/apache/tika/fuzzing/AutoDetectParserFuzzTestInputs/<method>/`
and `.cifuzz-corpus/`. An input that exceeds `tika.fuzz.timeout` fails the build.

## Replaying a directory

For a new parser or a refactor, run every file under a directory through all three
input modes, with no fuzzing:

```bash
./mvnw clean install -pl tika-fuzzing -am -Pfast
./mvnw clean test -pl tika-fuzzing -Dtest=CorpusReplayTest -Dtika.fuzz.corpus=<dir> \
  -Dtika.fuzz.timeout=10
grep -o 'testcase name="[^"]*"' tika-fuzzing/target/surefire-reports/TEST-*CorpusReplayTest.xml
```

The report names the mode and file of each failure; the console shows only an index.

One JVM runs the whole directory. An OOM ends the run, and a timed-out parse cannot be
stopped: it keeps a core and its memory, so files after it can time out falsely. Trust
the first timeout; re-run later ones alone.

## Fuzzing

```bash
./mvnw clean install -pl tika-fuzzing -am -Pfast
JAZZER_FUZZ=1 JAZZER_MAX_DURATION=10m \
  ./mvnw clean test -pl tika-fuzzing -Dtest='AutoDetectParserFuzzTest#path'
```

Fuzzing mode runs one method at a time. To seed it, **copy** real files into
`.cifuzz-corpus/org.apache.tika.fuzzing.AutoDetectParserFuzzTest/<method>/`
(git-ignored). libFuzzer adds new inputs to that directory, so never point it at the
original corpus. A plain test run also replays everything in it.

Jazzer stops at the first finding and writes it to
`src/test/resources/org/apache/tika/fuzzing/AutoDetectParserFuzzTestInputs/<method>/` as
`crash-*` (exceptions, OOM and hangs alike). Later plain test runs replay it. Finding
files are git-ignored. Commit one with `git add -f` only after the bug is fixed and
disclosed (see `SECURITY.md`). Commit only small, synthesized files, never corpus
documents.

After a session, empty `.cifuzz-corpus/` and remove the finding files you are not
keeping, or every later build replays them.
