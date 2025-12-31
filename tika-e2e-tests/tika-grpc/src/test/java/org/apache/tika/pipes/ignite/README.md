# Ignite ConfigStore E2E Test

## Overview

This test verifies that the **embedded Ignite ConfigStore** works correctly for sharing fetcher configurations between the gRPC server and forked PipesServer processes.

## Architecture

The Ignite server runs **embedded within the tika-grpc process** - no separate Ignite deployment needed!

```
┌─────────────────────────────────┐
│      tika-grpc Process         │
│  ┌──────────────────────────┐  │
│  │  IgniteStoreServer       │  │  ← Embedded server (daemon thread)
│  │  (server mode)           │  │
│  └────────▲─────────────────┘  │
│           │                     │
│  ┌────────┴─────────────────┐  │
│  │ TikaGrpcServer           │  │  ← Connects as client
│  │ IgniteConfigStore        │  │
│  └──────────────────────────┘  │
└─────────────────────────────────┘
           ▲
           │ (client connection)
           │
  ┌────────┴─────────────────┐
  │  PipesServer (forked)    │     ← Connects as client
  │  IgniteConfigStore       │
  └──────────────────────────┘
```

## Test Scenario

1. Start tika-grpc (automatically starts embedded Ignite server)
2. Dynamically create a fetcher via gRPC `saveFetcher` 
3. Fetcher is stored in Ignite cache
4. Process documents using forked PipesServer
5. PipesServer connects to Ignite as client and retrieves fetcher
6. Verify documents are processed successfully

## Prerequisites

- Docker and Docker Compose
- Maven 3.6+
- Java 17+
- Apache Tika Docker image with Ignite support: `apache/tika-grpc:local`

## Building Tika with Embedded Ignite Support

Build from the `file-based-config-store` branch:

```bash
cd /path/to/tika
git checkout file-based-config-store
mvn clean install -DskipTests

# Build Docker image
cd /path/to/tika-grpc-docker
./build-from-branch.sh -l /path/to/tika -t local
```

## Running the Test

### Run just the Ignite test with limited documents:

```bash
mvn test -Dtest=IgniteConfigStoreTest -Dcorpa.numdocs=5
```

### Run with all documents:

```bash
mvn test -Dtest=IgniteConfigStoreTest
```

### Run all e2e tests (file + ignite):

```bash
mvn test -Dcorpa.numdocs=10
```

## Configuration

The test uses `src/test/resources/tika-config-ignite.json`:

```json
{
  "pipes": {
    "configStoreType": "ignite",
    "configStoreParams": "{
      \"cacheName\": \"tika-e2e-test\",
      \"cacheMode\": \"REPLICATED\",
      \"igniteInstanceName\": \"TikaE2ETest\"
    }"
  }
}
```

**What happens on startup:**
1. TikaGrpcServer reads config
2. Sees `configStoreType: "ignite"`
3. Automatically starts `IgniteStoreServer` in background daemon thread
4. Creates IgniteConfigStore as client (connects to embedded server)
5. Ready to accept gRPC requests!

## Expected Behavior

✅ **Success:** Embedded Ignite server starts automatically  
✅ **Success:** Fetcher created via gRPC is stored in Ignite  
✅ **Success:** Forked PipesServer connects as client and retrieves fetcher  
✅ Documents are processed successfully  
✅ No `FetcherNotFoundException`

❌ **Failure:** Would indicate Ignite server/client communication issue

## Advantages of Embedded Architecture

| Aspect | Embedded Ignite | External Ignite Cluster |
|--------|----------------|------------------------|
| **Deployment** | Single Docker container | Multi-container setup |
| **Configuration** | Automatic startup | Manual cluster management |
| **Dependencies** | None (embedded) | Requires separate Ignite deployment |
| **Use Cases** | Single-instance, dev/test | Production multi-instance clusters |
| **Complexity** | Low | Medium-High |

## Troubleshooting

**Container fails to start:**
```bash
docker logs <container-id>
```

**Test timeout:**
- Increase `MAX_STARTUP_TIMEOUT` in `ExternalTestBase.java`
- Check Docker resources (memory, CPU)

**Ignite connection issues:**
```bash
# Check Ignite server started
docker logs <container> | grep "Ignite server started"

# Check topology
docker logs <container> | grep "Topology snapshot"
```

**Server didn't start:**
- Check logs for `"Starting embedded Ignite server"`
- Verify tika-pipes-ignite plugin is in classpath
- Check JAVA_OPTS has sufficient memory

## Difference from FileSystemFetcherTest

| Aspect | FileSystemFetcherTest | IgniteConfigStoreTest |
|--------|----------------------|----------------------|
| ConfigStore | File-based (`/tmp/tika-config-store.json`) | Embedded Ignite (in-memory) |
| Config File | `tika-config.json` | `tika-config-ignite.json` |
| Architecture | File I/O | Embedded server + clients |
| Use Case | Single-instance with persistence | In-process distributed cache |
| External Deps | None | None (embedded!) |

Both tests verify dynamic fetcher management works across JVM boundaries!

## Production Deployment

For production, you can:
1. Use the embedded architecture (easiest)
2. Run multiple tika-grpc instances - each starts its own Ignite server node
3. Nodes auto-discover and form cluster
4. Cache is replicated across all nodes
5. No external Ignite deployment needed!

