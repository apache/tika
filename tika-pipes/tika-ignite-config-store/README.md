# Apache Tika Ignite Config Store

This module provides an Apache Ignite-based implementation of the `ConfigStore` interface for distributed configuration storage in Tika Pipes clustering deployments.

## Overview

The `IgniteConfigStore` enables multiple Tika Pipes servers to share Fetcher, Emitter, and PipesIterator configurations across a cluster using Apache Ignite's distributed cache.

## Features

- **Distributed Configuration Storage**: Share configurations across multiple Tika servers
- **Thread-Safe Operations**: All operations are thread-safe for concurrent access
- **Flexible Cache Modes**: Supports both REPLICATED and PARTITIONED cache modes
- **Simple API**: Implements the standard `ConfigStore` interface
- **Automatic Initialization**: Easy setup with sensible defaults

## Maven Dependency

```xml
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-ignite-config-store</artifactId>
    <version>4.0.0-SNAPSHOT</version>
</dependency>
```

## Usage

### Basic Example

```java
import org.apache.tika.pipes.ignite.IgniteConfigStore;
import org.apache.tika.plugins.ExtensionConfig;

// Create and initialize the store
IgniteConfigStore store = new IgniteConfigStore();
store.init();

// Store a configuration
ExtensionConfig config = new ExtensionConfig("my-fetcher", "http-fetcher", "{\"timeout\": 30000}");
store.put("my-fetcher", config);

// Retrieve a configuration
ExtensionConfig retrieved = store.get("my-fetcher");

// Clean up when done
store.close();
```

### Custom Configuration

```java
IgniteConfigStore store = new IgniteConfigStore();

// Customize cache name
store.setCacheName("my-custom-cache");

// Set cache mode (REPLICATED or PARTITIONED)
store.setCacheMode(CacheMode.PARTITIONED);

// Set Ignite instance name
store.setIgniteInstanceName("MyTikaCluster");

// Initialize
store.init();
```

### Integration with Tika Pipes

The `IgniteConfigStore` can be used in Tika Pipes gRPC server via JSON configuration:

```json
{
  "pipes": {
    "configStoreType": "ignite",
    "configStoreParams": {
      "cacheName": "my-tika-cache",
      "cacheMode": "REPLICATED",
      "igniteInstanceName": "MyTikaCluster",
      "autoClose": true
    }
  },
  "fetchers": [...],
  "emitters": [...]
}
```

Or programmatically:

```java
// In your Tika Pipes server setup
IgniteConfigStore configStore = new IgniteConfigStore();
configStore.init();

// Use with your component managers
FetcherManager fetcherManager = new FetcherManager(configStore);
EmitterManager emitterManager = new EmitterManager(configStore);
```

## Configuration Options

### JSON Configuration Parameters

All parameters in `configStoreParams` are optional and have sensible defaults:

| Property | Description | Default | Values |
|----------|-------------|---------|--------|
| `cacheName` | Name of the Ignite cache | `tika-config-store` | Any string |
| `cacheMode` | Cache replication mode | `REPLICATED` | `REPLICATED` or `PARTITIONED` |
| `igniteInstanceName` | Name of the Ignite instance | `TikaIgniteConfigStore` | Any string |
| `autoClose` | Whether to automatically close Ignite on close() | `true` | `true` or `false` |

### Java API

When using the Java API directly:

| Property | Description | Default |
|----------|-------------|---------|
| `cacheName` | Name of the Ignite cache | `tika-config-store` |
| `cacheMode` | Cache replication mode (REPLICATED or PARTITIONED) | `REPLICATED` |
| `igniteInstanceName` | Name of the Ignite instance | `TikaIgniteConfigStore` |
| `autoClose` | Whether to automatically close Ignite on close() | `true` |

## Cache Modes

### REPLICATED Mode (Default)
- All configurations are replicated to every node
- Faster reads, higher memory usage
- Best for small to medium configuration sets
- Provides highest availability

### PARTITIONED Mode
- Configurations are distributed across nodes
- More memory efficient for large configuration sets
- Includes 1 backup by default
- Best for very large deployments

## Requirements

- Java 17 or higher
- Apache Ignite 2.16.0
- Apache Tika 4.0.0-SNAPSHOT or higher

## Thread Safety

The `IgniteConfigStore` implementation is fully thread-safe and can be safely accessed from multiple threads concurrently.

## Error Handling

All operations will throw `IllegalStateException` if called before `init()` is invoked:

```java
IgniteConfigStore store = new IgniteConfigStore();
// This will throw IllegalStateException
store.put("id", config);  // ERROR: Must call init() first!

// Correct usage
store.init();
store.put("id", config);  // OK
```

## Clustering Considerations

### Multiple Tika Servers

When running multiple Tika servers with `IgniteConfigStore`:

1. Each server should use the same `cacheName`
2. Ensure Ignite discovery is properly configured
3. Configurations created on one server will be immediately available on all other servers

### Network Configuration

For production deployments, configure Ignite discovery mechanisms (multicast, TCP/IP discovery, or cloud discovery) in `IgniteConfiguration` before calling `Ignition.start()`.

## Limitations

- The `init()` method must be called before any other operations
- Once initialized, configuration options (cacheName, cacheMode, etc.) cannot be changed
- Requires Ignite cluster setup for distributed operation

## See Also

- [Apache Ignite Documentation](https://ignite.apache.org/docs/latest/)
- [Tika Pipes Documentation](https://tika.apache.org/)
- `ConfigStore` interface
- `InMemoryConfigStore` - Single-instance alternative

## License

Licensed under the Apache License, Version 2.0. See LICENSE file for details.
