---
name: file-forensics
description: >
  Examine what a file claims about itself and what it actually contains —
  powered by Apache Tika. True content-based type detection (extensions lie),
  provenance claims (authors, dates, creating application), revision and
  tamper signals (PDF incremental updates, tracked changes, hidden slides,
  zip integrity), hidden and embedded content (attachments, macros),
  risk indicators (PDF JavaScript actions, encryption), and content digests.
  Evidence gathering, not verdicts: Tika reports what the file asserts and
  what parsing observed; it does not attribute authorship or validate
  signatures. Use for triaging suspicious files, provenance questions,
  e-discovery-style review, or "is this file what it claims to be."
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

Local override: `$TIKA_SKILLS_LOCAL/file-forensics/LOCAL.md` (default `~/.tika-skills`),
read after this file, wins on conflict.

# File forensics with Apache Tika

**What this can and cannot tell you.** Tika cannot tell you who wrote a
file. It can tell you — exhaustively — what the file *claims* about itself
and what it actually *contains*, and those claims are the evidence: a
`dc:creator` value is an assertion some software recorded, not an identity;
a template-default author, an inconsistency between claimed dates, or a
conspicuously *missing* field is as much a finding as a present one. Report
what the file says; let the human draw conclusions.

**The one rule for reading Tika's output:** the key prefix tells you *who is
asserting each fact*.

- **`tk:*`** — Tika's own parse-time observations, which the file cannot
  forge into place: magic-byte-detected type, embedded-item structure,
  encryption status, digests of the actual bytes.
- **Everything else** (`dc:`, `pdf:docinfo:`, `extended-properties:`,
  `xmp:`, ...) — the file's claims about itself, recorded by whatever
  software touched it, editable by anyone with a hex editor.

Disagreement between the two classes is where findings live.

**And one rule with no exceptions: no date in a file is an observation.**
Creation, modification, and print timestamps — including the per-revision
timestamps inside PDF incremental updates — are written by software under
the control of whoever produced the file, and can be set to anything. Tika
can observe that a revision layer *exists*, how large it is, and what it
*contains*; when it was written is only ever claimed. Report timestamps as
"the file records...", never "this happened at...".

Setup (getting tika-app or a tika-server) is covered by the
`file-to-markdown` companion skill; this skill assumes one is available and
uses the same invocation shapes. **Parse suspect files in isolation — this
is not optional for forensics work.** tika-server and `tika-app -f` both
parse in crash-isolated forked processes; for hostile-file triage prefer the
Docker route with the input mounted read-only (see `file-to-markdown-docker`)
so the parse can neither modify the evidence nor touch anything else.

The one-command forensics rig — the stock Tika image started with this
skill's config (which IS the unlock on the server surface: none of these
switches are on by default there):

```bash
docker run -d --rm --name tika-forensics -p 127.0.0.1:9998:9998 \
  -v "$(pwd)/file-forensics-config.json:/file-forensics-config.json:ro" \
  apache/tika:latest-full -c /file-forensics-config.json
curl -T suspect.file http://localhost:9998/rmeta > suspect.rmeta.json
docker stop tika-forensics   # when done
```

Guaranteed OCR, read-only config, process isolation, explicit named
configuration — in one command.

## Workflow: capture once, then converse over the JSON

This skill is **not** "run Tika and read the output into context." It's two
phases:

**Phase 1 — capture** (once per file): parse to disk with a digest,
**extract every embedded file to disk**, and make a compact metadata-only
view for the conversation:

```bash
java -jar tika-app.jar --config=file-forensics-config.json -J suspect.file > suspect.rmeta.json
java -jar tika-app.jar --config=file-forensics-config.json -Z --extract-dir=evidence/suspect-embedded suspect.file
# (--digest=sha256 is only needed if you are NOT using the config;
#  the config's built-in digester already covers it)
# or, against the docker rig from the isolation section above:
# curl -T suspect.file http://localhost:9998/rmeta > suspect.rmeta.json
# curl -T suspect.file http://localhost:9998/unpack > suspect-embedded.zip
# mkdir -p evidence/suspect-embedded && unzip -q suspect-embedded.zip -d evidence/suspect-embedded

jq 'map(del(."tk:content"))' suspect.rmeta.json > suspect.meta.json
ls -lR evidence/suspect-embedded && sha256sum evidence/suspect-embedded/*/* 2>/dev/null || sha256sum evidence/suspect-embedded/*
```

`suspect.rmeta.json` is the full evidence record — a JSON array where entry
0 is the file and entries 1+ are everything embedded in it, content
included. `suspect.meta.json` is the same array with the (possibly huge)
extracted text stripped out: small enough to inspect freely.
`evidence/suspect-embedded/` holds **the literal embedded files** —
attachments, images, macro source, each prior PDF revision as an openable
PDF — and the examiner needs those, not descriptions of them. Extraction
is part of capture, not a follow-up; tell the human where the directory is
and what's in it as soon as it exists.

**Phase 2 — investigate conversationally.** Each question the user asks
becomes a targeted query against the saved files, and only the answering
values enter the conversation:

```bash
jq 'length' suspect.meta.json                          # how many embedded items?
jq '.[0] | keys' suspect.meta.json                      # what fields exist at all?
jq '.[].["tk:embedded-resource-type"]' suspect.meta.json
jq '.[0]."pdf:incremental-update-count"' suspect.meta.json
grep -o 'Normal.dotm' suspect.meta.json                 # quick claim checks
jq '.[2]."tk:content"' suspect.rmeta.json               # content of ONE entry,
                                                        # only when asked
```

Two queries worth running early on any file:

```bash
# inventory: one line per entry -- index, depth, type, name, content-type
jq -r 'to_entries[] | [.key, .value."tk:embedded-depth", .value."tk:embedded-resource-type",
       .value."tk:resource-name", .value."Content-Type"] | @tsv' suspect.meta.json
# every key that appears anywhere in the array -- what there is to ask about
jq '[.[] | keys[]] | unique' suspect.meta.json
```

**When the human wants to see everything**, they get three things: the
extracted-files directory (open the images, the attachments, the prior
revisions — with whatever tools they normally use), and both JSON files:
`suspect.rmeta.json` is the complete record, content included;
`suspect.meta.json` is the same thing without the text, which is what you
want when the text runs to megabytes and drowns the metadata in a viewer.
Point them at whatever is already on the machine:

- `jq . suspect.rmeta.json | less` — pretty-printed, pageable, no install
  (`python3 -m json.tool` if `jq` is missing)
- `jq '.[0]' suspect.meta.json` — the container entry's metadata alone, the
  usual first look
- Open it in a browser: Firefox and Chrome render `.json` files as a
  collapsible tree with search
- `jless`, `fx`, or `visidata` if installed (see "Showing the human the
  evidence" below)

The size caution applies to the *agent's* context, not the human's screen.
Never load the full rmeta JSON into context — a document with a large text
body or many attachments makes it enormous, and the conversation only ever
needs slices. The saved files also make the session auditable: the evidence
the answers came from is on disk, unchanged, re-queryable.

## The forensics config: turn on what default parsing leaves off

This skill ships `file-forensics-config.json` (in this skill's directory):
one config for every surface, and the named, explicit parse configuration
for investigation work. (Its `"server": {}` element is required for
tika-server's `-c` and harmlessly ignored by tika-app; don't remove it.) Use it on
every surface, every time — an examination should be able to state exactly
what configuration produced its output, and "whatever that tool defaults
to" is not that. It also makes results identical across surfaces, because
the defaults are NOT the same everywhere:

**What it changes per surface:**

- **tika-server, pipes mode, or the library:** the config is the difference
  between seeing revision history/macros and not — none of these switches
  are on there.
- **tika-app single-file mode:** the CLI quietly applies its own convenience
  config (it prints a banner saying so) that already enables the marquee
  items — `parseIncrementalUpdates`, `extractMacros`, `extractActions`,
  `extractInlineImages`. On tika-app, `file-forensics-config.json` still adds
  `extractFontNames`, `extractScripts`, `includeMissingRows`,
  deleted/moved-content for legacy `.doc` (on `.docx` the convenience
  config already includes it, so testing this switch on a docx shows no
  diff — that's expected), and pins `accessCheckMode` to
  `DONT_CHECK` (tika-app's convenience config sets a more restrictive
  mode). A bare `tika-app -J` will already show PDF revisions and macros —
  the config's job there is making that explicit and reproducible rather
  than unlocking it.

The full switch list:

- `pdf-parser.parseIncrementalUpdates` — each prior revision of an
  incrementally-updated PDF is parsed as its own embedded document, so you
  can read what the file said *before* the last save(s)
- `pdf-parser.extractActions` — full detail of automatic actions/JavaScript
- `pdf-parser.extractFontNames` — font inventory (a provenance fingerprint:
  fonts betray the producing toolchain)
- `pdf-parser.extractInlineImages` — every image drawn in the PDF becomes
  an embedded entry: extractable to disk with `-Z`, OCR-able, and part of the
  inventory. (Repeated images are deduplicated by default —
  `extractUniqueInlineImagesOnly` — which is usually what you want.)
- Office (`office-parser` + `ooxml-parser` — set on both;
  4.x config is per-component): `extractMacros` (macro code as MACRO-typed
  embedded entries), `includeDeletedContent` and `includeMoveFromContent`
  (tracked-change deletions and moved-away text in the output),
  `includeMissingRows` (spreadsheet row gaps)
- `jsoup-parser.extractScripts` — script bodies in HTML (including HTML
  email bodies and HTML attachments) become their own `MACRO`-typed embedded
  entries instead of being silently dropped: JavaScript in a document is
  something a reviewer should see. Note they do NOT appear in the page's own
  `tk:content`; look for the extra entries (and the extracted files)

Some defaults are already forensics-friendly and are deliberately NOT
changed: overlapping/duplicate text is kept
(`suppressDuplicateOverlappingText: false` — text hidden under other text
stays visible), annotation/AcroForm/bookmark text is extracted, and
`accessCheckMode: DONT_CHECK` means Tika extracts regardless of the file's
claimed copy/extract restrictions while still *recording* those claims
under `access-permission:*` — the restriction flags are themselves
evidence.

```bash
java -jar tika-app.jar --config=file-forensics-config.json -J suspect.file > suspect.rmeta.json
```

The config also configures a SHA-256 digester, so `tk:digest:SHA-256`
appears on every entry on **every** surface — no per-command flag to forget.

If a parser name doesn't bind on your build, `--list-parser-names` prints
the registered names. Expect bigger output and slower parses than default
config — that's the point.

## Identity: is it what it claims to be?

```bash
java -jar tika-app.jar -d suspect.file    # content-based media type, extension ignored
```

**Anchor on `tk:content-type-magic-detected`** — what the leading bytes
said, computed by Tika from content alone. The top-level `Content-Type` is
the *routing* type, and on tika-server a caller-supplied `Content-Type`
header influences it by design — so on the HTTP surface that field can
reflect upstream input, accidental or adversarial, not just Tika's own
conclusion. An extension or routed type that disagrees with
`tk:content-type-magic-detected` is a classic finding (a "pdf" that is a
zip; an "xlsx" that is plain-text CSV). Caller-supplied types are tracked
under `tk:content-type-hint`, `tk:content-type-override`, and
`tk:content-type-parser-override` (which of these appears depends on how
the type was supplied) — all input, none evidence.

## Provenance claims

The file's own story about its origin — read it as testimony, not fact:

- `dc:creator` and related Dublin Core fields — claimed author(s)
- `pdf:docinfo:creator-tool`, `pdf:docinfo:producer` — what claims to have
  made the PDF (e.g. a phishing PDF "authored" by a word processor that
  doesn't match its claimed corporate origin)
- `extended-properties:*` (Office) — creating application/version, template
  (`Normal.dotm` = stock Word), total edit time, claimed created/modified
  timestamps
- **`tk:orig-resource-name`** — for xlsx, the absolute path where the file
  was last saved (Excel records it in the workbook): drive letters, network
  shares, and `C:\Users\<name>\...` usernames leak here. (The `tk:` prefix
  here means Tika surfaced it; the *value* is still the file's own record —
  treat it as a claim like the rest of this section.)

Findings look like: dates that precede the claimed creating tool's release;
edit time of 0 on a "carefully drafted" document; a template name from an
organization other than the claimed author's; identical creator strings
across supposedly independent documents.

## Revision and tamper signals

- **`pdf:incremental-update-count`** (emitted by default in 4.x) — a PDF
  above 0 has been modified after its initial write; each increment is a
  save layered on top of the previous file. Signed-then-modified PDFs show
  here. `pdf:eof-offsets` lists the byte offset of each revision's end.
- **Reading the prior versions themselves:** with the forensics config's
  `parseIncrementalUpdates` on, each earlier revision appears as its own
  entry in the rmeta array, typed `tk:embedded-resource-type: VERSION`, with
  `pdf:incremental-update-number` giving its place in history — **0-indexed
  from the earliest**; the final state is entry 0 of the array and carries
  no update number. Each VERSION entry is the complete file as it existed at
  that save (the bytes up to that revision's EOF offset), so its `tk:content`
  is what the document said *then* — diff a version's content against entry
  0's to see what changed between saves. The layer structure and content
  differences are observations; any timestamps inside each revision are that
  revision's own claims — order is established by the byte layering, timing
  is not. (Note: the final entry has no
  `pdf:incremental-update-number` key *at all* — `jq` prints `null` for a
  missing key and a null value alike, so "null on entry 0" is expected, not
  an error.)
- `tk:version-count` / `tk:version-number` — the format-general spelling of
  the same idea (also 0-indexed earliest-first; the latest version carries
  none). PDFs emit `tk:version-count` alongside the pdf-specific key; other
  formats that retain prior versions may adopt it, but not all parsers emit
  these yet.
- `msoffice:has-track-changes`, `msoffice:has-comments` — content the
  author may not have meant to ship. Parse output includes tracked-change
  and comment text; a "deleted" tracked change is still in the file.
- `extended-properties:HiddenSlides` — slides present but not shown.
- `zip:*` integrity family (zip-based formats): `zip:integrity-check-result`,
  `zip:duplicate-entry-names`, `zip:local-header-only-entries`,
  `zip:central-directory-only-entries` — structural anomalies associated
  with crafted or tampered archives (a duplicate entry name can make two
  tools see two different "same" files).

## Hidden and embedded content

The rmeta array *is* the embedded-content inventory: every entry past index
0 is something inside the file — attachments, embedded objects, images,
archive members, prior VERSIONs — each with its own metadata,
`tk:embedded-resource-type` (`INLINE`, `ATTACHMENT`, `MACRO`, `METADATA`,
`THUMBNAIL`, `VERSION`, ...), `tk:embedded-depth`, and path.

**Show the human the literal files.** The metadata inventory is for the
agent; the extracted bytes are for the person. Phase 1 already extracted
everything to `evidence/suspect-embedded/` (`-Z --extract-dir=...`, or
`/unpack` on the server) so they can open the images, hand an attachment
to another tool, or open a prior PDF revision side-by-side with the final.
If that step was skipped, do it now — an examination without the extracted
files is incomplete.

- With the forensics config, this includes **macro source** (MACRO entries)
  and **each prior PDF revision as a standalone, openable PDF** (VERSION
  entries).
- **Read code from the extracted files, not from `tk:content`.** The default
  content handler is Markdown, which escapes underscores and other
  characters — VBA/script source read via `jq '.[N]."tk:content"'` comes
  back visually corrupted (`VB\_Name`). The `-z`/`-Z` extracted files are
  pristine bytes. (Alternative: capture with `-J -t` for unescaped plain
  text per entry.)
- Extracted files are **renumbered** (`00000001.jpg`, ...); original names
  live in each rmeta entry's `tk:resource-name`, and `-z`/`-Z` writes a
  sidecar `<name>.json` metadata dump for mapping numbers back to names.
- Digest what you extracted (`sha256sum evidence/suspect-embedded/*/*`) so
  each artifact is pinned the same way the container file is.
- Mechanics and gotchas (Pipes-mode delay, chatty stderr) are in
  `file-to-markdown`; the server-side equivalent is `/unpack`, which returns
  the embedded files as a zip over HTTP. **`/unpack` names differ from
  `-z`/`-Z`:** plain sequential names (`1.jpg`, `2.pdf`, ...) and **no
  sidecar JSON** — map names back via each rmeta entry's `tk:resource-name`
  yourself. Against the server started above (forensics config loaded):

  ```bash
  curl -T suspect.file http://localhost:9998/unpack > suspect-embedded.zip
  mkdir -p evidence/suspect-embedded && unzip -q suspect-embedded.zip -d evidence/suspect-embedded
  sha256sum evidence/suspect-embedded/*
  ```

  `/unpack/all` also includes the container's own text and metadata. Both
  are `PUT`; `POST multipart/form-data` takes a per-request `config` part.

**Macros:** Office macro code is surfaced as embedded entries typed `MACRO`,
but only when macro extraction is enabled — it is **off by default**;
`file-forensics-config.json` (above) turns it on. Read the extracted VBA as
text. Presence of macros is a data point, not a verdict —
plenty of legitimate spreadsheets have them.

## Risk indicators

- `pdf:action-triggers`, `pdf:action-types`, `pdf:js-name` — automatic
  actions and JavaScript wired into a PDF (open actions are a common
  malicious-document mechanism, and also used legitimately by forms). With
  the forensics config's `extractActions`, **each JavaScript body is its
  own embedded entry**: `tk:embedded-resource-type: MACRO`, `Content-Type:
  text/javascript`, `pdf:action-trigger` naming what fires it
  (`PAGE_OPEN`, ...), and the code in `tk:content` — and `-Z` writes it to
  disk as a file. A one-page PDF with no text and a `PAGE_OPEN` script is
  the classic shape; `demo/invoice.pdf` is one.
- `pdf:has-xfa`, `pdf:has-acro-form-fields` — active form machinery
- `tk:encrypted` — the file (or an embedded item) is encrypted; content
  Tika couldn't read is content nobody scanned
- `tk:exception:*` — parse failures, per embedded item; a file that
  crashes parsers is itself a signal worth recording

## Signatures — presence, not validity

`pdf:has-signature-fields` and the `tk:signature:*` family (`name`, `date`,
`reason`, `location`, `contact-info`, `filter`) report that signature
structures exist and what they claim. **Tika does not cryptographically
validate signatures** — a well-formed `tk:signature:name` proves only that
signature metadata is present, not that it verifies, and
`pdf:incremental-update-count` > 0 alongside a signature means the file
changed after some revision was signed. Use a signature-validation tool for
validity; use Tika to know there's something to validate.

## Digests: pin down what you examined

```bash
java -jar tika-app.jar --digest=sha256 -j suspect.file
# adds tk:digest:SHA-256 to the metadata
```

Record the digest alongside findings so they're tied to exact bytes —
malware family lookups, dedup across an evidence set, and "is this the same
file I looked at yesterday" all start here. (`md5`, `sha1`, `sha384`,
`sha512` also available; SHA3 requires configuring the BouncyCastle digester
in a JSON config.)

## Demo files

The `demo/` directory in this skill ships six small real files with real
findings — a PDF with revision history, a macro-bearing Word document, a docx with
embedded content, an xlsx whose metadata records the absolute path where
it was last saved, a text-free PDF with a page-open JavaScript action, and
an HTML page with an embedded script — plus a README of suggested questions. They're
for showing a human what this skill does on files where the default parse
looks unremarkable, and for smoke-testing your setup end-to-end.

## Showing the human the evidence

Terminal `jq` quotes are fine for single answers; for "walk me through what
you found," give the human something browsable:

- If an interactive JSON viewer is installed, use it: `jless
  suspect.meta.json` or `fx`, or `visidata` (the rmeta array is naturally
  tabular — rows are entries, columns are keys).
- **Better: generate a report.** (Run the embedded-file extraction first if
  the report will link to extracted items.) Write a self-contained static
  HTML file next to the evidence — summary and digests up top, a claims-vs-observations
  table per entry, a collapsible `<details>` section per embedded item,
  relative links to the extracted files — and open it in the browser. No
  server, no dependencies, everything embedded (a page that phones out is
  not an evidence artifact). Regenerate it as the investigation deepens; it
  doubles as the deliverable the human keeps.
- If your agent host supports publishing pages/artifacts, that can make the
  report nicely viewable — **but read this first: publishing uploads the
  evidence to an external service.** Document content, metadata, extracted
  file names and paths leave the machine; anything shared can be forwarded,
  cached, or indexed beyond your control, and "deleted" rarely means gone.
  For anything sensitive — client documents, case material, unreleased
  content, anything under privilege or NDA — keep the report as a local
  HTML file and do not publish it. Publish only when the human has
  explicitly confirmed the content is safe to leave the machine.

## Evidence discipline

- Work on a copy; mount read-only in Docker. Tika doesn't modify inputs,
  but the discipline should not depend on that.
- Save the full rmeta JSON per file and record the exact command and Tika
  version (`--version`) with it — findings should be reproducible.
- Report inconsistencies as inconsistencies ("claimed creation 2019, claimed
  creator tool released 2021"), not conclusions ("forged").
- This is content analysis, not chain-of-custody forensics: no write
  blocking, no acquisition hashing, no court-grade custody trail. For
  matters likely to face legal scrutiny, treat Tika as the analysis layer
  inside a proper forensic process, not the process.
