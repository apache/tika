# Apache Tika Pipes - Ignite ConfigStore Plugin

This plugin provides distributed configuration storage using Apache Ignite for Tika Pipes.

## Features

- Distributed configuration storage across multiple Tika servers
- Support for REPLICATED and PARTITIONED cache modes
- Automatic cluster discovery and coordination
- Thread-safe configuration updates

## Configuration

### Using Factory Name

```json
{
  "pipes": {
    "configStoreType": "ignite",
    "configStoreParams": "{\"cacheName\":\"my-tika-cache\",\"cacheMode\":\"REPLICATED\",\"igniteInstanceName\":\"MyTikaCluster\"}"
  }
}
```

### Configuration Parameters

- **cacheName** (optional): Name of the Ignite cache. Default: `tika-config-cache`
- **cacheMode** (optional): Either `REPLICATED` or `PARTITIONED`. Default: `REPLICATED`
- **igniteInstanceName** (optional): Name for the Ignite instance. Default: `TikaIgnite`
- **autoClose** (optional): Whether to automatically close Ignite on shutdown. Default: `true`

### Cache Modes

- **REPLICATED**: All nodes have a full copy of the data. Best for small datasets that need fast reads.
- **PARTITIONED**: Data is distributed across nodes. Better for large datasets and scalability.

## Important Security Note

⚠️ **H2 Database Dependency**

Apache Ignite 2.16.0 requires H2 database version 1.4.197, which contains known security vulnerabilities (CVEs). 
This is a hard requirement due to Ignite's internal SQL engine dependencies - newer H2 2.x versions are incompatible.

**Risk Mitigation:**
- The H2 database is used internally by Ignite and is NOT exposed externally
- The ConfigStore only stores Tika pipeline configuration, not user data
- H2 is embedded and does not accept external connections
- No user SQL queries are executed against H2

**Alternatives for Security-Sensitive Environments:**
- Use the in-memory ConfigStore for single-node deployments
- Consider migrating to Apache Ignite with Calcite SQL engine (available since Ignite 2.13+)
- Wait for Apache Ignite 3.x which removes the H2 dependency entirely

If you have strict security requirements, we recommend evaluating these alternatives or implementing 
additional network isolation to ensure the Ignite cluster is not accessible from untrusted networks.

## Usage Example

```java
// Configuration is automatically loaded from tika-config.json
// The Ignite cluster will form automatically across all nodes
// with the same igniteInstanceName
```

## Development

See the main tika-grpc README for information about running in development mode with plugin hot-reloading.
