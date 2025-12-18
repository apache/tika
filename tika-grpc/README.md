# Tika Pipes GRPC Server

The following is the Tika Pipes GRPC Server.

This server will manage a pool of Tika Pipes clients.

* Tika Pipes Fetcher CRUD operations
    * Create
    * Read
    * Update
    * Delete
* Fetch + Parse a given Fetch Item

## Distributed Configuration with Apache Ignite

The gRPC server supports distributed configuration storage using Apache Ignite, enabling multiple Tika servers to share fetcher and emitter configurations across a cluster.

### Configuration

To enable Ignite-based distributed configuration, set the `configStoreType` in your Tika configuration file:

```json
{
  "pipes": {
    "configStoreType": "ignite"
  },
  "fetchers": [
    {
      "id": "my-fetcher",
      "name": "file-system",
      "params": {
        "basePath": "/data/input"
      }
    }
  ]
}
```

### ConfigStore Types

The following `configStoreType` values are supported:

- `memory` (default) - In-memory storage, configurations not shared across servers
- `ignite` - Apache Ignite distributed cache, configurations shared across cluster
- `<fully.qualified.ClassName>` - Custom ConfigStore implementation

### Maven Dependency

To use Ignite ConfigStore, include the dependency in your project:

```xml
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-ignite-config-store</artifactId>
    <version>4.0.0-SNAPSHOT</version>
</dependency>
```

### Running with Ignite

```bash
java -jar tika-grpc-4.0.0-SNAPSHOT.jar -c tika-config-ignite.json -p 50052
```

When multiple Tika gRPC servers start with `configStoreType: "ignite"`, they will:
1. Form an Ignite cluster automatically
2. Share all fetcher/emitter configurations
3. Allow fetchers created on one server to be used on all servers in the cluster

See the [Ignite ConfigStore README](../tika-pipes/tika-ignite-config-store/README.md) for more details on configuration options.

