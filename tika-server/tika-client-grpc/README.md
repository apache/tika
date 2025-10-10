# Tika gRPC Client

A Java client library for connecting to Apache Tika gRPC servers. This library provides a simple, easy-to-use interface for interacting with Tika gRPC services, abstracting away the complexity of gRPC communication while providing both synchronous and asynchronous operations.

## Features

- **Easy-to-use API**: Simple method calls for all Tika gRPC operations
- **Synchronous and Asynchronous support**: Choose the right approach for your use case  
- **Connection management**: Automatic connection handling with configurable timeouts and keep-alive
- **Type-safe**: Uses generated protocol buffer classes directly
- **Resource management**: Proper cleanup with try-with-resources support
- **Comprehensive coverage**: Supports all Tika gRPC operations (fetchers, emitters, pipe iterators, pipe jobs)

## Installation

Add the dependency to your Maven project:

```xml
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-grpc-client</artifactId>
    <version>4.0.0-SNAPSHOT</version>
</dependency>
```

For Gradle:

```gradle
implementation 'org.apache.tika:tika-grpc-client:4.0.0-SNAPSHOT'
```

## Quick Start

### Basic Usage

```java
import org.apache.tika.grpc.client.TikaGrpcClient;
import org.apache.tika.grpc.client.config.TikaGrpcClientConfig;
import org.apache.tika.FetchAndParseReply;

// Create client with default configuration (localhost:9090)
try (TikaGrpcClient client = TikaGrpcClient.createDefault()) {
    
    // Save a fetcher configuration
    client.saveFetcher("my-fetcher", "file-system-fetcher", 
                      "{\"basePath\": \"/tmp\"}");
    
    // Fetch and parse a document
    FetchAndParseReply result = client.fetchAndParse("my-fetcher", "document.pdf");
    
    // Access the results
    System.out.println("Status: " + result.getStatus());
    System.out.println("Metadata count: " + result.getMetadataCount());
    
    // Access metadata fields
    if (result.getMetadataCount() > 0) {
        result.getMetadata(0).getFieldsMap().forEach((key, valueList) -> {
            System.out.println(key + ": " + valueList.getValuesList());
        });
    }
}
```

### Custom Configuration

```java
import org.apache.tika.grpc.client.TikaGrpcClient;
import org.apache.tika.grpc.client.config.TikaGrpcClientConfig;

TikaGrpcClientConfig config = TikaGrpcClientConfig.builder()
    .host("tika-server.example.com")
    .port(9090)
    .tlsEnabled(true)
    .maxInboundMessageSize(16 * 1024 * 1024) // 16MB
    .connectionTimeoutSeconds(30)
    .keepAliveTimeSeconds(60)
    .build();

try (TikaGrpcClient client = new TikaGrpcClient(config)) {
    // Use the client...
}
```

## API Reference

### Fetcher Operations

```java
// Save a fetcher
String fetcherId = client.saveFetcher("my-fetcher", "file-system-fetcher", 
                                     "{\"basePath\": \"/documents\"}");

// Get fetcher info
GetFetcherReply fetcherInfo = client.getFetcher("my-fetcher");
System.out.println("Plugin ID: " + fetcherInfo.getPluginId());

// List all fetchers
ListFetchersReply fetchers = client.listFetchers(1, 10); // page 1, 10 per page
for (GetFetcherReply fetcher : fetchers.getGetFetcherRepliesList()) {
    System.out.println("Fetcher: " + fetcher.getFetcherId());
}

// Delete a fetcher
boolean deleted = client.deleteFetcher("my-fetcher");

// Get fetcher configuration schema
String schema = client.getFetcherConfigJsonSchema("file-system-fetcher");
```

### Parse Operations

```java
// Simple fetch and parse
FetchAndParseReply result = client.fetchAndParse("my-fetcher", "document.pdf");

// With additional configuration
FetchAndParseReply result = client.fetchAndParse(
    "my-fetcher", 
    "document.pdf",
    "{\"headers\": {\"Authorization\": \"Bearer token\"}}", // fetch metadata
    "{\"source\": \"api\"}", // added metadata
    "{\"maxStringLength\": 10000}" // parse context
);

// Asynchronous parsing
CompletableFuture<FetchAndParseReply> future = 
    client.fetchAndParseAsync("my-fetcher", "document.pdf");

future.thenAccept(result -> {
    System.out.println("Async result: " + result.getStatus());
}).exceptionally(throwable -> {
    System.err.println("Error: " + throwable.getMessage());
    return null;
});
```

### Emitter Operations

```java
// Save an emitter
String emitterId = client.saveEmitter("my-emitter", "file-system-emitter", 
                                     "{\"basePath\": \"/output\"}");

// Get emitter info
GetEmitterReply emitterInfo = client.getEmitter("my-emitter");

// List all emitters
ListEmittersReply emitters = client.listEmitters(1, 10);

// Delete an emitter
boolean deleted = client.deleteEmitter("my-emitter");

// Get emitter configuration schema
String schema = client.getEmitterConfigJsonSchema("file-system-emitter");
```

### Pipe Iterator Operations

```java
// Save a pipe iterator
String iteratorId = client.savePipeIterator("my-iterator", "csv-pipe-iterator", 
                                           "{\"csvFile\": \"/path/to/files.csv\"}");

// Get pipe iterator info
GetPipeIteratorReply iteratorInfo = client.getPipeIterator("my-iterator");

// List all pipe iterators
ListPipeIteratorsReply iterators = client.listPipeIterators(1, 10);

// Delete a pipe iterator
boolean deleted = client.deletePipeIterator("my-iterator");

// Get pipe iterator configuration schema
String schema = client.getPipeIteratorConfigJsonSchema("csv-pipe-iterator");
```

### Pipe Job Operations

```java
// Run a pipe job
String jobId = client.runPipeJob(
    "my-iterator",  // pipe iterator ID
    "my-fetcher",   // fetcher ID  
    "my-emitter",   // emitter ID
    3600           // timeout in seconds
);

// Check job status
GetPipeJobReply jobStatus = client.getPipeJob(jobId);
System.out.println("Job running: " + jobStatus.getIsRunning());
System.out.println("Job completed: " + jobStatus.getIsCompleted());
System.out.println("Job has error: " + jobStatus.getHasError());
```

## Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| `host` | `"localhost"` | Server hostname or IP address |
| `port` | `9090` | Server port |
| `tlsEnabled` | `false` | Enable TLS encryption |
| `maxInboundMessageSize` | `4MB` | Maximum message size |
| `connectionTimeoutSeconds` | `30` | Connection timeout |
| `keepAliveTimeSeconds` | `30` | Keep-alive interval |
| `keepAliveTimeoutSeconds` | `5` | Keep-alive timeout |

## Error Handling

All client operations throw `TikaGrpcClientException` for any errors:

```java
try {
    FetchAndParseReply result = client.fetchAndParse("my-fetcher", "document.pdf");
} catch (TikaGrpcClientException e) {
    System.err.println("gRPC operation failed: " + e.getMessage());
    
    // Access the underlying cause if needed
    Throwable cause = e.getCause();
    if (cause instanceof StatusRuntimeException) {
        StatusRuntimeException grpcError = (StatusRuntimeException) cause;
        System.err.println("gRPC status: " + grpcError.getStatus());
    }
}
```

## Connection Health

Check if the connection is healthy:

```java
if (client.isConnected()) {
    System.out.println("Client is connected to the server");
} else {
    System.out.println("Client is not connected");
}
```

## Working with Metadata

The `FetchAndParseReply` contains rich metadata information:

```java
FetchAndParseReply result = client.fetchAndParse("my-fetcher", "document.pdf");

// Check status
if ("SUCCESS".equals(result.getStatus())) {
    // Process metadata
    for (int i = 0; i < result.getMetadataCount(); i++) {
        Metadata metadata = result.getMetadata(i);
        
        metadata.getFieldsMap().forEach((fieldName, valueList) -> {
            System.out.print(fieldName + ": ");
            
            // Handle multiple values per field
            for (Value value : valueList.getValuesList()) {
                switch (value.getValueCase()) {
                    case STRING_VALUE:
                        System.out.print(value.getStringValue() + " ");
                        break;
                    case INT_VALUE:
                        System.out.print(value.getIntValue() + " ");
                        break;
                    case BOOL_VALUE:
                        System.out.print(value.getBoolValue() + " ");
                        break;
                    case DOUBLE_VALUE:
                        System.out.print(value.getDoubleValue() + " ");
                        break;
                    default:
                        System.out.print("null ");
                }
            }
            System.out.println();
        });
    }
} else {
    System.err.println("Parse failed with status: " + result.getStatus());
    if (!result.getErrorMessage().isEmpty()) {
        System.err.println("Error: " + result.getErrorMessage());
    }
}
```

## Best Practices

1. **Use try-with-resources**: Always use try-with-resources or manually call `close()` to properly cleanup connections.

2. **Configure timeouts**: Set appropriate timeouts based on your document sizes and network conditions.

3. **Handle errors gracefully**: Wrap operations in try-catch blocks and handle `TikaGrpcClientException`.

4. **Reuse client instances**: Create one client instance and reuse it for multiple operations rather than creating new instances.

5. **Check connection health**: Use `isConnected()` to verify connectivity before critical operations.

6. **Configure message sizes**: Increase `maxInboundMessageSize` if you're processing large documents.

## Thread Safety

The `TikaGrpcClient` is thread-safe and can be used concurrently from multiple threads. The underlying gRPC channel handles concurrent requests efficiently.

## Examples

See the `src/test/java` directory for comprehensive examples and integration tests.

## Requirements

- Java 17 or later
- A running Tika gRPC server

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.