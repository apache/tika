# tika-grpc-api

Protobuf definitions and generated Java stubs for the experimental
`org.apache.tika.grpc.v2alpha1.TikaService`, served by `tika-grpc` next to the v1 service. Depend
on this artifact to build a client without pulling in the server.

The package may change incompatibly until it is renamed to `org.apache.tika.grpc.v2`. The
generated `FileDescriptorSet` is in `META-INF/org.apache.tika.grpc.v2alpha1.descriptors` for
clients that work from descriptors.

User documentation, covering the reply shape, type hints, errors and client message size, is the
"v2alpha1 service" section of the Tika gRPC page in the Tika docs
(`docs/modules/ROOT/pages/using-tika/grpc/index.adoc`).

Lint and breaking-change checks:

```bash
cd tika-grpc-api && buf lint
```
