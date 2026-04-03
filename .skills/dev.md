# Tika Development Skill

Guidelines and checklist for developing against the Apache Tika codebase.

## Git Policy

Unless otherwise directed, the user wants to commit and push changes
themselves.  Do not run `git commit` or `git push`.  Stage files and
provide the suggested commit message for the user to execute.

## Session Start Checklist

1. **Local Maven repo** — Ask the user if they want to use an in-repo
   `.local_m2_repo` (via `-Dmaven.repo.local=$(pwd)/.local_m2_repo`).
   This isolates builds from the system `~/.m2/repository` and avoids
   polluting or being affected by other projects.

2. **Maven wrapper** — Use `./mvnw` (or the fallback
   `/apache/apache-maven-3.9.12/bin/mvn` if the wrapper is absent).

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
  tests, checkstyle, and spotless in one flag.  Prefer this over
  individual `-D` skip flags when you want a quick build (e.g.,
  installing for downstream consumers or eval runs):
  ```bash
  ./mvnw clean install -pl <module> -am -Pfast \
    -Dmaven.repo.local=$(pwd)/.local_m2_repo
  ```
  Run **without** `-Pfast` before final commit to catch formatting
  and style issues.

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

## Testing an End-to-End Change

When a change affects parsing output (e.g., new parser behavior,
encoding fix), run a before/after comparison using tika-eval.
See `.skills/tika-eval-compare.md` for the full procedure.

## Pre-Commit Checks

```bash
# Full compile with checkstyle (catches formatting issues)
./mvnw clean compile -pl <module> -am \
  -Dmaven.repo.local=$(pwd)/.local_m2_repo

# Run module tests
./mvnw clean test -pl <module> \
  -Dmaven.repo.local=$(pwd)/.local_m2_repo
```
