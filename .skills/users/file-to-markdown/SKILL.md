---
name: file-to-markdown
description: >
  Turn almost any file into Markdown plus metadata — PDF, Office, HTML,
  email, archives, images, audio/video, 1000+ formats — powered by Apache
  Tika, either via
  the tika-app CLI (zero setup, one file) or a running tika-server (curl,
  warm process, many calls). Leads with rmeta (structured, embedded-item-aware
  output) as the default operation rather than flat concatenated text, since
  you can't tell whether a file has embedded content from its extension.
  Covers metadata-only triage, type and language detection, OCR, and
  output-size discipline for agent context. Use whenever a task involves
  reading the content of a file whose format you don't want to hand-parse.
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

Local override: `$TIKA_SKILLS_LOCAL/file-to-markdown/LOCAL.md` (default `~/.tika-skills`),
read after this file, wins on conflict.

# Using Apache Tika from an agent

Apache Tika turns almost any document into text you can read, and reports
per-document content as **Markdown** by default (4.x) — the format you
actually want, not raw XML or a wall of HTML tags. See below for why to
reach for the structured `rmeta` view rather than a flat blob, even when the
file looks simple.

This skill is for reading files, one at a time, inside your normal working
loop. It is not a batch pipeline — see **Batch processing**, below, for why.

**When NOT to reach for Tika:** if your host can already read the file
natively (many agent environments read PDFs and images directly) and you
only need its visible text, use that — it's one step, not three. Tika earns
its place for everything else: `.docx`/`.xlsx`/`.pptx`, email (`.eml`,
`.msg`) and its attachments, archives, embedded content inside anything,
metadata (authors, dates, edit history), OCR, and the long tail of ~1000
formats nothing else opens.

**Requirements:** Tika 4.x needs **Java 17+** (`java -version` to check).
No Java 17? In order of least pain:

1. **Docker installed?** Use the Docker route — zero local Java (see the
   `file-to-markdown-docker` companion skill, or
   `docker run apache/tika:latest-full`).
2. **No Docker either? Offer to install a user-local Java — ask the user
   first; never install software silently.** The least invasive option is a
   Temurin JRE unpacked into a directory the user owns: ~45 MB, no admin
   rights, no PATH changes, uninstall = delete the directory.

   ```bash
   mkdir -p ~/tika-jre && cd ~/tika-jre
   curl -L "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jre/hotspot/normal/eclipse" | tar xz
   ~/tika-jre/*/bin/java -jar tika-app.jar --version
   ```

   Swap `linux`/`x64` in the URL for `mac`|`windows` and `aarch64` as
   needed (Windows: fetch the `.zip` variant and unzip). If the user prefers
   a managed install, the system package works too (`temurin-21-jre` via
   apt, `brew install --cask temurin@21`, `winget install
   EclipseAdoptium.Temurin.21.JRE`) — that needs admin rights and touches
   system state, so it's their call, not the default.
This skill describes **Tika 4.x**; on a 3.x install the defaults differ
(3.x outputs XHTML, not Markdown, and some flags changed) — check
`java -jar tika-app.jar --version` or `curl localhost:9998/version` if
behavior doesn't match what's described here.

## Which surface: tika-app or tika-server?

First gate: **no Java 17 on this machine?** Then this choice is moot — use
the Docker route (`file-to-markdown-docker` skill), or offer the user-local
JRE install (Requirements, above). With Java 17+, two ways to reach Tika —
pick based on how many files you expect to touch in this session.

- **`tika-app` (CLI)** — zero setup, no process to manage, one file per
  invocation. Each call pays a JVM startup cost (roughly a second). Use this
  for "read this one file" or a handful of files.
- **`tika-server` (curl)** — a warm process you either already have running
  (ask, or check `curl -s localhost:9998/version`) or start yourself. No
  per-call JVM cost once it's up. Use this if you're about to read many files
  in the same session, or a server is already available.

If unsure and doing more than two or three files, start a server:

```bash
java -jar tika-server-standard-<version>.jar &
# starts on localhost:9998
```

**Getting either one:** download the **zip** distribution from
https://tika.apache.org/download.html and run the jar from inside the
unzipped directory. Do NOT grab just the jar from Maven Central: 4.x jars
are thin launchers that need the `lib/` directory sitting next to them, and
fail with `NoClassDefFoundError` on their own. If a jar you've been pointed
at already works, it's inside a proper distribution — leave it where it is.

Both surfaces are driven by the same parsing engine — output is identical
either way for the same handler/format choice.

## The core operation: parse one file with `rmeta`

Default to **`-J` (tika-app) / `/rmeta` (tika-server)**, not plain `/tika`.

```bash
java -jar tika-app.jar -J document.pdf           # JSON array, markdown content per entry
curl -T document.pdf http://localhost:9998/rmeta  # same shape
```

Result is a JSON array: entry 0 is the document itself, entries 1+ are
anything embedded in it (attachments, embedded images/objects, archive
members — if any). Read entry 0's `tk:content` field for the common
single-document case (note the `tk:` prefix — a plain `.content` lookup
returns nothing).

**Why rmeta and not plain `/tika`, even for an ordinary-looking file:** you
cannot tell whether a file has embedded content from its extension or format
— a `.pdf` can carry attachments, a `.docx` can carry embedded objects, a
`.png` can carry XMP-embedded sidecar data, and even a file you're sure is
"flat" you can't actually confirm without parsing it. Plain `/tika` doesn't
avoid this — it still recursively parses and includes any embedded text,
just concatenated into one blob with no boundaries, no per-item metadata,
and no visibility into whether an individual embedded item failed. `rmeta`
is the *same parse*, structured, at no real extra cost. Reach for plain
`/tika`/`-t` deliberately (see below), not as the default.

`-J` combines with `-x`/`-h`/`-t`/`-m` to pick the content format used inside
each entry (default markdown); the server's equivalent is `/rmeta/<handler>`
(`/rmeta/text`, `/rmeta/html`, ...).

To pull embedded items out as actual files (not just their extracted text):
`/unpack` on the server, or on tika-app `-z`/`--extract` (direct
attachments, depth 1) or `-Z` (recursive, all depths), with
`--extract-dir=<dir>` for the destination. This is not
format-specific to office documents — it works on anything Tika can find
embedded content in: email attachments (`.eml`, `.msg`), archive members
(`.zip`), PDF attachments, embedded objects/images in any office format
(an inline pasted picture counts), and so on.

What lands on disk: a `<name>-embed/` directory of the embedded files
**renumbered** (`00000001.jpg`, ...) — original names are not preserved on
disk; they're in the rmeta output's `tk:resource-name` per entry, so keep
the sibling `<name>.json` (an rmeta-shaped metadata dump `-z` also writes)
if you need to map numbers back to names.

`-z`/`-Z` route through Tika Pipes mode rather than the fast synchronous
path the other flags use — expect several seconds and a burst of
plugin/forked-JVM startup logging on stderr; that's normal, not a hang or
an error.

Read the extracted content selectively (grep, or Read with offset/limit)
rather than dumping it into your context wholesale — see **Output
discipline** below.

## Flat concatenated text — when you deliberately don't need structure

If you specifically want one blob of body text and don't care about
per-document boundaries, embedded-item metadata, or per-item exception
visibility — a quick keyword search, a rough skim — plain output is cheaper:

```bash
java -jar tika-app.jar document.pdf > document.md   # markdown is the default
curl -T document.pdf http://localhost:9998/tika > document.md
# note: the HTTP response Content-Type header says text/plain even when the
# body is Markdown — the endpoint you called, not the header, tells you the format
```

This still includes embedded document text (see above) — it's just flattened
in. And its damage behavior is the strongest reason to prefer `rmeta`: a
*fatal* container exception gets a `422` here (partial content, no detail),
but a **recoverable** parse problem — the common case for a damaged or
truncated file — returns a plain `200` with **silently truncated content
and no signal at all**: no status change, no header, nothing in the body.
`rmeta` on the same file returns `200` too, but *shows* the damage: a
`tk:exception:warn` (or `tk:exception:container-exception`) entry with the
stack trace, and possibly fewer array entries than the intact file would
produce. Status codes cannot be relied on to detect damage — inspecting
`tk:exception:*` keys in rmeta output is the detection mechanism.

## Metadata only (fast triage before committing to full content)

Cheaper than a full parse when you just need to know what a file is —
author, dates, page count, content-type — before deciding whether to read it.

```bash
java -jar tika-app.jar -j document.pdf          # JSON metadata, no content
curl -T document.pdf http://localhost:9998/meta  # JSON by default
```

This doesn't tell you whether the file has embedded content — `/meta`/`-j`
covers only the container document's own metadata. Some formats surface a
hint (Office's `msoffice:has-comments`/`has-track-changes`), but the only
reliable way to know is `rmeta`'s array length:
`java -jar tika-app.jar -J file | jq 'length'` — `1` means no embedded
items; each entry past the first is one embedded item (its
`tk:embedded-resource-type` says how it's embedded, e.g. `INLINE` for a
pasted-in image vs `ATTACHMENT`).

## Detection: what kind of file is this, without parsing it

```bash
java -jar tika-app.jar -d document              # prints the media type
curl -T document http://localhost:9998/detect     # text/plain media type

java -jar tika-app.jar -l document.pdf           # language only
curl -T document.pdf http://localhost:9998/language
```

`/detect` and `/language` work even on files with no extension or a
misleading one — detection is content-based.

## OCR (scanned PDFs, images, screenshots)

Text-layer parsing does nothing for a scanned page or a photo of text — you
need OCR, which requires Tesseract to be present. `tika-app`/local
`tika-server` only OCR if Tesseract is installed on the host; there is no
bundled fallback.

**Guaranteed OCR, no local install:** run the `-full` tika-server Docker
image, which bundles Tesseract, GDAL, and fonts:

```bash
docker run -d -p 127.0.0.1:9998:9998 apache/tika:<version>-full
curl -T scanned.pdf http://localhost:9998/tika    # OCR runs automatically
```

If a parse of an image-heavy PDF comes back suspiciously short, that's the
signal you're missing OCR, not that the file has no text. For mount/path
setup, confirming OCR actually ran, and other Docker specifics, see the
`file-to-markdown-docker` skill.

## Output discipline — don't flood your own context

The most common mistake using Tika from inside an agent loop: piping a large
document's full Markdown/JSON straight into context. A single PDF can produce
megabytes of output. Instead:

1. **Redirect to a file, not a variable or inline output.** `> extracted.json`,
   then read what you need with offset/limit or grep — don't capture full
   stdout into your working context by default.
2. **Triage with metadata or detection first** (above) when you're deciding
   *whether* to read a file, not just what's in it.
3. **`rmeta`/`-J` output is compact single-line JSON — use `jq`, not grep, to
   isolate one entry** (e.g. `jq '.[0]."tk:content"'`); a bare grep can find a
   string but can't tell you which array entry it came from. Add
   `-r`/`--pretty-print` first if you want it grep-friendly instead. A file
   with many embedded items (a large `.eml`, a nested archive) can produce a
   long array either way.
4. **Ask for `text`/`txt` as the per-entry handler** (`/rmeta/text`,
   `-J -t`) when you only need body text, not formatting — smaller output,
   same information for most downstream uses (search, keyword extraction,
   summarization prompts you control the framing of).

## Batch processing — not this skill

If the task is "parse thousands of files," stop reaching for per-call
tika-app/curl inside your loop — that's the wrong shape (slow, and each
result would flood your context in turn). Use tika-app's Tika Pipes mode
instead, which is designed for it and writes results to an output directory
(or a configured emitter) rather than back to you:

```bash
java -jar tika-app.jar -i /path/to/input -o /path/to/output
# one JSON-array (rmeta-shaped) output file per input document;
# add --handler m --content-only for bare .md files instead
```

This runs out-of-band; check the output directory or configured emitter for
results rather than expecting them in your context. Configuring fetchers,
emitters, and worker count is beyond this skill's scope — see the Tika Pipes
documentation at https://tika.apache.org/docs if you need to set this up.

## Error handling (tika-server)

- **`429`** — the server's worker pool is saturated, not broken. Back off and
  retry (`Retry-After` header tells you how long).
- **`503`** with `TIMEOUT`/`OOM`/`UNSPECIFIED_CRASH` — that specific parse
  failed (the file may be hostile or malformed); the server itself is fine.
  Retrying the same file will likely fail the same way — move on rather than
  loop.
- **`422`** on the raw endpoints (`/tika`, `/tika/text`, etc.) — a *fatal*
  container exception; partial content is still in the body. Recoverable
  parse problems do NOT get a 422 — they return `200` with silently
  truncated content (see the flat-text section above); only `/rmeta` reveals
  those, via `tk:exception:*`.
- **`400`** — malformed request: an unrecognized handler name on
  `/rmeta/<handler>` (the message lists the valid set), or an unknown
  fetcher/emitter on `/pipes`. Fix the request; retrying unchanged won't
  help. **Exception:** a wrong handler under `/tika/...` returns a bare
  `404`, because `/tika` has only four literal handler routes.
- Handler names differ per family: `/rmeta/<handler>` accepts `text`, `txt`,
  `html`, `xml`, `body`, `markdown`, `md`, `ignore`; `/tika/<handler>` is
  only `text`, `html`, `xml`, `md` (plus `json`). `/tika/markdown` is a 404
  even though `/rmeta/markdown` works.
- **`429`/`503`** (below) are documented behavior you can't easily reproduce
  with clean small files — take them on faith until you hit them under load.

## Trust note

Tika parses untrusted files safely when it runs in a forked/isolated process
— which `tika-server` and `tika-app`'s `-f`/`--fork` mode both do. Calling
the library directly in-process on a file you don't trust has no such
protection: a hostile file can exhaust memory/CPU or crash the process. If
you're not sure a file is safe, use tika-server or `-f`, not an embedded
parser call.
