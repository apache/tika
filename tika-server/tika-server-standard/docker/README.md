# OpenTelemetry Observability Stack for Tika Server

This directory contains Docker Compose configurations for running an observability stack alongside Tika Server.

## Quick Start

### 1. Start Jaeger (Traces)

```bash
docker-compose -f docker-compose-otel.yml up -d jaeger
```

This starts Jaeger all-in-one which includes:
- OTLP gRPC receiver on port 4317
- OTLP HTTP receiver on port 4318
- Jaeger UI on port 16686

### 2. Configure Tika Server

Set the OTLP endpoint environment variable:

```bash
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
export OTEL_SERVICE_NAME=my-tika-server
```

Or enable via XML configuration in `tika-server-config.xml`:

```xml
<openTelemetry>
  <enabled>true</enabled>
  <otlpEndpoint>http://localhost:4317</otlpEndpoint>
  <serviceName>my-tika-server</serviceName>
</openTelemetry>
```

### 3. Start Tika Server

```bash
java -jar tika-server-standard/target/tika-server-standard-*.jar
```

### 4. Send Test Requests

```bash
# Parse a document
curl -T sample.pdf http://localhost:9998/tika

# Detect MIME type
echo "Hello World" | curl -X PUT --data-binary @- http://localhost:9998/detect/stream

# Extract metadata
curl -T sample.pdf http://localhost:9998/meta
```

### 5. View Traces in Jaeger UI

Open your browser to: http://localhost:16686

- Select "my-tika-server" from the Service dropdown
- Click "Find Traces"
- Click on a trace to see detailed span information

## Services

### Jaeger

Jaeger provides distributed tracing:
- **UI**: http://localhost:16686
- **OTLP gRPC**: localhost:4317
- **OTLP HTTP**: localhost:4318

### Prometheus (Optional)

To start Prometheus for metrics collection:

```bash
docker-compose -f docker-compose-otel.yml --profile with-metrics up -d
```

Access Prometheus UI at: http://localhost:9090

## Stopping Services

```bash
docker-compose -f docker-compose-otel.yml down
```

To remove volumes as well:

```bash
docker-compose -f docker-compose-otel.yml down -v
```

## Troubleshooting

### Traces not appearing in Jaeger

1. Check Tika Server logs for OpenTelemetry initialization messages
2. Verify OTEL_EXPORTER_OTLP_ENDPOINT is set correctly
3. Check Jaeger logs: `docker logs tika-jaeger`
4. Ensure firewall allows connection to port 4317

### Connection refused errors

Make sure Jaeger is running:
```bash
docker ps | grep jaeger
```

If using Docker on Mac/Windows, use `host.docker.internal` instead of `localhost`:
```bash
export OTEL_EXPORTER_OTLP_ENDPOINT=http://host.docker.internal:4317
```

## Advanced Configuration

### Custom Prometheus Configuration

Create a `prometheus.yml` file in this directory:

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'tika-server'
    static_configs:
      - targets: ['host.docker.internal:9998']
```

Then start with the metrics profile.

### Using with Tika Docker Container

If running Tika Server in Docker, add it to the same network:

```bash
docker run -d \
  --name tika-server \
  --network tika-otel_tika-otel \
  -e OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4317 \
  -e OTEL_SERVICE_NAME=tika-server \
  -p 9998:9998 \
  apache/tika:latest
```
