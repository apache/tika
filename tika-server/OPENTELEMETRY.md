# OpenTelemetry Instrumentation for Apache Tika Server

This document describes how to enable and use OpenTelemetry (OTEL) observability in Apache Tika Server for comprehensive monitoring of traces, metrics, and logs.

## Table of Contents

1. [Introduction](#introduction)
2. [Prerequisites](#prerequisites)
3. [Quick Start](#quick-start)
4. [Configuration](#configuration)
5. [Auto-Instrumentation Setup](#auto-instrumentation-setup)
6. [Manual Instrumentation](#manual-instrumentation)
7. [Exporters](#exporters)
8. [Docker Integration](#docker-integration)
9. [Verifying Setup](#verifying-setup)
10. [Performance Considerations](#performance-considerations)
11. [Troubleshooting](#troubleshooting)

## Introduction

OpenTelemetry provides standardized observability instrumentation for Tika Server, enabling:

- **Distributed Tracing**: End-to-end request flows from HTTP ingestion to parser execution
- **Metrics**: Throughput, error rates, and resource usage
- **Structured Logs**: Correlated with traces via trace/span IDs

### Why OpenTelemetry?

- **Vendor-neutral**: Works with Jaeger, Zipkin, Prometheus, Grafana, and many others
- **Future-proof**: Semantic conventions ensure compatibility with evolving backends
- **Low overhead**: Configurable sampling and async export minimize performance impact
- **Rich ecosystem**: Integrates with modern observability platforms

## Prerequisites

- **Java**: 11 or higher
- **Apache Tika Server**: 4.0.0-SNAPSHOT or later
- **OpenTelemetry Collector or Backend**: Jaeger, Zipkin, or OTLP-compatible collector

## Quick Start

### 1. Enable OpenTelemetry via Environment Variables

The simplest way to enable OpenTelemetry is through environment variables:

```bash
# For manual instrumentation (uses gRPC by default)
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
export OTEL_SERVICE_NAME=my-tika-server

# For auto-instrumentation with Java agent (uses HTTP by default)
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
export OTEL_SERVICE_NAME=my-tika-server
```

**Port Reference:**
- **4317**: OTLP gRPC endpoint (default for manual instrumentation)
- **4318**: OTLP HTTP endpoint (default for Java agent auto-instrumentation)

Setting `OTEL_EXPORTER_OTLP_ENDPOINT` automatically enables OpenTelemetry.

### 2. Start a Local Jaeger Instance

```bash
cd tika-server/tika-server-standard/docker
docker-compose -f docker-compose-otel.yml up -d jaeger
```

Jaeger UI will be available at: http://localhost:16686

### 3. Start Tika Server

```bash
java -jar tika-server-standard/target/tika-server-standard-*.jar
```

### 4. Send Test Requests

**Important**: Include the `File-Name` header to properly populate the `tika.resource_name` span attribute:

```bash
# Parse a document
curl -T mydocument.pdf \
  -H "File-Name: mydocument.pdf" \
  http://localhost:9998/tika

# Detect MIME type
curl -T sample.txt \
  -H "File-Name: sample.txt" \
  http://localhost:9998/detect/stream

# Extract metadata
curl -T mydocument.pdf \
  -H "File-Name: mydocument.pdf" \
  http://localhost:9998/meta

# Alternative: Use Content-Disposition header (standard HTTP)
curl -T document.docx \
  -H "Content-Disposition: attachment; filename=document.docx" \
  http://localhost:9998/tika

# Or use multipart form upload (filename included automatically)
curl -F "file=@mydocument.pdf" http://localhost:9998/tika/form
```

### 5. View Traces

Open http://localhost:16686, select "my-tika-server" from the service dropdown, and click "Find Traces".

## Configuration

### Environment Variables

OpenTelemetry can be configured entirely through environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `OTEL_SDK_DISABLED` | Disable OpenTelemetry completely | `false` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OTLP endpoint URL | `http://localhost:4317` (gRPC) or `http://localhost:4318` (HTTP) |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | Protocol to use | `grpc` (manual) or `http/protobuf` (agent) |
| `OTEL_SERVICE_NAME` | Service name for identification | `tika-server` |
| `OTEL_TRACES_SAMPLER` | Sampling strategy | `parentbased_always_on` |
| `OTEL_TRACES_SAMPLER_ARG` | Sampling probability (0.0-1.0) | `1.0` |

**Example:**

```bash
export OTEL_EXPORTER_OTLP_ENDPOINT=https://my-collector.example.com:4317
export OTEL_SERVICE_NAME=production-tika-server
export OTEL_TRACES_SAMPLER=traceidratio
export OTEL_TRACES_SAMPLER_ARG=0.1  # Sample 10% of traces
```

### XML Configuration

Alternatively, configure via `tika-server-config.xml`:

```xml
<properties>
  <server>
    <params>
      <port>9998</port>
      <!-- other params -->
    </params>
    <openTelemetry>
      <enabled>true</enabled>
      <exporterType>otlp</exporterType>
      <otlpEndpoint>http://localhost:4317</otlpEndpoint>
      <serviceName>tika-server</serviceName>
      <samplingProbability>1.0</samplingProbability>
      <exportTimeoutMillis>30000</exportTimeoutMillis>
    </openTelemetry>
  </server>
</properties>
```

**Note:** Environment variables take precedence over XML configuration.

### Configuration Options

- **enabled**: Enable/disable OpenTelemetry (`true`/`false`)
- **exporterType**: Currently only `otlp` is supported
- **otlpEndpoint**: OTLP gRPC endpoint URL
- **serviceName**: Identifier for this Tika Server instance
- **samplingProbability**: Fraction of traces to sample (0.0 to 1.0)
- **exportTimeoutMillis**: Timeout for exporting telemetry data

## Auto-Instrumentation Setup

Auto-instrumentation provides automatic tracing for HTTP requests and other framework-level operations.

### Download the OpenTelemetry Java Agent

```bash
cd tika-server/tika-server-standard/otel-agent
curl -L -O https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar
```

### Run Tika Server with Auto-Instrumentation

```bash
# Using HTTP protocol (default for Java agent) - port 4318
java -javaagent:otel-agent/opentelemetry-javaagent.jar \
     -Dotel.service.name=tika-server \
     -Dotel.exporter.otlp.endpoint=http://localhost:4318 \
     -jar tika-server-standard/target/tika-server-standard-*.jar

# Or using gRPC protocol - port 4317
java -javaagent:otel-agent/opentelemetry-javaagent.jar \
     -Dotel.service.name=tika-server \
     -Dotel.exporter.otlp.endpoint=http://localhost:4317 \
     -Dotel.exporter.otlp.protocol=grpc \
     -jar tika-server-standard/target/tika-server-standard-*.jar
```

**Important**: The Java agent defaults to HTTP protocol (port 4318). If using gRPC (port 4317), you must specify `-Dotel.exporter.otlp.protocol=grpc`.

### What Gets Instrumented Automatically?

- HTTP server requests (Jetty/CXF)
- HTTP client calls (if Tika makes outbound HTTP requests)
- JDBC database operations (if using database features)
- JVM metrics (memory, GC, threads, CPU)

See [otel-agent/README.md](tika-server-standard/otel-agent/README.md) for more details.

## Manual Instrumentation

Tika Server includes manual instrumentation for Tika-specific operations:

### Instrumented Endpoints

| Endpoint | Span Name | Attributes |
|----------|-----------|------------|
| `/tika` | `tika.parse` | `tika.resource_name`, `tika.content_type`, `tika.endpoint` |
| `/detect` | `tika.detect` | `tika.resource_name`, `tika.detected_type`, `tika.endpoint` |
| `/meta` | `tika.metadata.extract` | `tika.resource_name`, `tika.metadata_count`, `tika.endpoint` |

### Span Attributes

- **tika.resource_name**: Filename or resource being processed
  - Extracted from `File-Name` request header
  - Or from `Content-Disposition: attachment; filename=...` header
  - Automatically populated when using multipart form uploads
  - Displays as "unknown" if no filename header is provided
- **tika.content_type**: Detected MIME type
- **tika.detected_type**: Result of MIME detection
- **tika.metadata_count**: Number of metadata fields extracted
- **tika.endpoint**: API endpoint invoked

### Error Handling

Exceptions are automatically recorded in spans with:
- Exception type and message
- Stack traces
- Span status set to ERROR

## Exporters

### OTLP (Recommended)

OTLP (OpenTelemetry Protocol) is the native protocol and recommended exporter. OTLP supports two transport protocols:

#### gRPC (Port 4317)
- **Used by**: Manual instrumentation (default in our code)
- **Protocol**: Binary, efficient
- **Configuration:**

```bash
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
# Protocol is gRPC by default for manual instrumentation
```

#### HTTP/Protobuf (Port 4318)
- **Used by**: Java agent auto-instrumentation (default)
- **Protocol**: HTTP with protobuf encoding
- **Configuration:**

```bash
# For Java agent (HTTP is default)
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318

# Or explicitly specify protocol
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
export OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
```

**Supported Backends:**
- OpenTelemetry Collector
- Jaeger (v1.35+)
- Grafana Tempo
- Grafana Cloud
- Honeycomb
- Lightstep
- New Relic
- Many others

### Jaeger

Direct export to Jaeger using OTLP:

```bash
docker run -d --name jaeger \
  -p 16686:16686 \
  -p 4317:4317 \
  jaegertracing/all-in-one:latest
```

Access Jaeger UI: http://localhost:16686

### Grafana Cloud

```bash
export OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp-gateway-prod-us-central-0.grafana.net/otlp
export OTEL_EXPORTER_OTLP_HEADERS="Authorization=Basic <base64-encoded-credentials>"
```

### Console Exporter (Development)

For development/debugging, use the console exporter:

```java
// In TikaOpenTelemetry.java, replace OtlpGrpcSpanExporter with:
LoggingSpanExporter spanExporter = LoggingSpanExporter.create();
```

## Docker Integration

### Using Docker Compose

Start Tika Server with observability stack:

```bash
cd tika-server/tika-server-standard/docker
docker-compose -f docker-compose-otel.yml up -d
```

This starts:
- Jaeger (traces + UI)
- Optionally: Prometheus (metrics)

### Running Tika Server in Docker

```bash
docker run -d \
  --name tika-server \
  --network tika-otel_tika-otel \
  -e OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4317 \
  -e OTEL_SERVICE_NAME=tika-server \
  -p 9998:9998 \
  apache/tika:latest
```

### Kubernetes / Helm

For production Kubernetes deployments, use the OpenTelemetry Operator:

```yaml
apiVersion: opentelemetry.io/v1alpha1
kind: Instrumentation
metadata:
  name: tika-instrumentation
spec:
  exporter:
    endpoint: http://otel-collector:4317
  propagators:
    - tracecontext
    - baggage
  sampler:
    type: parentbased_traceidratio
    argument: "0.25"
```

Apply to Tika Server deployment with annotation:
```yaml
metadata:
  annotations:
    instrumentation.opentelemetry.io/inject-java: "true"
```

See the Tika Helm chart documentation for more details.

## Verifying Setup

### 1. Check Tika Server Logs

Look for initialization messages:

```
INFO  [main] o.a.t.s.c.TikaOpenTelemetry - Initializing OpenTelemetry: TikaOpenTelemetryConfig{enabled=true, ...}
INFO  [main] o.a.t.s.c.TikaOpenTelemetry - OpenTelemetry initialized successfully
```

### 2. Send Test Requests

**Important**: Always include filename headers to populate the `tika.resource_name` attribute in traces.

```bash
# Create a test file
echo "Hello OpenTelemetry" > test.txt

# Parse with Tika (include File-Name header)
curl -T test.txt \
  -H "File-Name: test.txt" \
  http://localhost:9998/tika

# Detect MIME type
curl -T test.txt \
  -H "File-Name: test.txt" \
  http://localhost:9998/detect/stream

# Extract metadata
curl -T test.txt \
  -H "File-Name: test.txt" \
  http://localhost:9998/meta

# Alternative methods to include filename:

# Method 1: Content-Disposition header (HTTP standard)
curl -T test.txt \
  -H "Content-Disposition: attachment; filename=test.txt" \
  http://localhost:9998/tika

# Method 2: Multipart form upload (filename automatic)
curl -F "file=@test.txt" http://localhost:9998/tika/form
```

**Why include filename headers?**  
Without the filename header, the `tika.resource_name` span attribute will show as "unknown", making it harder to identify which document was processed in traces.

### 3. View in Jaeger

1. Open http://localhost:16686
2. Service: Select your service name (e.g., "tika-server")
3. Click "Find Traces"
4. Click on a trace to see detailed spans

### Expected Span Structure

**With Auto-Instrumentation (Java agent):**
```
HTTP PUT /tika
 └─ tika.parse
     Attributes:
       - tika.resource_name: test.txt        ← From File-Name header
       - tika.content_type: text/plain       ← Auto-detected by Tika
       - tika.endpoint: /tika
       - span.status: OK
```

**Manual Instrumentation Only:**
```
tika.parse
 Attributes:
   - tika.resource_name: mydocument.pdf
   - tika.content_type: application/pdf
   - tika.endpoint: /tika
```

**Note**: If the filename header is missing, `tika.resource_name` will show as "unknown".

## Performance Considerations

### Overhead

OpenTelemetry adds minimal overhead when properly configured:
- **Disabled**: No overhead
- **Enabled with sampling**: 1-3% typical overhead
- **Enabled without sampling**: 3-5% worst-case overhead

### Sampling Strategies

**Always On** (Default):
```bash
export OTEL_TRACES_SAMPLER=always_on
```
Captures every trace. Good for development and low-traffic services.

**Probability-Based**:
```bash
export OTEL_TRACES_SAMPLER=traceidratio
export OTEL_TRACES_SAMPLER_ARG=0.1  # 10% sampling
```
Samples a percentage of traces. Reduces overhead and storage costs.

**Parent-Based** (Recommended):
```bash
export OTEL_TRACES_SAMPLER=parentbased_traceidratio
export OTEL_TRACES_SAMPLER_ARG=0.1
```
Respects parent trace sampling decisions (for distributed tracing).

### Async Export

Telemetry data is exported asynchronously in batches, preventing blocking of request processing.

### Resource Limits

The OpenTelemetry SDK uses bounded queues to prevent memory issues:
- Default queue size: 2048 spans
- Spans are dropped if queue is full (counted in metrics)

## Troubleshooting

### Traces Not Appearing

**Problem**: No traces visible in Jaeger/backend.

**Solutions**:

1. **Check OpenTelemetry is enabled:**
   ```bash
   grep "OpenTelemetry" tika-server.log
   ```
   Should see "OpenTelemetry initialized successfully".

2. **Verify endpoint is reachable:**
   ```bash
   telnet localhost 4317
   ```

3. **Check for errors in Tika logs:**
   ```bash
   grep "ERROR.*OpenTelemetry" tika-server.log
   ```

4. **Verify backend is running:**
   ```bash
   docker ps | grep jaeger
   ```

### Connection Refused

**Problem**: `Connection refused` to OTLP endpoint.

**Solutions**:

1. **Start Jaeger/collector:**
   ```bash
   docker-compose -f docker/docker-compose-otel.yml up -d jaeger
   ```

2. **Verify correct port for your protocol:**
   - **Manual instrumentation (gRPC)**: Use port `4317`
   - **Auto-instrumentation (HTTP)**: Use port `4318`
   
   ```bash
   # Manual: 
   export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
   
   # Agent:
   export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
   ```

3. **Check firewall rules:**
   Ensure ports 4317 (gRPC) and 4318 (HTTP) are not blocked.

4. **Use correct hostname:**
   - Local: `http://localhost:4317` or `http://localhost:4318`
   - Docker: `http://jaeger:4317` or `http://jaeger:4318`
   - Docker Desktop: `http://host.docker.internal:4317` or `http://host.docker.internal:4318`

### Wrong Port/Protocol

**Problem**: Warning in logs: "OTLP exporter endpoint port is likely incorrect for protocol version..."

**Cause**: Port and protocol mismatch.

**Solution**: Match the port to the protocol:

```bash
# If using gRPC (manual instrumentation):
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
export OTEL_EXPORTER_OTLP_PROTOCOL=grpc

# If using HTTP (Java agent default):
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
# HTTP is the default for agent, no need to specify protocol
```

**Quick Reference:**
- gRPC → Port 4317
- HTTP → Port 4318

### High Overhead

**Problem**: Tika Server performance degraded after enabling OTEL.

**Solutions**:

1. **Enable sampling:**
   ```bash
   export OTEL_TRACES_SAMPLER=traceidratio
   export OTEL_TRACES_SAMPLER_ARG=0.1
   ```

2. **Disable auto-instrumentation:**
   Remove `-javaagent` flag if only manual instrumentation is needed.

3. **Increase export batch size:**
   Reduces export frequency (in code: `BatchSpanProcessor.builder().setMaxExportBatchSize(512)`).

### Spans Missing Attributes

**Problem**: Spans don't show expected attributes (e.g., `tika.resource_name` shows "unknown").

**Causes**:
- **Missing filename header**: The `File-Name` or `Content-Disposition` header was not included in the request
- **Attributes are null or not set**

**Solutions**:

1. **Include filename in requests:**
   ```bash
   # Add File-Name header
   curl -T document.pdf -H "File-Name: document.pdf" http://localhost:9998/tika
   
   # Or use Content-Disposition
   curl -T document.pdf -H "Content-Disposition: attachment; filename=document.pdf" http://localhost:9998/tika
   
   # Or use multipart form
   curl -F "file=@document.pdf" http://localhost:9998/tika/form
   ```

2. **Check Tika Server logs for warnings:**
   ```bash
   grep "WARN" tika-server.log | grep -i metadata
   ```

### Duplicate Spans

**Problem**: Seeing duplicate spans for the same operation.

**Cause**: Both auto and manual instrumentation creating spans.

**Solution**: This is expected. Auto-instrumentation creates HTTP-level spans, manual creates Tika-specific spans. They should be nested, not duplicated.

## Further Reading

- [OpenTelemetry Official Documentation](https://opentelemetry.io/docs/)
- [OpenTelemetry Java SDK](https://opentelemetry.io/docs/instrumentation/java/)
- [OTLP Specification](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/protocol/otlp.md)
- [Semantic Conventions](https://github.com/open-telemetry/semantic-conventions)
- [Jaeger Documentation](https://www.jaegertracing.io/docs/)
- [Tika Wiki](https://cwiki.apache.org/confluence/display/TIKA)

## Contributing

To add more instrumentation to Tika Server:

1. Import OpenTelemetry API:
   ```java
   import io.opentelemetry.api.trace.Span;
   import io.opentelemetry.api.trace.Tracer;
   ```

2. Get tracer instance:
   ```java
   Tracer tracer = TikaOpenTelemetry.getTracer();
   ```

3. Create spans:
   ```java
   Span span = tracer.spanBuilder("operation.name")
       .setAttribute("key", "value")
       .startSpan();
   try {
       // Your code
       span.setStatus(StatusCode.OK);
   } catch (Exception e) {
       span.recordException(e);
       span.setStatus(StatusCode.ERROR);
   } finally {
       span.end();
   }
   ```

See existing instrumentation in `TikaResource.java`, `DetectorResource.java`, and `MetadataResource.java` for examples.
