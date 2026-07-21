# Apache Tika gRPC API

Typed protobuf messages for Tika parse output under `org.apache.tika.grpc.v1`.

## Contents

- **Document** (`document.proto`) — the single, small, stable parse-result contract:
  an envelope (content type, origin, parse status), typed common metadata
  (`DocumentMetadata`), and a tagged metadata tail (`extra`) that losslessly carries
  everything else.
- **Bundled descriptors** — `META-INF/org.apache.tika.grpc.v1.descriptors` in the
  published jar.

## Usage

```xml
<dependency>
  <groupId>org.apache.tika</groupId>
  <artifactId>tika-grpc-api</artifactId>
  <version>${tika.version}</version>
</dependency>
```

## The Document shape

Rather than one proto message per source format, `Document` models metadata by
*concern*, not by *format*:

- **`DocumentMetadata`** carries a small, bounded set of typed common fields — the
  Dublin Core descriptive core (title, authors, description, keywords, languages,
  publishers, identifiers, created/modified, rights). These are the cross-format
  facts every consumer wants, typed.
- **`extra`** (a `repeated MetadataField`) is the lossless tagged tail for everything
  else — PDF permissions, EXIF/GPS, OOXML core properties, custom keys. Every entry
  is a typed **array**, mirroring Tika's own `String[]`-backed metadata model: the
  tag comes from Tika's declared `Property` element type
  (integers/numbers/booleans/timestamps), so a declared integer sequence like
  `pdf:charsPerPage` arrives as int64s per page — and untyped keys stay strings,
  never guessed. A metadata key is data, not schema: new or renamed keys never force
  a client rebuild.
- **`ParseStatus`** carries the typed outcome (`SUCCESS`/`PARTIAL`/`FAILED`), the raw
  pipes status for diagnostics, timing, and the producing Tika version.

Format-specific mapping (which Tika `Property` becomes which typed field, and what
falls through to `extra`) lives in `tika-grpc-mapper`'s
`org.apache.tika.grpc.mapper.transform.DocumentTransformer` implementations — code,
not schema. Adding a parser means adding a transformer; the wire contract does not
change.

Planned follow-ups extend `Document` additively (proto3 field additions are
wire-compatible): a structured content tree, and recursion into embedded documents.
Field numbers for those are intentionally left unassigned in `document.proto`.

## Lint

```bash
cd tika-grpc-api && buf lint
```
