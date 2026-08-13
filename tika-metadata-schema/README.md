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

**Scope: `tika-core` + the standard parser bundle + the extended/optional families
(scientific, sqlite3, nlp, vlm; TIKA-4816 stage 5a).** All are on the (test-scope, for the
heavier ones) classpath, so `sqlite3:`, `vlm:`, `grib:`, `netcdf:`, `envi:`, `ctakes:`, `ner:`
and the `grobid:tei:*` keys are all in the registry. `MetadataCoverageTest` enforces completeness: any
module declaring keys that is neither scanned nor on its explicit out-of-scope list fails the
build, so nothing escapes *silently*. `OUT_OF_SCOPE` is currently empty; it stays as the documented
mechanism for excluding a future heavy/optional family.

## `metadata-keys.json` — the closed set (generated + gated)
Every key Tika declares as a `Property` constant, plus the bounded digest cross-product
(`tk:digest:<ALGORITHM>[:<ENCODING>]`, enumerated from `DigestDef`). Each record:
`{ key, namespace, valueType, cardinality, module }`.

**Generated, never hand-edited.** `SchemaGenerator` scans the parser classpath for classes that
declare a `Property` field, force-loads them, reads the global `Property` table, and writes stable
sorted JSON. `MetadataSchemaTest` regenerates in-memory and asserts it matches the committed file, so
the registry can never drift from the declarations.

Regenerate after adding/changing a `Property` **or** a `KeyPrefix` (writes all three files):
```
tika-metadata-schema/regen.sh
```
Installs the dependency modules, regenerates the registries via the forked-exec profile, sanity-checks
the key-count diff, and runs the gate tests — see `.skills/metadata-schema.md` for flags and the
manual steps this replaces.

## `metadata-open-namespaces.json` — the open sets (generated + gated)
The **prefixes** under which parsers mint file-controlled key names at runtime — names that are not
`Property` constants, so the individual keys cannot be enumerated (scraped HTML `<meta>` under
`html:`, OOXML `custom:`, email `message:raw-header:`, Access `mdb-prop:`, Vorbis comments, FLV
attributes, unmapped image/XMP tags, …). Each record: `{ prefix, provenance, description, module }`.

**Generated from the `KeyPrefix` declarations, never hand-edited.** Every such prefix is a
registered `KeyPrefix` constant; `SchemaGenerator` reads that registry the same way it reads
the `Property` table, and `MetadataSchemaTest` gates it identically. Adding a passthrough prefix in a
parser and forgetting to regenerate fails the build.

Not covered here: **templates** — parameterized key families like XMP `rdf:Alt` language variants
`<base-key>:<lang>` (`dc:title:fr`), where the *suffix* rather than the prefix is open. These are
documented by rule, not enumerated.

## `MetadataKeyValidator` — the registry-driven lint
Classifies any key by reading the two registries above (no parser classes needed):
`CLOSED` (in `metadata-keys.json`), `OPEN` (under a registered
passthrough prefix), `TEMPLATE` (a `<closed-key>:<lang>` lang-alt instance), or `UNKNOWN` — a typo,
an unregistered namespace, or a key nobody declared. This is the payoff the registries exist for: a
data-driven legitimacy check instead of a hand-coded regex.

Together the files describe the key space of the scanned bundle: closed keys are enumerated and
gated; open namespaces are enumerated by prefix and gated;
templates are described by rule; and `MetadataCoverageTest` guarantees no scanned-bundle module is
silently missed. Keys from the out-of-scope families above are excluded by design.
