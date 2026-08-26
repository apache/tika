---
name: ground-rules
description: >
  Ground rules for working in the Tika codebase — git policy, Maven
  wrapper/repo conventions, building and testing specific modules, code and
  test conventions, pre-commit checks. Load at session start for any Tika
  development task.
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

Local override: `$TIKA_SKILLS_LOCAL/ground-rules/LOCAL.md` (default `~/.tika-skills`),
read after this file, wins on conflict.

# Tika Development Skill

Guidelines and checklist for developing against the Apache Tika codebase.

## Question the direction, not just the code

Before optimizing a change — yours or a PR's — ask whether it should exist:
does it belong in Tika, is the complexity proportional to the need, would
config, an existing mechanism, a plugin, or documentation serve the use case
more cheaply?  Every merged feature is surface the project maintains for
decades.  Steelman the use case first and question the vehicle, not the goal;
pushback must name a concrete cost or a simpler path — never taste alone, and
never no for the sake of no.  "The direction is right" is a valid conclusion.

Feature whose shape isn't known yet: spike first, cut PRs after
(`.skills/dev/feature-workflow/SKILL.md`).

## Git Policy (default — personally overridable)

Never run `git commit` or `git push` — no commits of any kind, including
merge commits.  If a merge is needed, use `git merge --no-commit --no-ff`
and hand back.  Stage files and provide the suggested commit message for
the user to run.

Never write to GitHub (PR comments, reviews, issues, labels, merges).
Read-only `gh` is fine.

**Precedence**: these are conservative defaults for *workflow* — actions on
the contributor's own machine and accounts.  A contributor's personal agent
configuration (their own skills, CLAUDE.md/AGENTS.md, settings, or a
`LOCAL.md` overlay — see `AGENTS.md`) may override them.  Everything else in
this file — code and comment conventions, test discipline, hygiene,
pre-commit checks — governs what lands in the repo and is project policy:
personal configuration does not override it.

## Session Start Checklist

1. **Local Maven repo** — Default to an in-repo `.local_m2_repo`
   (via `-Dmaven.repo.local=$(pwd)/.local_m2_repo`) unless the user says
   otherwise.  This isolates builds from the shared `~/.m2/repository` and
   avoids polluting or being affected by other projects.

2. **Maven wrapper** — Use `./mvnw`; fall back to a system Maven (3.9+)
   only if the wrapper is absent.

3. **Merge conflicts** — Check `git status` for `UU` files and resolve
   before building.

## Maven Rules

- **Always include `clean`** in every `./mvnw` invocation.
  Stale classes in `target/` cause hard-to-debug failures.
  ```bash
  ./mvnw clean compile -pl <module> ...   # not just: mvnw compile
  ./mvnw clean test -pl <module> ...      # not just: mvnw test
  ./mvnw clean install -pl <module> ...   # not just: mvnw install
  ```

- **Always use absolute path for local repo**:
  ```bash
  -Dmaven.repo.local=$(pwd)/.local_m2_repo
  ```

- **Fast builds with `-Pfast`** — use the `fast` profile to skip
  tests, checkstyle, spotless, and rat in one flag.  Prefer this over
  individual `-D` skip flags when you want a quick build (e.g.,
  installing for downstream consumers or eval runs):
  ```bash
  ./mvnw clean install -pl <module> -am -Pfast \
    -Dmaven.repo.local=$(pwd)/.local_m2_repo
  ```
  Run **without** `-Pfast` before final commit to catch formatting
  and style issues.  License (rat) checks run only under `-Ppedantic`
  (or explicit `apache-rat:check`), not in default builds.

  **`-Pfast` skips test *execution*** (by design): a green `-Pfast`
  build — including `-Pfast test` — has run zero tests, and stale
  `target/surefire-reports/*` will look current. Verify with a plain
  (non-`-Pfast`) `test` run.

- **Plugin zips resolve only after `package`** — a reactor `clean test`
  fails on modules that depend on pipes plugin zips (`tika-server-core`,
  `tika-app`, ...) unless the zips are already in the local repo.  Use
  `clean install` (or `-Pfast install` first).

- **Forked JVM tests** — Integration tests in `tika-pipes` fork new
  JVMs that load classes from the local Maven repo, not from
  `target/classes`.  You must `./mvnw clean install -Pfast` the
  changed modules before running integration tests that fork.

## Building Specific Modules

```bash
# Single module (with dependencies)
./mvnw clean compile -pl <module> -am \
  -Dmaven.repo.local=$(pwd)/.local_m2_repo

# Run a single test class
./mvnw clean test -pl <module> -Dtest=<TestClass> \
  -Dmaven.repo.local=$(pwd)/.local_m2_repo -Dcheckstyle.skip=true

# Install for downstream consumers (tika-app, integration tests)
./mvnw clean install -pl <module> -am -Pfast \
  -Dmaven.repo.local=$(pwd)/.local_m2_repo
```

## Common Module Paths

| Module | Path |
|--------|------|
| tika-core | `tika-core` |
| tika-app | `tika-app` |
| tika-server | `tika-server/tika-server-core` |
| tika-eval | `tika-eval/tika-eval-app` |
| Pipes core | `tika-pipes/tika-pipes-core` |
| Pipes API | `tika-pipes/tika-pipes-api` |
| Async CLI | `tika-pipes/tika-async-cli` |

## Code Conventions

- ASF License 2.0 header on all Java files
- Spotless formatter runs during build — don't fight it
- Tests use `@TempDir Path tmp` for temp directories
- No emojis in code or comments
- **Comments**: every comment must earn its place — one short line by
  default; multi-line only for a genuinely non-obvious WHY (subtle
  invariant, workaround, spec quirk).  Never restate the code, narrate the
  next line, justify the change to a reviewer, or describe past states of
  the code.
- **Input files are hostile**: bound anything derived from document content
  (loop counts, allocations, timeouts); release external processes, temp
  files, and pool slots on every failure path.
- **No local/machine-specific paths** in committed code, tests, docs, or
  config — never `/home/<user>`, `/Users/<user>`, `C:\Users\<user>`, or a
  personal `~/data/...`.  Use a placeholder (`<workdir>/`, `<corpus>`),
  `@TempDir`, or an in-repo `src/test/resources` fixture instead.  *Only*
  legitimate exception: a path that is the data under test (e.g. an expected
  metadata value extracted from a test document) — leave those untouched.

## Test Discipline

- A behavioral change gets a regression test that fails without it.  Where
  impractical (timing, native binaries, external services, kill paths), say
  so explicitly and name the next-best check.
- Prove a negative by reverting the fix: an "X does not happen" test that
  still passes is not a test.  Assert on what the consumer is handed, not an
  ambient side effect (a `@TempDir` watch misses `TemporaryResources` not
  bound to it).
- Cover error paths and the configuration/mode matrix — a behavior verified
  in only one parse mode or config shape is a gap (RMETA-only tests miss
  CONCATENATE-only bugs).
- Keep tests non-duplicative: don't add a test whose failure another test
  already guarantees.
- Where there's bang for the buck, prefer parameterized tests over
  copy-pasted cases, randomized inputs over hand-picked ones (log the seed
  so failures reproduce), and fuzzing for parsers and format/boundary
  arithmetic.  Don't force it on code a couple of fixed cases fully cover.

## Metadata Keys & Schema Registry

Adding/renaming a metadata key touches the committed, build-gated registry in
`tika-metadata-schema` — regeneration has real traps. See
`.skills/dev/metadata-schema/SKILL.md`.

## Testing an End-to-End Change

When a change affects parsing output (e.g., new parser behavior,
encoding fix), run a before/after comparison using tika-eval.
See `.skills/dev/tika-eval-compare/SKILL.md` for the full procedure.

## Pre-Commit Checks

```bash
# Full compile with checkstyle (catches formatting issues)
./mvnw clean compile -pl <module> -am \
  -Dmaven.repo.local=$(pwd)/.local_m2_repo

# Run module tests
./mvnw clean test -pl <module> \
  -Dmaven.repo.local=$(pwd)/.local_m2_repo
```

Also before commit:

- Commit message / PR title references a JIRA ticket (`TIKA-XXXX`).
- CHANGES entry for user-visible changes.
- New dependencies: ASF-compatible license; LICENSE/NOTICE updated.

Scan the staged diff for machine-specific local paths before committing
(see Code Conventions). Added lines only; review any hit by hand — a test
fixture's expected value is allowed, a real config/doc/code path is not:

```bash
git diff --cached -U0 | grep -E '^\+' \
  | grep -nE '/home/[A-Za-z0-9._-]+|/Users/[A-Za-z0-9._-]+|[A-Za-z]:\\+Users|~/data/' \
  && echo "^ local path in staged diff — replace with a placeholder/fixture"
```
