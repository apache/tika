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

# ParseBytes (TIKA-4795)

How this fits with the rest of tika-grpc v2: [architecture.md](architecture.md).

`TikaV2Service.ParseBytes` parses bytes sent in the request and returns the same
`Document` as FetchAndParse (see [v2-document-contract.md](v2-document-contract.md)).
This page covers why it exists and how it behaves; the settings table is in
[the module README](../README.md#parsebytes).

## Why

A crawler has already downloaded the page. Asking Tika to fetch it again costs a
second request, and the second fetch can get something different: a redirect, a new
cookie, a login page, rotated content. ParseBytes parses exactly the bytes the caller
captured, so the parse and the crawl record describe the same thing.

## Request

| Field | Use |
|---|---|
| `content` | the bytes; required |
| `resource_name` | a file name, used as a detection hint and returned as `origin.filename` |
| `correlation_id` | returned unchanged as `Document.id` |
| `source_uri`, `effective_uri`, `base_uri` | the caller's URIs for the bytes, returned in `origin`; Tika never dereferences them |
| `truncated` | the caller cut the bytes short |
| `parse_context_json` | per-request parse settings, under the same `allowPerRequestConfig` rule as FetchAndParse |

The proto change is additive: the RPC, its request and response, and four new
`SourceOrigin` fields (5 to 8).

## How the bytes reach the worker

Parsing happens in the forked pipes workers, as for FetchAndParse. Content up to
`pipes.maxInlineBytes` goes to the worker with the request, the way tika-server
passes a request body. Larger content is written to a temporary file and read by a
file-system fetcher the server registers under a reserved id starting with `__`. The
fetcher RPCs treat `__` ids as unknown and `SaveFetcher` refuses them, so a client
cannot see or replace it. Since TIKA-5001 step 1 that fetcher comes from the config
file and never enters the config store. The file is deleted when the parse ends.

ParseBytes never emits.

## Limits and memory

ParseBytes is the obvious way to push a lot of memory into the server, so it is
bounded at each step.

| Setting | Default | What it bounds |
|---|---|---|
| `parseBytesMaxContentBytes` | 256 MiB | the `content` field; over it is `RESOURCE_EXHAUSTED` |
| `maxInboundMessageBytes` | content limit + 1 MiB | the whole gRPC message, applied by gRPC before Tika sees it |
| `parseBytesMaxConcurrent` | 2 x `pipes.numClients` | requests in progress; one more is `RESOURCE_EXHAUSTED` before its bytes are spooled |

The 256 MiB default is there because web-crawl WARCs of 100 MB and more are normal.
A request keeps its place under `parseBytesMaxConcurrent` until its reply is sent,
so hashing and fallback detection are bounded too. With the defaults, up to 8 requests
of 256 MiB can be held at once, in heap and in the spool directory. The server warns
at startup when the heap is below that product or three times the inbound limit, or
when the spool directory has less free space. On a small host, lower the concurrency
or the content limit.

Empty content is `INVALID_ARGUMENT`. A spool write that fails (disk full) is
`RESOURCE_EXHAUSTED`. Busy pool, startup failures and cancel behave as in v2
FetchAndParse.

## What ParseBytes adds because it holds the bytes

**Digest and type on failure.** When a parse fails before recording a digest or a
content type, FetchAndParse has nothing to report. ParseBytes has the bytes, so it
computes `origin.sha256` itself (always lowercase hex SHA-256) and detects the type
from magic bytes and the resource name. For gzip it looks inside the stream, so a
gzipped WARC is `application/warc+gz` with or without a name. That uses tika-core's
`GZipSpecializationDetector`, which this change limits to inflating 64 bytes of a
1 KiB prefix. Container detection is skipped, because this runs in the server JVM,
not in a worker. Whatever the parse did record always wins.

**Truncation.** A PDF cut off before its trailer parses "successfully": PDFBox
rebuilds it without an exception and the document info is lost. Tika cannot see that;
the crawler that cut the bytes can. With `truncated=true`, a `STATUS_SUCCESS` becomes
`STATUS_PARTIAL` and every status gets an error saying the content was truncated.
`pipes_status` keeps the raw value. Without the flag nothing changes.

## Not covered

A cancelled call lets the running parse finish and drops the result, the same as
FetchAndParse. Stopping the parse inside the worker without restarting the JVM needs
a PipesClient change and is not part of this work.

## Testing

```bash
mvn clean verify -pl tika-grpc-api,tika-grpc-mapper,tika-grpc
```

The tests cover parity with FetchAndParse on the same file, the inline and spooled
paths, the size and concurrency limits, cancel, the fallback digest and type
(including `.warc.gz`), truncation, and the reserved fetcher id.

It has also been run from a crawler: [tika-demo](https://github.com/dpol1/tika-demo)
runs a StormCrawler 3.7 crawl whose parse bolt sends every fetched body to
ParseBytes. It checks one GET per URL, the client's SHA-256 matching `origin.sha256`,
and Tika's fields reaching the crawl's indexer.
