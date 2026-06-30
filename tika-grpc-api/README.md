# Apache Tika gRPC API

Typed protobuf messages for Tika parse output under `org.apache.tika.grpc.v1`.

## Contents

- **ParseResponse** — top-level parse result replacing the legacy `map<string,string>` metadata bag
- Format-specific metadata messages (PDF, Office, Image, Email, …)
- **Bundled descriptors** — `META-INF/org.apache.tika.grpc.v1.descriptors` in the published jar

## Usage

```xml
<dependency>
  <groupId>org.apache.tika</groupId>
  <artifactId>tika-grpc-api</artifactId>
  <version>${tika.version}</version>
</dependency>
```

## Breaking change (4.x gRPC)

`FetchAndParseReply.fields` (`map<string,string>`) was removed. Clients must read
`FetchAndParseReply.parse_response` (`org.apache.tika.grpc.v1.ParseResponse`):

- Extracted text: `parse_response.content.body`
- Document title: `parse_response.content.title`
- **MIME type:** `parse_response.content_type` (canonical; from Tika `Content-Type`)
- **Format category:** `parse_response.primary_format` (`DocumentFormatCategory`; mirrors mapper routing)
- PDF fields: `parse_response.pdf.*`
- Dublin Core: `parse_response.dublin_core.*`
- Creative Commons (alongside primary type): `parse_response.creative_commons.*`
- Any other Tika key: `parse_response.metadata` (`MetadataEntry` with `text` or `text_list`)

### Field precedence

| Need | Read |
|------|------|
| Detected MIME | `parse_response.content_type` |
| Coarse format (routing) | `parse_response.primary_format` (`DocumentFormatCategory` enum) |
| Every Tika key (lossless) | `parse_response.metadata` |
| Known PDF/Office/etc. fields | `parse_response.pdf`, `.office`, … |
| Dublin Core | `parse_response.dublin_core` |

The format submessages (`pdf`, `office`, `image`, …) are independent `optional`
fields, not a `oneof` — a document may populate more than one (e.g. a PDF with
embedded image/EXIF metadata), matching Tika's multi-namespace model.
`primary_format` names the detected primary type.

There is exactly one catch-all: `parse_response.metadata`. The format messages
carry no `google.protobuf.Struct` "additional metadata" — every key Tika emits is
in `metadata`, typed and multivalue-preserving. Per-format `content_type` fields
are not populated when the envelope field is set.

## Lint

```bash
cd tika-grpc-api && buf lint
```
