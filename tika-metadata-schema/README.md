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
# tika-metadata-schema

A machine-readable schema of Apache Tika's metadata keys, plus a registry-driven validator
(`MetadataKeyValidator`) that classifies any key as CLOSED / OPEN / TEMPLATE / UNKNOWN.

**Scope: `tika-core` + the standard parser bundle.** The heavier/optional parser families
(scientific, sqlite3, nlp, vlm) are *not* scanned — pulling their runtime deps (netcdf, grib,
opennlp, DL4J, sqlite-jdbc) into a build-time schema module isn't worth it. Their keys are the only
ones absent (e.g. `sqlite3:`, `vlm:`, `grib:`, `netcdf:`, `ctakes:`, `NER_`). `MetadataCoverageTest`
enforces this: any module declaring keys that is neither scanned nor on its explicit out-of-scope
list fails the build, so nothing escapes *silently*.

## `metadata-keys.json` — the closed set (generated + gated)
Every key Tika declares as a `Property` constant, plus the bounded digest cross-product
(`X-TIKA:digest:<ALGORITHM>[:<ENCODING>]`, enumerated from `DigestDef`). Each record:
`{ key, namespace, valueType, cardinality }`.

**Generated, never hand-edited.** `SchemaGenerator` scans the parser classpath for classes that
declare a `Property` field, force-loads them, reads the global `Property` table, and writes stable
sorted JSON. `MetadataSchemaTest` regenerates in-memory and asserts it matches the committed file, so
the registry can never drift from the declarations.

Regenerate after adding/changing a `Property` **or** a `PassthroughPrefix` (writes both files):
```
java -cp <tika-metadata-schema + deps classpath> \
     org.apache.tika.metadata.schema.SchemaGenerator \
     src/main/resources/org/apache/tika/metadata/metadata-keys.json \
     src/main/resources/org/apache/tika/metadata/metadata-open-namespaces.json
```

## `metadata-open-namespaces.json` — the open sets (generated + gated)
The **prefixes** under which parsers mint file-controlled key names at runtime — names that are not
`Property` constants, so the individual keys cannot be enumerated (scraped HTML `<meta>` under
`html:`, OOXML `custom:`, email `Message:Raw-Header:`, Access `MDB_PROP:`, Vorbis comments, FLV
attributes, unmapped image/XMP tags, …). Each record: `{ prefix, provenance, description }`.

**Generated from the `PassthroughPrefix` declarations, never hand-edited.** Every such prefix is a
registered `PassthroughPrefix` constant; `SchemaGenerator` reads that registry the same way it reads
the `Property` table, and `MetadataSchemaTest` gates it identically. Adding a passthrough prefix in a
parser and forgetting to regenerate fails the build.

Not covered here: **templates** — parameterized key families like XMP `rdf:Alt` language variants
`<base-key>:<lang>` (`dc:title:fr`), where the *suffix* rather than the prefix is open. These are
documented by rule, not enumerated.

## `metadata-string-keys.json` — legacy bare-String closed keys (curated + gated)
A handful of closed keys predate `Property` and are still declared as bare `String` constants
(`HttpHeaders.CONTENT_TYPE` = `Content-Type`, the `Content-*`/`Location` family, `Message-*` /
`Multipart-*`, `tika:chunks`). They self-register nowhere, so the `Property` scan can't see them —
yet Tika emits them constantly. Each record: `{ key, source }`.

**Curated, but gated against the code:** `MetadataStringKeysTest` reflects each `source` constant
(e.g. `HttpHeaders.CONTENT_TYPE`) and asserts its live value equals `key`, so a rename/retype/value
change fails the build. The right long-term fix is to make these `Property` constants (then they'd
move to `metadata-keys.json` automatically); that's a large, `Content-Type`-blast-radius change left
for a future major release.

## `MetadataKeyValidator` — the registry-driven lint
Classifies any key by reading the three registries above (no parser classes needed):
`CLOSED` (in `metadata-keys.json` or `metadata-string-keys.json`), `OPEN` (under a registered
passthrough prefix), `TEMPLATE` (a `<closed-key>:<lang>` lang-alt instance), or `UNKNOWN` — a typo,
an unregistered namespace, or a key nobody declared. This is the payoff the registries exist for: a
data-driven legitimacy check instead of a hand-coded regex.

Together the files describe the key space of the scanned bundle: closed keys are enumerated and
gated (Property-backed and legacy-String alike); open namespaces are enumerated by prefix and gated;
templates are described by rule; and `MetadataCoverageTest` guarantees no scanned-bundle module is
silently missed. Keys from the out-of-scope families above are excluded by design.
