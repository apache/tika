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

# External parsers: user manual

How to run tika-grpc with external gRPC parsers, read what they return, and write
an adapter for a new one. For why it is built this way, see
[external-parser-adapters.md](external-parser-adapters.md) and
[architecture.md](architecture.md).

The feature is experimental and works only with `TikaV2Service.ParseBytes`.

## What you get

You send bytes to ParseBytes. Tika parses them as usual. Then every external parser
configured for the detected content type gets the same bytes, and what it found is
added to the reply:

- its **child documents** (for a WARC, one per record) in `Document.embedded`, each
  typed like Tika's own metadata;
- a few **parent keys under its own prefix** (`warc:record-count`) in
  `Document.extra`, only where Tika set no key of that name;
- one **result slot** in `Document.extensions` saying how it went.

Tika's own fields, status and origin are never changed by an external parser, and a
parser that fails never fails your request.

## Quick start: FastWARC

1. Run fastwarc-grpc **1.1.0 or later** next to tika-grpc, for example on port
   50060. Earlier versions use different field numbers and fail every call.
2. Put the FastWARC adapter jar on tika-grpc's classpath. The adapter is a separate
   project: [ai-pipestream/tika#6](https://github.com/ai-pipestream/tika/pull/6).
   `java -jar tika-grpc.jar`, which the Docker image's `start-tika-grpc.sh` uses,
   only sees the classpath in the jar's manifest, so a jar copied next to it is not
   loaded. Start the server with `-cp` instead:

   ```
   java -cp "tika-grpc.jar:tika-grpc/*:tika-grpc-adapter-fastwarc.jar:<its dependencies>" \
     org.apache.tika.pipes.grpc.TikaGrpcServer --config tika-config.json
   ```

   The published Docker image has no hook for extra jars yet; build an image whose
   start script uses `-cp` with the adapter jar added.
3. Add an entry to the `grpc` section of the tika-config:

   ```json
   {
     "grpc": {
       "externalParsers": [
         {"producerId": "fastwarc", "target": "localhost:50060", "plaintext": true}
       ]
     }
   }
   ```

4. Start tika-grpc. The log shows one line per parser:

   ```
   external parser 'fastwarc': fastwarc.v1.WarcService/ParseArchive at localhost:50060 (plaintext) for [application/warc, application/warc+gz]
   ```

5. Send a WARC to ParseBytes. A `.warc`, a `.warc.gz`, or a gzipped WARC with no
   name all route to FastWARC, because routing uses the type Tika detects from the
   bytes, not the file name.

What comes back, trimmed (values are illustrative):

```json
{
  "contentType": "application/warc+gz",
  "status": {"status": "STATUS_SUCCESS"},
  "extra": [{"key": "warc:record-count", "value": {"integers": {"values": ["50"]}}}],
  "extensions": [{
    "producerId": "fastwarc", "status": "STATUS_OK",
    "embeddedReported": "50", "embeddedKept": "50", "elapsedMs": "41"
  }],
  "embedded": [{
    "path": "/record-1", "producerId": "fastwarc",
    "contentType": "text/html; charset=UTF-8",
    "metadata": {"created": "2023-04-01T10:00:00Z"},
    "extra": [
      {"key": "warc:WARC-Type", "value": {"strings": {"values": ["response"]}}},
      {"key": "warc:WARC-Target-URI", "value": {"strings": {"values": ["https://example.org/"]}}},
      {"key": "warc:http:status", "value": {"strings": {"values": ["200"]}}}
    ],
    "status": "STATUS_SUCCESS"
  }]
}
```

FastWARC's records use the same keys as Tika's own WARCParser (`warc:<header>`,
`warc:http:<header>`), so a consumer reads both the same way.

## Configuration

Each entry under `grpc.externalParsers`:

| Key | Default | Meaning |
|---|---|---|
| `producerId` | required | which adapter jar to use; unique in the list |
| `target` | required | the parser's `host:port` |
| `plaintext` | `false` | connect without TLS |
| `tls.caFile` | JVM trust store | PEM file of a private CA to trust |
| `tls.certFile`, `tls.keyFile` | none | PEM client cert and key for mutual TLS |
| `timeoutMs` | 60000 | deadline per call; the earlier of this and the client's deadline applies |
| `maxReplyBytes` | 128 MiB | largest reply read from the parser |
| `maxContentBytes` | none | larger documents are not sent (slot `STATUS_SKIPPED`) |
| `maxChildren` | 100,000 | most child documents kept from one reply |
| `maxEmbeddedBytes` | 64 MiB | most bytes those children may add |
| `maxKeysPerDocument` | 256 | most metadata keys per child, and on the parent |
| `maxValueChars` | 8192 | values and paths are cut to this length |
| `keepRawReply` | `false` | also put the whole reply in the slot's `payload` as an `Any` |
| `options` | `{}` | the adapter's own settings, checked at startup |

FastWARC's `options`: `verifyDigests` and `quirksMode`, both booleans.

One setting covers all parsers together: `grpc.externalParsersMaxAddedBytes`
(96 MiB), the most all external parsers may add to one reply.

TLS is the default. An entry with neither `plaintext` nor `tls` uses TLS with the
JVM's trust store. `plaintext: true` and a `tls` block together are refused.

A production entry with mutual TLS:

```json
{"producerId": "fastwarc", "target": "fastwarc.internal:50060", "timeoutMs": 30000,
 "tls": {"caFile": "/etc/tika/ca.pem",
         "certFile": "/etc/tika/client.pem", "keyFile": "/etc/tika/client.key"},
 "options": {"verifyDigests": true}}
```

### Startup checks

The server refuses to start when:

- an entry's `producerId` has no adapter jar on the classpath, or two entries share
  one;
- an adapter's method is not unary, it names no content types, or its key prefix
  does not end in `:` or `-`;
- a prefix overlaps one of Tika's namespaces (`tk:`, `X-TIKA`, `dc:`, `dcterms:`,
  `Content-`, `pdf:`, ...) or another configured adapter's, ignoring case;
- the adapter rejects the entry's `options`;
- a TLS file is missing or unreadable, or a cert has no key.

An adapter jar on the classpath that no entry names is never used. If it fails to
load, the server logs it and starts anyway.

## Reading the result

### The slot

| `extensions[].status` | What happened | What was merged |
|---|---|---|
| `STATUS_OK` | the parser read everything | all of it |
| `STATUS_PARTIAL` | the parser stopped part way; `warnings[0]` says why | what it read before stopping |
| `STATUS_FAILED` | call failed, timed out, cancelled, reply over `maxReplyBytes`, or the adapter threw | nothing |
| `STATUS_SKIPPED` | document over `maxContentBytes` | nothing; not sent |

`warnings` holds up to 100 messages of up to 1000 characters. A failed slot's reason
is one line, at most 1024 characters.

### Truncation

When a limit cuts the child list, the slot says so:

```json
{"producerId": "fastwarc", "status": "STATUS_OK",
 "embeddedReported": "180000", "embeddedKept": "100000", "truncated": true,
 "warnings": ["80000 children past maxChildren (100000) not kept"]}
```

If `truncated` is true, `embedded` is not the whole archive. The children kept are
the first ones, in the parser's order.

### Child documents

Each `EmbeddedDocument` has a `path` unique for its `producer_id` (`/record-1`,
`/record-2`, ...), a content type, typed `metadata`, `extra`, and a status:
`STATUS_SUCCESS`, or `STATUS_PARTIAL` with `errors` when the parser reported a
problem with that one child.

Tika's own embedded documents are not in this list yet.

## Sizing

A ParseBytes request holds its `parseBytesMaxConcurrent` place until the reply is
sent, so at most that many requests hold, at once, their content, a reply of up to
`maxReplyBytes` per parser, and the composed Document. Size the heap for that.

With no client deadline, a request can take Tika's parse plus the `timeoutMs` of
every parser that takes its type. Parsers run one after another.

**Clients must raise their own receive limit.** gRPC clients accept 4 MiB by
default. A WARC with thousands of records produces a reply far larger than that, so
set `maxInboundMessageSize` on the client channel to what you expect back.

## Writing an adapter

An adapter jar holds three things: the parser's protos compiled to Java, one class
implementing `ExternalParserAdapter`, and a service file. Depend on
`org.apache.tika:tika-grpc-adapter-api`.

```java
public final class RecordListAdapter
        implements ExternalParserAdapter<ParseRequest, ParseResponse> {

    @Override public String producerId() { return "records"; }

    @Override public Set<MediaType> supportedTypes() {
        return Set.of(MediaType.parse("application/x-records"));
    }

    @Override public MethodDescriptor<ParseRequest, ParseResponse> method() {
        return RecordServiceGrpc.getParseMethod();   // from your generated stubs; unary
    }

    @Override public String keyPrefix() { return "rec:"; }

    @Override public void validateOptions(JsonNode options) {
        // throw IllegalArgumentException for an unknown key or bad value
    }

    @Override public ParseRequest buildRequest(AdapterRequest request) {
        return ParseRequest.newBuilder().setContent(request.content()).build();
    }

    @Override public AdapterContribution map(ParseResponse reply, AdapterRequest request) {
        AdapterContribution.Builder out = request.contribution();
        int i = 0;
        for (Record r : reply.getRecordsList()) {
            Metadata m = new Metadata();
            m.set(TikaCoreProperties.CREATED, r.getDate());
            m.set("rec:kind", r.getKind());
            out.addChild("/record-" + (++i), m);
        }
        out.reportedChildren(reply.getRecordsCount());
        out.parentKey("rec:count", Integer.toString(i));
        if (reply.hasFatalError()) {
            out.partialFailure(reply.getFatalError());
        }
        return out.build();
    }
}
```

Service file
`META-INF/services/org.apache.tika.grpc.adapter.ExternalParserAdapter`:

```
com.example.RecordListAdapter
```

The builder from `request.contribution()`:

| Call | Effect |
|---|---|
| `addChild(path, metadata)` | one child; path starts with `/` and is unique |
| `addChild(path, metadata, errors)` | a child with errors, reported as `STATUS_PARTIAL` |
| `parentKey(key, value)` | a parent key; must start with your prefix |
| `reportedChildren(n)` | how many the parser found, for the truncation count |
| `warn(message)` | a warning on your slot |
| `partialFailure(reason)` | keep what was built; slot `STATUS_PARTIAL` |
| `fail(reason)` | merge nothing; slot `STATUS_FAILED`; wins over `partialFailure` |

Rules to follow:

- **Be stateless.** One instance serves every call, from several threads.
- **Use Tika's keys.** Set Tika `Property` constants (`TikaCoreProperties.CREATED`,
  `DublinCore.TITLE`) so values come out typed, and use Tika's own key names where
  Tika already has a parser for the format.
- **Stay in your prefix on the parent.** Parent keys outside it are dropped with a
  warning. Children can carry any keys.
- **Don't build big things outside the builder.** The builder enforces the limits as
  you add; work done before it is not bounded.
- **Your code is trusted.** It runs in the server JVM. A hang or an
  `OutOfMemoryError` there affects the whole server.

To test without a running service, start an in-process gRPC server that implements
your parser's service, as tika-grpc's own tests do with a test-only adapter over
`src/test/proto/tika/test/v1/records.proto`.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Server won't start: no adapter for `producerId` | the jar is not on the classpath (started with `java -jar`?), or its service file is missing | start with `-cp` including the jar; check `META-INF/services` |
| Server won't start: prefix overlaps | two adapters, or an adapter and Tika, share a prefix | change one adapter's prefix |
| Slot `STATUS_FAILED`, `UNAVAILABLE ... Connection refused` | the parser is down or the target is wrong | start it or fix `target`; the channel reconnects by itself |
| Slot `STATUS_FAILED`, `Invalid protobuf byte sequence` | the parser's version doesn't match the adapter's protos | for FastWARC, run 1.1.0 or later |
| Slot `STATUS_FAILED`, `DEADLINE_EXCEEDED` | the parser took longer than `timeoutMs` or the client's deadline | raise `timeoutMs` or the client deadline |
| Slot `STATUS_FAILED`, reply over `maxReplyBytes` | a very large archive | raise `maxReplyBytes`, or set `maxContentBytes` to skip it |
| Client gets `RESOURCE_EXHAUSTED: message larger than max` | the client's 4 MiB receive limit | raise `maxInboundMessageSize` on the client |
| No slot at all | the detected type isn't one the adapter takes | check `content_type` in the reply; there is no filename fallback |
| `truncated: true` | `maxChildren`, `maxEmbeddedBytes` or `externalParsersMaxAddedBytes` cut the list | raise the limit that the warning names |

## Not supported yet

Streaming parser methods, external parsers on FetchAndParse, Tika's own embedded
documents in `Document.embedded`, and replacing Tika's parse with an external one.
