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

A machine-readable schema of Apache Tika's metadata keys. Two registries, because Tika has two
kinds of keys:

## `metadata-keys.json` — the closed set (generated + gated)
Every key Tika declares as a `Property` constant, plus the bounded digest cross-product
(`X-TIKA:digest:<ALGORITHM>[:<ENCODING>]`, enumerated from `DigestDef`). Each record:
`{ key, namespace, valueType, cardinality }`.

**Generated, never hand-edited.** `SchemaGenerator` scans the parser classpath for classes that
declare a `Property` field, force-loads them, reads the global `Property` table, and writes stable
sorted JSON. `MetadataSchemaTest` regenerates in-memory and asserts it matches the committed file, so
the registry can never drift from the declarations.

Regenerate after adding/changing a `Property`:
```
java -cp <tika-metadata-schema + deps classpath> \
     org.apache.tika.metadata.schema.SchemaGenerator \
     src/main/resources/org/apache/tika/metadata/metadata-keys.json
```

## `metadata-open-namespaces.json` — the open sets (curated)
Keys minted at **runtime** whose names are not `Property` constants, so they cannot be generated:
- **open/passthrough namespaces** — file-controlled key names (scraped HTML `<meta>` under `html:`,
  OOXML `custom:`, email `Message:Raw-Header:`, Access `MDB_PROP:`, Vorbis comments, GRIB/NetCDF/FLV
  attributes, …);
- **templates** — e.g. XMP `rdf:Alt` language variants `<base-key>:<lang>` (`dc:title:fr`).

**Curated (hand-maintained, reviewed), not generated**, and possibly not exhaustive; the
closed-namespace lint (a follow-up) is the intended completeness backstop.

Together the two files describe the whole key space: closed keys are enumerated and gated; open keys
are described by rule.
