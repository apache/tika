# Apache Tika gRPC API

Protobuf messages and gRPC service stubs for Tika parse results, in the Java package
`org.apache.tika.grpc.v2`. Every class of that package lives in this module.

This is the experimental v2 contract. The v1 `tika.Tika` service, which returns
metadata as a `fields` map of strings, is defined in the `tika-grpc` module.

## Contents

- **Document** (`document.proto`): the parse result. It holds the detected content
  type, the origin of the bytes, the parse status, common metadata as typed fields
  (`DocumentMetadata`) and all other metadata as key/value entries (`extra`). It does
  not include the extracted text or embedded documents.
- **TikaV2 service** (`tika_v2.proto`): the v2 FetchAndParse RPCs, which return a
  `Document`, with generated stubs. The server implementation is in `tika-grpc`.
- **Descriptors**: the jar contains `META-INF/org.apache.tika.grpc.v2.descriptors`.

## Usage

```xml
<dependency>
  <groupId>org.apache.tika</groupId>
  <artifactId>tika-grpc-api</artifactId>
  <version>${tika.version}</version>
</dependency>
```

## The Document shape

`Document` uses the same fields for every source format:

- **`DocumentMetadata`**: typed fields for the Dublin Core properties most formats
  share: title, authors, description, keywords, languages, publishers, identifiers,
  created and modified dates, rights.
- **`extra`** (`repeated MetadataField`): every other metadata key, such as PDF
  permissions, EXIF/GPS or OOXML core properties. Each entry is an array, because a
  Tika metadata key can hold several values. The value type follows the `Property`
  type Tika declares for the key (integer, number, boolean or timestamp); keys
  without a declared type are strings. For example, `pdf:charsPerPage` arrives as
  one int64 per page. New or renamed keys need no change to the proto.
- **`ParseStatus`**: the outcome (`SUCCESS`, `PARTIAL` or `FAILED`), the raw Tika
  Pipes status, the parse time and the Tika version.

`tika-grpc-mapper` decides which Tika property becomes which typed field, and what
goes to `extra`, in its `org.apache.tika.grpc.mapper.transform.DocumentTransformer`
implementations. Mapping a format's properties to typed fields means adding a
transformer; the proto stays the same.

## Lint

```bash
cd tika-grpc-api && buf lint
```
