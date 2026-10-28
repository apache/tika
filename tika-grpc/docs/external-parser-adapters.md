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

# External parser adapters (TIKA-4771, TIKA-4951)

How this fits with the rest of tika-grpc v2: [architecture.md](architecture.md).
How to set it up and use it: [external-parsers-manual.md](external-parsers-manual.md).

ParseBytes (see [parse-bytes.md](parse-bytes.md)) can send the same bytes to
external gRPC parsers, written in any language, and fold what they return into
Tika's own typed `Document`. This page covers the design, the trust model and how
to write an adapter. Configuration keys and defaults are in
[the module README](../README.md#external-parsers-experimental-tika-4771).

## Why adapters

Some formats have better parsers outside the JVM. FastWARC, a Rust WARC reader, is
the case this was built for (TIKA-4951). Passing an external reply through as an
opaque `Any` would leave every client to decode a schema Tika knows nothing about.
Calling services through gRPC reflection and JSON would make Tika depend on whatever
a remote server chooses to advertise.

Instead, each parser comes with an **adapter jar**: the parser's generated stubs and
one `ExternalParserAdapter`, found with `ServiceLoader`. The adapter builds the typed
request and maps the typed reply into Tika's terms (child documents and Tika
`Metadata`), so the results come out typed by the same `Property` rules as Tika's own
parse. Putting the jar on the classpath and adding an entry under
`grpc.externalParsers` is the whole install. tika-grpc compiles none of the parser's
protos.

## Where it runs

```
ParseBytes request
  -> Tika parse in a pipes worker         -> Tika's Document
  -> for each configured adapter whose types match Document.content_type:
       buildRequest -> unary call -> map(reply) -> AdapterContribution
  -> AdapterMerge + DocumentComposer      -> one Document back to the caller
```

The calls run one after another, after Tika's parse, inside the tika-grpc server
JVM. FetchAndParse never calls adapters, because the server never holds the fetched
bytes.

Routing uses the base type of `Document.content_type`. A gzipped WARC is detected
as `application/warc+gz` by its bytes, so `crawl.warc.gz`, `crawl.gz` and an unnamed
upload all reach a WARC adapter. There is no filename fallback.

## Trust model

An adapter may contribute two things:

- **Child documents** it authors in full, each with a path that starts with `/` and
  is unique for that producer. They land in `Document.embedded` (field 41) with the
  adapter's `producer_id`. `EmbeddedDocument` is flat: no id and no nesting.
- **Parent keys under its own prefix** (`warc:` for FastWARC), added to `extra` only
  where Tika recorded no key of that name.

It never changes Tika's core fields, status, origin, id or Tika's children. tika-grpc
enforces this whatever the adapter wrote: keys outside the prefix are dropped with a
warning. At startup a prefix must end with `:` or `-`, and is refused if it overlaps
one of Tika's namespaces (`tk:`, `X-TIKA`, `dc:`, `Content-`, `pdf:` and the rest,
built from tika-core's metadata classes) or another configured adapter's, ignoring
case. A mode where an adapter replaces Tika's parse would have to be explicit, and is
not part of this.

`map` returns an immutable `AdapterContribution` built through a builder that applies
the limits as the adapter adds to it. The server refuses a contribution built with
other limits than the ones it handed out.

**Adapters are trusted code.** They run in the server JVM, not in a forked worker.
An adapter that hangs or runs out of memory does so in the server. The limits bound
what the server keeps from an adapter, not the adapter's own work.

## Results and failure

Each adapter gets one `ExtensionResult` in `Document.extensions` (field 40):

| Status | Meaning |
|---|---|
| `STATUS_OK` | everything it contributed was merged |
| `STATUS_PARTIAL` | the parser stopped part way; what it read before was merged, the first warning says why |
| `STATUS_FAILED` | nothing merged: call failed, timed out or was cancelled, reply too big, adapter threw or said the parse failed |
| `STATUS_SKIPPED` | the document was over the entry's `maxContentBytes` and was not sent |

A failed adapter never fails the request. Tika's Document comes back untouched with
a `STATUS_FAILED` slot and a one-line reason. An `Error` from an adapter (a
`NoClassDefFoundError`, say) is caught the same way; only VM errors such as
`OutOfMemoryError` are rethrown. A failed slot never carries the raw reply.

The slot also says how many children the parser reported and how many were kept.
When a limit cuts the list, `truncated` is set, so a consumer does not take a partial
list for the whole archive.

## Limits

Defaults are sized for a 256 MiB WARC:

| Limit | Default |
|---|---|
| `maxReplyBytes`, per parser | 128 MiB, separate from the server's inbound limit |
| `maxChildren`, per reply | 100,000 |
| `maxEmbeddedBytes`, per reply | 64 MiB |
| `maxKeysPerDocument`, `maxValueChars` | 256, 8192 |
| `externalParsersMaxAddedBytes`, shared by all parsers | 96 MiB |

Tika's own Document is never cut and does not count against the shared budget.

A ParseBytes request keeps its `parseBytesMaxConcurrent` place until the composed
reply is sent, so external calls are bounded by the same concurrency cap. Each call's
deadline is the earlier of the client's deadline and the entry's `timeoutMs`, and a
client cancel stops the call in progress and any not yet started.

Clients keep gRPC's 4 MiB receive limit unless they raise it. A client that expects
large composed replies has to raise its own `maxInboundMessageSize`.

## Writing an adapter

1. Generate Java stubs from the parser's protos in your own module.
2. Implement `ExternalParserAdapter<Req, Rep>` from `tika-grpc-adapter-api`:
   `producerId`, `supportedTypes`, `method` (a unary `MethodDescriptor` from your
   stubs), `keyPrefix`, `validateOptions`, `buildRequest`, and `map`.
3. In `map`, build the result with `request.contribution()`: `addChild` for each
   child, `parentKey` for prefixed keys, `warn`, `partialFailure` or `fail`.
4. List the class in
   `META-INF/services/org.apache.tika.grpc.adapter.ExternalParserAdapter`.
5. Put the jar on tika-grpc's classpath and add
   `{"producerId": "...", "target": "host:port"}` under `grpc.externalParsers`.
   Channels use TLS by default; set `plaintext: true` to turn it off.

One adapter instance serves every call from several threads, so keep it stateless.
Use Tika's own key names for children where Tika has a parser for the format (a WARC
adapter writes `warc:<header>` and `warc:http:<header>` like Tika's WARCParser), so
the two producers' records read the same.

The FastWARC adapter is a complete example. It lives outside Tika, at
[ai-pipestream/tika#6](https://github.com/ai-pipestream/tika/pull/6), and needs
fastwarc-grpc 1.1.0 or later.

## Not covered

Streaming external methods (unary only until there is a measured need), FetchAndParse,
Tika's own embedded documents in `Document.embedded`, and replacing Tika's parse.

## Testing

```bash
mvn clean verify -pl tika-grpc-api,tika-grpc-mapper,tika-grpc-adapter-api,tika-grpc
```

The tika-grpc tests use a test-only adapter over a small `records.proto` in
`src/test/proto`, so nothing depends on FastWARC. They cover the merge rules, every
result status, limits and truncation, deadlines and cancel, startup checks, and a
`.warc.gz` composed through a real forked worker.

The FastWARC adapter was also run against fastwarc-grpc 1.1.0 with a 50-record WARC,
as `.warc`, `.warc.gz` and an unnamed `.warc.gz`. All 50 records came back matching
FastWARC's own output. With FastWARC stopped, Tika's Document came back intact with a
`STATUS_FAILED` slot, and the next call after a restart worked again.
