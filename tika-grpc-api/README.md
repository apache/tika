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
- PDF fields: `parse_response.pdf.*`
- Dublin Core: `parse_response.dublin_core.*`
- Creative Commons (alongside primary type): `parse_response.creative_commons.*`

## Lint

```bash
cd tika-grpc-api && buf lint
```
