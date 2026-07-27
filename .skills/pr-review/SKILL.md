---
name: pr-review
description: >
  Review a GitHub pull request (or a local branch) against Apache Tika. Fetches
  the PR, reads the linked JIRA + review thread, builds and tests the ACTUALLY
  affected modules (not just the changed one), and hunts the regressions that
  slip past module-local builds. Use for "review PR #NNNN", "look at this PR",
  "can you review https://github.com/apache/tika/pull/NNNN".
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

# Review an Apache Tika Pull Request

Goal: produce a review that a maintainer can act on — grounded in the code as
it actually is, with every substantive claim verified by building/running, not
by reading alone. Report findings ranked by severity, and say plainly what you
checked and what you did not.

## Output rules

- **Never post to GitHub.** No PR/issue comments, reviews, labels, approvals.
  Read-only `gh` is fine. Deliver the review as text in the chat for the user
  to post themselves. (This mirrors the user's standing instruction.)
- **Never commit.** If you draft a fix, stage it or paste a diff; the user
  commits.
- Lead with a one-line verdict (mergeable? blockers?), then findings
  most-severe first, then the routine/nit items, then "what I verified."

## 1. Gather context (do all of this before judging anything)

```bash
gh pr view <N> --repo apache/tika \
  --json number,title,author,state,body,files,additions,deletions,\
baseRefName,headRefName,url,comments
gh pr diff <N> --repo apache/tika
```

- Read the **linked JIRA** — the PR title is usually `TIKA-XXXX: ...`. Fetch it;
  the issue often lists a *proposed fix* with specific requirements the PR must
  satisfy (and sometimes a superset the PR deliberately scoped down):
  `curl -s "https://issues.apache.org/jira/rest/api/2/issue/TIKA-XXXX?fields=summary,description,comment"`
- **Read the existing PR review thread.** Reviewers (and their agents) often
  already asked for changes. Check each requested change against the *current*
  head — authors iterate, and a later commit may have **reverted** an earlier
  one. Diff the commits (`git log --oneline base..head`, `git show <sha>`) so you
  review the final state, not commit 1.
- **Don't assume a bot pre-reviewed.** Copilot coverage is going away, so you now
  own the full mechanical pass (§4) *and* the judgment (§5). If prior bot/agent
  comments exist, adjudicate them (confirm/refute — §6); otherwise do the pass
  yourself.

## 2. Get the branch and build the RIGHT modules

Use a throwaway worktree so you never disturb the user's checkout:

```bash
git fetch origin pull/<N>/head:pr-<N>
git worktree add "$SCRATCH/pr-<N>" pr-<N>
```

Build with the repo's rules (see `.skills/dev/SKILL.md`): `./mvnw`, always `clean`,
absolute `-Dmaven.repo.local`, `-Pfast` for a quick pass.

> **The trap that hides real bugs:** building only the changed module is not
> enough. A change to a *shared* type (an enum, an interface, a public method
> signature, a serialized field) can compile cleanly in its own module and
> **break a downstream module that recompiles against it.** Always ask: *who
> consumes the thing that changed, and did I compile them?*
>
> Concretely, if the diff touches `tika-pipes-api` / `tika-core` / any
> `*-api` module, compiling just that module proves almost nothing. Compile the
> consumers too (`tika-server-core`, `tika-pipes-fork-parser`, `tika-grpc`,
> `tika-app`, …). A full `./mvnw clean install` from the root is the honest
> check when you have time.

Run the PR's own new/changed tests, and the surrounding module's suite:

```bash
$SCRATCH/pr-<N>/mvnw -f $SCRATCH/pr-<N>/pom.xml clean install \
  -pl :<changed-module> -am -Dmaven.repo.local=<abs>/.local_m2_repo
```

Do a run **without** `-Pfast` on the changed module before you bless it, so
checkstyle + spotless run (Tika gates on both).

## 3. High-yield checks (these catch the most in Tika)

- **Enum constant added to an exhaustive `switch`.** Java `switch`
  *expressions* over an enum with no `default` are exhaustive-checked at compile
  time. Adding a constant turns every such switch into a **compile error** — in
  whatever module owns the switch, which is usually *not* the module that
  changed. Grep for consumers: `grep -rn "switch" --include=*.java | grep <EnumType or the switched var>`
  and check each for arrow-form `return switch (x) { ... };` with no `default`.
  (`switch` *statements* don't require exhaustiveness — they compile but may
  silently miss the new case; flag those as behavior gaps, not build breaks.)
- **New public field / config option → is it actually wired end to end?**
  Trace every read site of the old constant/limit and confirm the new
  configurable value reaches *all* of them (or that the ones it skips are
  intentional and documented). A config that only takes effect on one of two
  ends is a common half-wiring.
- **Docs vs. code.** Read the javadoc/CHANGES/README the PR adds or touches
  against what the code now does. Iterated PRs frequently leave a javadoc
  describing an *earlier* commit's behavior (e.g. "configures both ends" after
  a later commit made it one end). This is exactly the kind of thing that reads
  as done but isn't.
- **New enum status / result code plumbed to the surface?** If the change adds a
  result/status, follow it to where users see it (HTTP response mapping, CLI
  exit code, serialized output). tballison's recurring ask: *surface the real
  cause* rather than collapsing it into a generic crash/500.
- **Error/stream handling correctness.** For IPC/socket/stream code: after an
  error, is the stream left synchronized or torn down? Is a "healthy, no
  restart" claim actually true for the *default* configuration (e.g. Tika's
  `useSharedServer` default is `false` → per-client forked server; a broken
  pipe there still kills the process regardless of client-side handling)? Verify
  the claim against the default path, not just the convenient one.
- **CHANGES.txt.** Tika lists every change under the current unreleased version
  with its `(TIKA-XXXX)`. A PR with no CHANGES.txt entry is usually incomplete
  (often fixed at merge, but call it out).
- **Catch-block ordering / exception hierarchy.** A new exception subclass must
  be caught before its supertype; confirm it compiles and that the intended
  handler actually wins.

## 4. Tika recurring-issue checklist (verify, don't just echo)

Ranked by how often reviews hit them. You own this pass now — work it top to
bottom; verify each hit.

**Resource / stream lifecycle** (densest cluster)
- `parse()` must NOT close the caller's `InputStream`; closing a `TikaInputStream`
  that wraps it closes it via `TemporaryResources`.
- Close per-entry/zip streams; try-with-resources so cleanup survives exceptions.
- Temp files: prefer delete-on-`TikaInputStream`-close (see `S3Fetcher`); avoid
  unconditional `deleteOnExit()`; delete on exception. Null a field after
  `close()`; shut down `Executors`; cache `URLClassLoader` (Windows jar-lock).

**Exceptions**
- Exception-in-`finally` masking the real one — esp. draining XHTML balancing
  handlers only on `SAXException` when `IOException`/`TikaException` also escape,
  so `endDocument()` throws on unbalanced elements. Drain for all escaping types.
- No silent swallow (log at least); catch specific exceptions by behavior; custom
  exceptions extend `TikaException`.

**Stream reading**
- Don't trust `skip()`/`read()` returns — use `IOUtils.skipFully`/`readFully`,
  check length. (`IOUtils.read` returns `[0,len]`, never `-1`.)

**Thread-safety** — parsers stateless/thread-safe, no mutable instance fields.
`Metadata` field leak: a value set conditionally but never cleared bleeds into
the next parse on a reused `Metadata`.

**Config (Jackson)** — param names map to setters by JavaBean rules (`skipOcr`,
not `skipOCR`); don't hand-track "userConfigured"; watch resource-exhaustion
defaults; array- vs object-form JSON schema changes break checked-in configs.

**Maven/POM** — no local version pins (use `tika-parent` dependencyManagement);
match `main`'s `X.Y.Z-SNAPSHOT`; no duplicate plugin blocks; ASF license header
(RAT) on every new file incl. test resources.

**Tests** — coverage for new class/factory/method; no nonexistent resource files;
no net downloads in `@BeforeAll`; no `lsof`/`kill`/`docker` assumed present, use
`assumeTrue(exe present)`; restore global state (timezone, ServiceLoader CCL); a
disabled test often hides a real regression — flag it.

**Numeric/boundary** — range-check offsets/sizes from container bytes; int
overflow from corrupt models; `getFileName()` NPE at FS root; code points >
`Character.MAX_CODE_POINT` into `Character.*`.

**XHTML output** — emit real markup (`<div class="section">`, `<p>`), not raw
body writes; balanced/nested close. Leave `XHTMLContentHandler` alone (no
auto-close/suppression — fix the parser or let it throw).

**Docs & style** — javadoc/README/adoc claims vs actual code (rename drift,
omitted fields, "order irrelevant"); correct JIRA id; runnable commands; no
real-looking secrets; no machine-specific paths; no wildcard imports; drop
unrelated formatter/import churn.

## 5. Go beyond the mechanical pass (judgment a bot won't apply)

- **Necessity / altitude** — "Is this needed? Does it justify the code +
  maintenance? Simpler/safer way?" Don't guess system paths (portability).
- **Scope discipline** — unrelated whitespace/bugfixes → separate commit/PR; keep
  the diff surgical.
- **Cross-module + security reasoning** (see §3) — downstream compile breaks;
  defense-in-depth (e.g. XML entity resolver empty-string trap).

## 6. Known false positives — reproduce before re-raising

- `ByteBuffer.wrap(arr, off, len)` starts at position `off`, not 0 (only 1-arg
  `wrap` starts at 0).
- `IOUtils.read` returns `[0,len]`, never `-1` — a `-1` check is not "missing".
- "Our own file" / test fixtures → no `SecurityException`; file expected present.
- A plausible read is not a bug. Reproduce, or cite the exact contract, first.

## 7. Verify, don't assert

For every non-trivial finding, prove it:

- Compile break → show the compiler error (build the consumer module).
- "This test doesn't cover X" → add/trace the case and show it.
- Behavioral claim → a tiny probe `main()` on the built classpath beats
  speculation. Build a classpath with
  `mvn dependency:build-classpath -Dmdep.outputFile=cp.txt` and run with a JDK
  matching the module's bytecode target.

Distrust smoothness: the more obviously-fine a shared-type change looks, the
more it earns a downstream compile.

## 8. Write it up

- **Verdict** first: is it mergeable as-is? What's the blocker?
- **Blockers** (build breaks, correctness bugs) with the evidence.
- **Design / should-fix** (half-wiring, stale docs, narrow-benefit claims).
- **Nits** (CHANGES.txt, PR-description drift, naming).
- **What I verified** — modules built, tests run, probes executed — and what I
  deliberately did *not* check, so the maintainer knows the edges of the review.
- If you wrote a fix, include it as a diff/patch; do not commit or push.

## Cleanup

```bash
git worktree remove "$SCRATCH/pr-<N>" --force
git branch -D pr-<N>   # optional
```
