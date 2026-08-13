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

Detailed guidance lives in **`.skills/`** (one directory per skill, each with
a `SKILL.md`). Read `.skills/dev/SKILL.md` before doing anything else — it has
the ground rules: build with `./mvnw` (always `clean`, `-Pfast` for quick
builds), never run `git commit`/`git push` or write to GitHub, code and test
conventions, pre-commit checks.

| Skill | Use when |
|-------|----------|
| `.skills/dev/SKILL.md` | Any development task — load at session start |
| `.skills/pr-review/SKILL.md` | Reviewing a PR or branch |
| `.skills/metadata-schema/SKILL.md` | Adding/renaming metadata keys; schema gate failures |
| `.skills/tika-eval-compare/SKILL.md` | Before/after corpus comparison of two Tika builds |
| `.skills/tika-eval-encoding-regression/SKILL.md` | Charset-detector regression hunts |
| `.skills/tika-eval-h2-query/SKILL.md` | Querying the tika-eval H2 database directly |
| `.skills/update-site-for-release/SKILL.md` | Updating tika.apache.org for a release |
