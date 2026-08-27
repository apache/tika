---
name: file-to-markdown-docker
description: >
  Run Apache Tika as a Docker container when you need guaranteed OCR (scanned
  PDFs, images) or geospatial raster support with zero local install —
  `apache/tika:<version>-full` bundles Tesseract, GDAL, ImageMagick, and
  fonts. Also covers the minimal image, port/volume/memory conventions, the
  path-identity mount gotcha, and how to confirm OCR actually ran rather than
  silently returning no text. Powered by Apache Tika. Use when a local `tika-app`/`tika-server`
  doesn't have Tesseract installed, or you want a disposable, self-contained
  parsing environment. Companion to the `file-to-markdown` skill, which covers the
  parsing calls themselves once a server is up.
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

Local override: `$TIKA_SKILLS_LOCAL/file-to-markdown-docker/LOCAL.md` (default `~/.tika-skills`),
read after this file, wins on conflict.

# Running Apache Tika via Docker

Two images on Docker Hub: `apache/tika` (REST server, port 9998) and
`apache/tika-grpc` (gRPC, port 9090). This skill covers `apache/tika`; the
`file-to-markdown` companion skill covers the parsing calls in more depth once the
container is up (same HTTP API either way), but the examples below are
enough to parse on their own.

The Docker route needs **no local Java at all** — the container brings its
own. If the host lacks Java 17+ (Tika 4.x's requirement), this is the
easiest path, not just the OCR path. This skill describes Tika 4.x images;
on a 3.x image the default output is XHTML, not Markdown.

## Minimal vs `-full` — the choice that matters

```
apache/tika:<version>        # JRE + tika-server-standard, pure-Java parsers only
apache/tika:<version>-full   # + Tesseract OCR, GDAL, ImageMagick, font sets
```

**Take `-full` if the task involves OCR (scanned PDFs, photos of documents,
image-only PDFs) or geospatial rasters — otherwise take minimal.** A scanned
PDF parsed against the minimal image doesn't error; it just silently returns
little or no text, because there's no Tesseract to run. If output looks
suspiciously short for a document that's clearly a scan, that's the signal
you're on the wrong image, not that the file has no text — see **Confirming
OCR actually ran**, below.

Tag forms: `<version>` rolls forward on rebuild of the same release,
`<version>-<N>` is immutable (pin this in anything long-lived), `latest`
tracks newest stable. Published for `linux/amd64`, `linux/arm64`,
`linux/s390x` — `docker pull`/`docker run` picks the right one automatically.

## Starting it

```bash
docker run -d -p 127.0.0.1:9998:9998 apache/tika:latest-full
# or pin a real version: apache/tika:4.0.0-full
curl -T document.pdf http://localhost:9998/tika
```

**Bind to `127.0.0.1`, not `0.0.0.0` or a bare port mapping, unless you
specifically mean to expose it.** Docker writes its own iptables rules, so
`-p 9998:9998` (no host part) can publish the server past your host
firewall onto the network — a real, easy-to-hit surprise, not a theoretical
one. Tika parses untrusted input by design; the server itself does no
authentication, so treat network exposure as a deliberate decision, not a
default.

Give it a few seconds to start before the first request — `curl -sf
http://localhost:9998/version` is a simple readiness check to poll in a
script.

## Mounting files: get the path identity right

Plain `curl -T` uploads need **no mount at all** — the document travels in
the HTTP body. Mounts matter when the container must read paths itself: a
`-c` config file, pipes fetchers reading from a directory, extra jars. The
path-identity rule below applies to **directories of documents that callers
reference by path**; a single config or jar mounted at a fixed container
path (`-v .../my.json:/my.json:ro`) is fine and normal — nothing translates
those paths back and forth. When a caller (an agent, a script) passes
filesystem paths, mount the directory at the **same absolute path inside
the container** that the caller uses outside it:

```bash
docker run -d -p 127.0.0.1:9998:9998 \
  -v "/home/me/project:/home/me/project:ro" \
  apache/tika:<version>-full
```

Don't remap to something like `/data` — if you do, every path the caller
passes needs translating before Tika can see it, and every response
(embedded-file paths from `/unpack`, resource names) needs translating back.
Matching the host path exactly makes the container a transparent stand-in
for a locally-installed Tika: paths just work in both directions. `:ro` is
worth defaulting to — Tika only needs to read the input, and a read-only
mount is a real containment boundary if a parse goes wrong, on top of (not
instead of) the process isolation Tika's own forked workers already give
you.

The container runs as a **non-root user, UID/GID `35002:35002`**. Mounted
input files must be readable, and any directory Tika writes to must be
writable, by that UID — a mount that's `0600` owned by your host user will
fail inside the container even though it works fine outside it.

## Confirming OCR actually ran

Don't assume the `-full` image means OCR fired on a given file — confirm it,
especially the first time you stand one up.

**For PDFs**, `pdf:ocr-page-count` is a verified, code-confirmed signal —
non-zero means Tesseract processed that many pages:

```bash
curl -T scanned.pdf http://localhost:9998/rmeta | jq '.[0]."pdf:ocr-page-count"'
```

Secondary evidence: the same entry's `tk:parsed-by-full-set` lists
`org.apache.tika.parser.ocr.TesseractOCRParser` when Tesseract ran.

`0` or `null` means OCR didn't run on this PDF — check you're on `-full` (not
minimal), that the PDF is actually image-only (a PDF with a real text layer
correctly skips OCR — that's not a bug), and that no mounted config sets
`skipOcr: true` (see below). If the key exists but under a **camelCase name**
(`pdf:ocrPageCount`) — or you see `X-TIKA:*` keys — your image predates the
4.0.0 metadata-key renames; upgrade the image rather than adapting to the old
spellings.

**For standalone images** (not embedded in a PDF), there's no single
verified flag confirmed here — this skill doesn't have a code-checked answer
for that case yet. The pragmatic fallback: compare extracted-text length
against a file you know is a genuine scan; suspiciously empty output on a
visibly text-bearing image is the same "wrong image" signal as above.

## Configuration (turning OCR off, or anything else)

Mount a `tika-config.json` and point `-c` at it — anything after the image
name is appended to the entry point, which already sets `-h 0.0.0.0` (don't
pass `-h` again):

```bash
docker run -d -p 127.0.0.1:9998:9998 \
  -v "$(pwd)/tika-config.json:/tika-config.json" \
  apache/tika:<version>-full -c /tika-config.json
```

To keep `-full`'s other parsers but disable OCR specifically (e.g. you only
wanted GDAL):

```json
{
  "parsers": [
    { "default-parser": {} },
    { "tesseract-ocr-parser": { "skipOcr": true } }
  ]
}
```

## Memory

Size the container's `--memory` limit; don't pass `-Xmx` — the JVM sizes its
heap from the container's own limit. Tika Pipes forks additional JVMs
*inside* the same container for isolation, and each fork's heap comes out of
that same limit, so size for the forks, not just the parent:

```bash
docker run -d -p 127.0.0.1:9998:9998 --memory 4g apache/tika:<version>-full
```

## Disposable / one-shot use

For a short-lived session, name the container so you can stop it
deterministically — backgrounding `docker run` does NOT stop the container
when your script exits, and a leaked container keeps port 9998 occupied for
your next attempt:

```bash
docker run -d --rm --name tika-tmp -p 127.0.0.1:9998:9998 apache/tika:latest-full
until curl -sf http://localhost:9998/version >/dev/null; do sleep 1; done
curl -T document.pdf http://localhost:9998/tika
docker stop tika-tmp   # --rm removes it on stop
docker ps --filter name=tika-tmp   # verify: should list nothing
```

For many parses in a session, start it once and reuse it — the JVM warm-up
cost is worth paying only once, not per file.

## Troubleshooting

- **`500` with an empty body** from `/tika` or `/rmeta`: the response tells
  you nothing — go straight to `docker logs <container>`. A log line like
  `Unknown fetcher type: 'file-system-fetcher' ... Available types: []` plus
  a pf4j `No 'plugins' root` warning means the server can't find its plugins
  directory — a symptom of an outdated or hand-built image whose working
  directory isn't the distribution root. Official 4.0.0+ images set the
  working directory correctly; upgrade the image (workaround for a broken
  one: add `--workdir /opt/tika-server`).
- **Old metadata key spellings** (`pdf:ocrPageCount`, `X-TIKA:*`): image
  predates 4.0.0 — upgrade (see the OCR section).
- **Connection refused right after start**: the JVM is still booting; poll
  `curl -sf http://localhost:9998/version` rather than sleeping a fixed time.
- **Permission denied reading a mounted file**: the container runs as UID
  35002 — see the mount section.

## What's not built yet

There is no `apache/tika-app` image and no MCP mode for the Tika Docker
images as of this writing — the container always runs `tika-server`. If a
task specifically wants a stdio-spoken tool (MCP) rather than an HTTP server,
that isn't available through Docker today; use the `file-to-markdown` skill's
tika-app path directly on the host instead.
