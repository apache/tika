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

# Agent Guidance for Apache Tika

Detailed guidance lives in **`.skills/`** — `devs/` for working on Tika,
`users/` for using it as a tool; one directory per skill, each with a
`SKILL.md`. Read `.skills/devs/development/SKILL.md` before doing anything else — it has
the ground rules: build with `./mvnw` (always `clean`, `-Pfast` for quick
builds), never run `git commit`/`git push` or write to GitHub, code and test
conventions, pre-commit checks.

`.skills/` is contributor guidance, not project policy (that lives in
`SECURITY.md`, `CONTRIBUTING.md`, and the release process).

## Local overrides

A skill may have a private companion at `$TIKA_SKILLS_LOCAL/<name>/LOCAL.md`
(default `~/.tika-skills`): machine paths, personal workflow defaults. Read it
after the public skill; it wins on conflict. Additive unless a `## Replaces`
section names public rules it turns off. Never committed, never quoted into
any public artifact.

## Working on Tika

Contributor-facing — building, testing, and releasing this codebase.

| Skill | Use when |
|-------|----------|
| `.skills/devs/development/SKILL.md` | Any development task — load at session start |
| `.skills/devs/feature-workflow/SKILL.md` | Multi-PR features; reviews keep changing interfaces; splitting a large branch |
| `.skills/devs/pr-review/SKILL.md` | Reviewing a PR or branch, or self-reviewing before submitting |
| `.skills/devs/metadata-schema/SKILL.md` | Adding/renaming metadata keys; schema gate failures |
| `.skills/devs/tika-eval-compare/SKILL.md` | Before/after corpus comparison of two Tika builds |
| `.skills/devs/tika-eval-encoding-regression/SKILL.md` | Charset-detector regression hunts |
| `.skills/devs/tika-eval-h2-query/SKILL.md` | Querying the tika-eval H2 database directly |
| `.skills/devs/update-site-for-release/SKILL.md` | Updating tika.apache.org for a release |
| `.skills/devs/oss-fuzz/SKILL.md` | Fuzzing a parser locally (OSS-Fuzz Jazzer targets); reproducing an OSS-Fuzz crash |

## Using Tika

For any agent that wants Tika as a tool — not specific to this repo, useful
whether or not you're working on Tika's own source.

| Skill | Use when |
|-------|----------|
| `.skills/users/file-to-markdown/SKILL.md` | Turning a file (PDF, Office, email, archives, images, ...) into Markdown + metadata via tika-app or tika-server |
| `.skills/users/file-to-markdown-docker/SKILL.md` | Need guaranteed OCR/GDAL with no local install, or a disposable containerized Tika — running tika-server via Docker |
| `.skills/users/file-forensics/SKILL.md` | What a file claims vs. contains: provenance, tamper signals, hidden/embedded content, macros, digests — evidence, not verdicts |

## Security

Security model: [SECURITY.md](./SECURITY.md)

Agents that scan this repository should consult `SECURITY.md` before
reporting issues. It links Tika's published
[security model](https://tika.apache.org/security-model.html), which states
what the project does and does not treat as a vulnerability — in particular
its position on untrusted data, untrusted callers, and denial of service.
