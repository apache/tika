# ParseResponse extensions

Core mapping in `ParseResponseMapper` focuses on Tika `Metadata` → strongly typed
`org.apache.tika.grpc.v1.ParseResponse` fields (~1,330+ mapped properties).

## Outline and structure decorators (planned)

Document outlines (PDF bookmarks, HTML/Markdown heading trees, section char offsets) are
**not** part of the core mapper today. They will be added as **`ParseResponseDecorator`**
implementations in a separate extension (or additional Maven module) so the base mapper
stays free of PDFBox/HTML-specific outline code.

Reference implementations exist in pipestream-parser:

| Decorator (planned) | Source reference | Needs source bytes? |
|---------------------|------------------|---------------------|
| PDF outline | `PdfOutlineExtractor` | Yes (PDF bytes) |
| HTML outline | `HtmlOutlineExtractor` | Yes (HTML bytes) |
| Markdown outline | `MarkdownExtractor` | Yes (MD bytes) |
| Section offsets | `SectionOffsetResolver` | Body text only |
| EPUB TOC | Already on `EpubMetadata.table_of_contents` via `EpubStructureExtractor` | Yes (EPUB bytes) |

## Hook API

```java
ParseMapContext ctx = ParseMapContext.withSourceBytes(
        primary, metadataList, body, docId, documentBytes);

ParseResponse response = ParseResponseMapper.map(
        ctx, "OK", parseTimeMs, List.of(new PdfOutlineDecorator(), new HtmlOutlineDecorator()));
```

When source bytes are unavailable (default gRPC pipes path), decorators should no-op.
Pass `ParseMapContext.of(...)` from `TikaGrpcServerImpl` today; wire bytes when fetchers
expose them or when decorating in-process parse paths.

## Proto evolution

When outline messages are added to `tika-grpc-api`, decorators will populate new fields
on `ParseResponse` or nested format messages. Until then, overflow can use
`MetadataEntry` or format `additional_metadata` Struct fields.
