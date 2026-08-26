---
name: feature-workflow
description: >
  Taking a multi-PR feature from "shape unknown" to merged without five
  review rounds per PR: spike until interfaces stop moving, write the
  contract, cut PRs along contract seams, one review per PR. Use when
  starting a feature that touches more than one lifecycle object or public
  interface, when a PR review keeps changing interfaces, or when splitting a
  large branch.
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

Local override: `$TIKA_SKILLS_LOCAL/feature-workflow/LOCAL.md` (default `~/.tika-skills`),
read after this file, wins on conflict.

# Feature Workflow: spike, contract, cut, ship

A complex feature's shape is learned by building it. Learning it *on the PR*
costs a review round per lesson and reshapes what the next round reviews.
Keep learning and shipping on different branches.

## 1. Spike

Throwaway branch. Build end to end, roughly: no CHANGES, docs, or polish;
change neighbors freely. Review it (`.skills/dev/pr-review/SKILL.md`) and let
findings reshape interfaces.

**Exit:** the last review changed edge-case handling, not an interface. While
reviews still rename, split, or add methods, keep spiking (pr-review verdict
"still spiking").

## 2. Contract

Write down what the spike taught, half a page per lifecycle object
(open/close, acquire/release, publish/abort, spill, rewind): states and
transitions, each method's behavior per state, resource ownership on success
and every failure path, threading. Put it in the type's javadoc.

Encode it as an `Abstract<Type>ContractTest` every implementation extends:
close twice, abort then close, write after close, throw mid-write then close,
resources released on both paths. This is what makes review converge.

## 3. Cut PRs

Split *after* the spike, one contract (or tightly coupled group) per PR.
Splitting before is guesswork and leaves one PR holding five contracts.
Each PR carries only that contract's files, its contract test, CHANGES, docs.
Everything else waits for its own PR or the todo doc.

## 4. Ship

Per PR: one high-effort review, fix, one confirm pass on the delta. A third
round means either a fix changed an interface (pull that piece back to the
spike) or findings are out of scope (todo doc, not the fix commit). Never
widen a PR during review.

| You see | Do |
|---|---|
| Review adds/renames/splits a type or method | keep spiking; no PR yet |
| Review finds edge cases only | write contract + test, cut PR |
| PR review changes an interface | pull piece back to spike |
| PR review finds out-of-scope issue | todo doc, not this PR |
| Third review round on a PR | one of the two above applies |
