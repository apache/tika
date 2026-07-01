# Apache Tika gRPC API

Typed protobuf messages for Tika parse output under `org.apache.tika.grpc.v1`.

## Contents

- **Document** (`document.proto`) — the single, small, stable parse-result contract: a
  structured markdown content tree (`blocks`/`markdown`), typed common metadata
  (`DocumentMetadata`), and a tagged metadata tail (`extra`) for everything
  format-specific.
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

Rather than one proto message per source format, `Document` models content and
metadata by *concern*, not by *format*:

- **Content** lives in `markdown` (the authoritative render) and `blocks` (the same
  content parsed into a structured tree of headings/paragraphs/lists/tables/code
  blocks/inline runs). This is format-agnostic: every Tika parser's output reaches
  this shape the same way, via markdown.
- **Metadata** has a small, bounded set of typed common fields on `DocumentMetadata`
  (title, authors, description, keywords, languages, dates, counts, dimensions,
  rights) plus a tagged tail (`extra`, a `repeated MetadataField`) for everything
  format-specific. Tail values are typed where Tika's own `Property` declares a type
  (integer/number/boolean/timestamp), and a string otherwise — never guessed.
- **`format_category`** is a cheap routing hint (`FormatCategory` enum: PDF, OFFICE,
  IMAGE, HTML, RTF, EPUB, WARC, GENERIC). It is not mutually exclusive with anything
  in `extra` — a document can be `FORMAT_CATEGORY_PDF` and still carry Creative
  Commons rights metadata, for example.
- **`embedded`** recurses: a PDF with an embedded image is one `Document` whose
  `embedded` list contains a fully-typed child `Document` for the image.

Format-specific mapping (which Tika `Property` becomes which typed field, and what
falls through to `extra`) lives in `tika-grpc-mapper`'s
`org.apache.tika.grpc.mapper.transform.DocumentTransformer` implementations, one per
format — code, not schema. Adding a parser means adding a transformer; the wire
contract does not change.

## Lint

```bash
cd tika-grpc-api && buf lint
```
