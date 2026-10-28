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

# The v2 typed Document contract (TIKA-4766)

This page explains what `TikaV2Service` returns and why it is shaped the way it is.
The configuration reference is in [the module README](../README.md), and the field
list in [tika-grpc-api](../../tika-grpc-api/README.md).

## Why a second service

The v1 `tika.Tika` service returns metadata as a `fields` map of strings. A client
has to know Tika's key names, parse every number and date itself, and guess whether
a key holds one value or several. It also gets every outcome in-band, so a retry
policy cannot tell "this PDF is broken" from "the server is overloaded".

v2 keeps v1 exactly as it is on the wire and adds a second service on the same port
that returns a typed `Document`. Fetchers and iterators are still managed through
v1. New behaviour goes into v2; v1 is frozen.

## What a Document holds

| Part | Contents |
|---|---|
| Envelope | `id` (the caller's key), the detected `content_type`, `parsed_at` |
| `origin` | filename, byte size (unset means unknown, 0 means empty), parser, and the source SHA-256 when the pipeline has a digester |
| `status` | `STATUS_SUCCESS`, `STATUS_PARTIAL` or `STATUS_FAILED`, the raw pipes status, errors, parsers used, Tika version, elapsed time |
| `metadata` | the Dublin Core properties most formats share, as real types: title, authors, description, keywords, languages, publishers, identifiers, created and modified as Timestamps, rights |
| `extra` | every other metadata key, each as a list so multi-valued keys keep all their values |

The extracted text is not in the Document yet. That is a separate step.

### How values get their types

A value in `extra` is typed only when Tika knows the type: the key's `Property` in
tika-core declares it, or the pipes fork reports it (`metadata-list.typedValues`,
TIKA-4969). Anything else is a string. Nothing is guessed from what the value looks
like, so a string that happens to look like a number stays a string. If a value does
not match its declared type, the whole entry falls back to strings instead of
arriving half-typed.

Which Tika property becomes which typed field is decided in tika-grpc-mapper by
`DocumentTransformer` classes, not in the proto. Supporting a new format's fields
means adding a transformer; the wire contract does not change.

A multi-valued title, description, rights, created or modified keeps its first value
in the typed field and all of its values in `extra`.

## Outcomes: Document status or gRPC status

The rule is: a fact about the document goes in the Document, a fact about the server
goes in the gRPC status.

| Situation | v2 answer |
|---|---|
| Parsed | OK, `STATUS_SUCCESS` (pipes `PARSE_SUCCESS`, `EMPTY_OUTPUT`, ...) |
| Parsed with an exception, or hit the timeout part way | OK, `STATUS_PARTIAL` |
| Fetch failed, parse threw, worker timed out, ran out of memory or crashed on it | OK, `STATUS_FAILED`, reason in `errors` |
| No worker free within `pipes.maxWaitForClientMillis` | `RESOURCE_EXHAUSTED` |
| Pipes runtime or fetcher failed to start | `UNAVAILABLE` |
| Unknown fetcher | `NOT_FOUND` |
| Anything unexpected | `INTERNAL`, cause in the server log only |

A timeout, OOM or crash stays a `STATUS_FAILED` Document because sending the same
bytes again would most likely do the same thing; it is not a server error worth
retrying. A `FAILED` or `PARTIAL` Document always has at least one error.

Errors are the first line of the pipes diagnostic, at most 1024 characters. Stack
traces stay in the server log unless `parse-context.exception-reporting` is set, in
which case its text is passed through as is.

## Cancelling and deadlines

A call that the client cancels, or whose deadline passes, stops waiting for a worker
right away. A parse already running on a worker is allowed to finish and its result
is dropped. Interrupting it instead restarts the worker JVM in the default per-client
mode, so a client with tight deadlines would cost a JVM start per call.
`"grpc": {"cancelRunningParse": true}` brings the interrupt back.

Waiting for a worker and running on one are kept apart by a permit per pipes client,
taken before asking pipes for a client.

## Keeping v1 frozen

`tika.proto` is unchanged. CI (`.github/workflows/buf.yml`) runs `buf lint` on both
proto modules and `buf breaking` against the PR's base branch, so a change that would
break a v1 client fails the build.

- `tika-grpc-api` (v2) passes buf's STANDARD rules with no exceptions.
- `tika-grpc/buf.yaml` (v1) turns off the six naming rules v1 cannot meet without
  renaming its wire API. Each is commented. Every other rule applies.

One v1 behaviour does change: a fetch failure's `error_message` is cut to its first
line, like v2's errors, unless `parse-context.exception-reporting` is set. The wire
format is the same.

`emitter_id` is reserved on the v2 requests (field 4). v2 returns the Document and
never emits, so accepting the field and ignoring it would be misleading.

## Modules

| Module | Role |
|---|---|
| `tika-grpc-api` | v2 protos and generated classes; clients depend on this alone |
| `tika-grpc-mapper` | `DocumentBuilder` and the `DocumentTransformer`s, Tika `Metadata` to `Document` |
| `tika-grpc` | `TikaGrpcV2ServerImpl`, registered beside the v1 implementation |

## Testing

```bash
mvn clean verify -pl tika-grpc-api,tika-grpc-mapper,tika-grpc
```

The mapper tests cover the typing rules and transformers on real parse output. The
server tests cover each row of the outcome table, cancellation, the bidi stream after
an error, and older v2 clients that still send `emitter_id`. The tika-e2e-tests
`HandlerTypeTest` runs a PDF through a live server over both v1 and v2.
