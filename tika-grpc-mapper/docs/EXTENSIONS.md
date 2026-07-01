# Document extensions

Core mapping lives in `org.apache.tika.grpc.mapper.DocumentBuilder`, which delegates
metadata mapping to `org.apache.tika.grpc.mapper.transform.DocumentTransformer`
implementations (one per format concern) and content-tree building to
`org.apache.tika.grpc.mapper.content.MarkdownBlockTreeBuilder` (format-agnostic, since
every Tika parser's output reaches this module as markdown).

## Adding a new format

Adding support for a new format means adding a `DocumentTransformer`, not touching the
wire contract:

1. Implement `DocumentTransformer`: `appliesTo(Metadata)` decides whether this
   transformer applies (usually a `Metadata.CONTENT_TYPE` check; cross-cutting
   transformers like `CreativeCommonsDocumentTransformer` may inspect the full
   metadata instead); `transform(Metadata, Document.Builder)` maps the common,
   cross-format facts into `DocumentMetadata` via `TransformSupport`, then calls
   `MetadataTagger.appendTail(...)` so every remaining Tika key lands in the tagged
   tail, typed where Tika declares the type and string otherwise.
2. Register it in `DocumentTransformers.defaults()`.
3. Add a test that parses a real fixture (via the module's existing
   `tika-parser-*-module` test-jar dependencies) and asserts on the fields that
   actually populate.

`PdfDocumentTransformer` is the reference implementation for this pattern.

## Document outlines / structure

PDF bookmarks, HTML/Markdown heading trees, and section boundaries are largely already
captured by `Document.blocks` (a `Heading` block carries its `level`, so a heading tree
is just a filter over `blocks`). A dedicated outline extraction pass (e.g. PDF
bookmarks, which are metadata rather than content) is not yet implemented; it would be
a `DocumentTransformer` that populates `DocumentMetadata` or the tagged tail from
PDFBox's outline API, not a proto change.

## Pluggable, format-unknown output

For output that does not fit the typed shape at all (e.g. a document-layout model's
own tree), the plan is a pluggable external-parser mechanism carrying opaque
`google.protobuf.Any` payloads on the `Document` — a follow-up change, so that this
contract never has to model a plugin's output shape. Do not add per-format proto
messages here.
