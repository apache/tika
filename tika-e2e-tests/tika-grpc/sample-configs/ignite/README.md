# Apache Ignite ConfigStore Configuration

This directory contains sample configurations for running tika-grpc with Apache Ignite distributed configuration storage.

## Building the Image

To build a Docker image from the TIKA-4583 branch with Ignite support:

```bash
./build-from-branch.sh -b TIKA-4583-ignite-config-store -i -t ignite-test
```

## Running Standalone

Run a single instance with Ignite (useful for testing):

```bash
docker run -p 50052:50052 \
  -v $(pwd)/sample-configs/ignite/tika-config-ignite.json:/config/tika-config.json \
  apache/tika-grpc:ignite-test \
  -c /config/tika-config.json
```

## Running in Docker Compose (Clustered)

Create a `docker-compose.yml`:

```yaml
version: '3.8'

services:
  tika-grpc-1:
    image: apache/tika-grpc:ignite-test
    ports:
      - "50052:50052"
    volumes:
      - ./sample-configs/ignite/tika-config-ignite.json:/config/tika-config.json
    command: ["-c", "/config/tika-config.json"]
    networks:
      - tika-cluster

  tika-grpc-2:
    image: apache/tika-grpc:ignite-test
    ports:
      - "50053:50052"
    volumes:
      - ./sample-configs/ignite/tika-config-ignite.json:/config/tika-config.json
    command: ["-c", "/config/tika-config.json"]
    networks:
      - tika-cluster

  tika-grpc-3:
    image: apache/tika-grpc:ignite-test
    ports:
      - "50054:50052"
    volumes:
      - ./sample-configs/ignite/tika-config-ignite.json:/config/tika-config.json
    command: ["-c", "/config/tika-config.json"]
    networks:
      - tika-cluster

networks:
  tika-cluster:
    driver: bridge
```

Start the cluster:

```bash
docker-compose up
```

## Verifying Cluster Formation

Check the logs to verify Ignite cluster formation:

```bash
docker-compose logs | grep "Topology snapshot"
```

You should see output like:
```
Topology snapshot [ver=3, servers=3, clients=0, ...]
```

## Testing Configuration Sharing

1. Create a fetcher on one server:
```bash
# Add fetcher to server 1 (port 50052)
grpcurl -d '{"fetcher_config": "{\"id\":\"shared-fetcher\",\"name\":\"file-system\",\"params\":{\"basePath\":\"/data\"}}"}' \
  -plaintext localhost:50052 tika.Tika/SaveFetcher
```

2. Retrieve it from another server:
```bash
# Get fetcher from server 2 (port 50053)
grpcurl -d '{"fetcher_id": "shared-fetcher"}' \
  -plaintext localhost:50053 tika.Tika/GetFetcher
```

The fetcher should be available on all servers in the cluster!

## Configuration Options

Edit `tika-config-ignite.json` to customize:

| Parameter | Description | Default |
|-----------|-------------|---------|
| `cacheName` | Name of the Ignite cache | `tika-config-store` |
| `cacheMode` | Cache mode (REPLICATED or PARTITIONED) | `REPLICATED` |
| `igniteInstanceName` | Ignite instance name | `TikaIgniteCluster` |
| `autoClose` | Auto-close Ignite on shutdown | `true` |

## Kubernetes Deployment

See the main [Ignite ConfigStore README](https://github.com/apache/tika/tree/TIKA-4583-ignite-config-store/tika-pipes/tika-ignite-config-store#kubernetes-deployment) for comprehensive Kubernetes deployment instructions.
