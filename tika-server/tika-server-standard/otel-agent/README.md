# OpenTelemetry Java Agent for Tika Server

This directory contains instructions for using the OpenTelemetry Java Agent for automatic instrumentation of Tika Server.

## What is Auto-Instrumentation?

The OpenTelemetry Java Agent provides automatic instrumentation for many popular Java libraries without requiring code changes. When attached to Tika Server, it automatically captures:

- HTTP request/response traces (via Jetty instrumentation)
- JDBC database calls (if using database features)
- Additional framework-level spans

This complements the manual instrumentation already present in Tika Server for Tika-specific operations.

## Download the Agent

### Option 1: Direct Download

Download the latest OpenTelemetry Java Agent:

```bash
curl -L -O https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar
```

### Option 2: Maven

Add to your pom.xml and use Maven to download:

```xml
<dependency>
    <groupId>io.opentelemetry.javaagent</groupId>
    <artifactId>opentelemetry-javaagent</artifactId>
    <version>2.10.0</version>
    <scope>runtime</scope>
</dependency>
```

### Option 3: Specific Version

```bash
VERSION=2.10.0
curl -L -O "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${VERSION}/opentelemetry-javaagent.jar"
```

## Usage

### Basic Usage

```bash
java -javaagent:opentelemetry-javaagent.jar \
     -Dotel.service.name=tika-server \
     -Dotel.exporter.otlp.endpoint=http://localhost:4317 \
     -jar tika-server-standard-*.jar
```

### With Environment Variables

```bash
export OTEL_SERVICE_NAME=tika-server
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
export OTEL_TRACES_EXPORTER=otlp
export OTEL_METRICS_EXPORTER=otlp
export OTEL_LOGS_EXPORTER=otlp

java -javaagent:opentelemetry-javaagent.jar \
     -jar tika-server-standard-*.jar
```

### Using the Tika Startup Script

If you place the agent JAR in this directory, you can use an environment variable:

```bash
export OTEL_JAVAAGENT_PATH=/path/to/otel-agent/opentelemetry-javaagent.jar
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317

./bin/tika
```

## Configuration Options

### Common System Properties

| Property | Description | Default |
|----------|-------------|---------|
| `otel.service.name` | Service name for traces | `unknown_service:java` |
| `otel.exporter.otlp.endpoint` | OTLP endpoint URL | `http://localhost:4317` |
| `otel.traces.sampler` | Sampling strategy | `parentbased_always_on` |
| `otel.traces.sampler.arg` | Sampler argument (e.g., probability) | - |
| `otel.instrumentation.http.capture-headers.server.request` | Capture request headers | - |
| `otel.instrumentation.http.capture-headers.server.response` | Capture response headers | - |

### Sampling Configuration

Sample 10% of traces:
```bash
-Dotel.traces.sampler=traceidratio \
-Dotel.traces.sampler.arg=0.1
```

### Disable Specific Instrumentations

```bash
# Disable JDBC instrumentation
-Dotel.instrumentation.jdbc.enabled=false

# Disable HTTP client instrumentation
-Dotel.instrumentation.http-url-connection.enabled=false
```

## What Gets Instrumented Automatically?

With the Java Agent attached, you'll see additional spans for:

### HTTP Server (Jetty/CXF)
- `HTTP GET /tika`
- `HTTP PUT /detect/stream`
- Request/response headers
- HTTP status codes
- Request duration

### JDBC (if used)
- Database queries
- Connection pool metrics
- Transaction boundaries

### JVM Metrics
- Memory usage
- GC activity
- Thread counts
- CPU usage

## Combining Auto and Manual Instrumentation

The Java Agent works seamlessly with Tika's manual instrumentation:

1. **Auto-instrumentation** creates high-level HTTP request spans
2. **Manual instrumentation** creates detailed Tika-specific spans (parse, detect, metadata)
3. Spans are automatically nested, showing the complete request flow

Example trace structure:
```
HTTP PUT /tika (auto-instrumented)
 └─ tika.parse (manual)
     ├─ parser.initialization
     └─ content.extraction
```

## Verifying Installation

After starting Tika Server with the agent:

1. Check logs for OpenTelemetry initialization:
   ```
   [otel.javaagent] OpenTelemetry Java Agent ...
   ```

2. Send a test request:
   ```bash
   curl -T test.pdf http://localhost:9998/tika
   ```

3. View traces in Jaeger UI (http://localhost:16686)

4. Look for both auto-instrumented (`HTTP PUT`) and manual (`tika.parse`) spans

## Performance Considerations

The Java Agent adds minimal overhead:
- Typical overhead: 1-3%
- Can be reduced with sampling
- Async span export prevents blocking

To measure impact:
```bash
# Without agent
time java -jar tika-server-standard-*.jar &

# With agent
time java -javaagent:opentelemetry-javaagent.jar -jar tika-server-standard-*.jar &
```

## Troubleshooting

### Agent not loading

Ensure the JAR path is correct:
```bash
java -javaagent:/full/path/to/opentelemetry-javaagent.jar -jar ...
```

### No auto-instrumented spans

Check that supported libraries are being used:
```bash
-Dotel.javaagent.debug=true
```

### Conflicts with manual instrumentation

The agent is compatible with manual SDK usage. Spans are automatically correlated.

### Excessive spans

Disable unwanted instrumentations:
```bash
-Dotel.instrumentation.common.default-enabled=false \
-Dotel.instrumentation.jetty.enabled=true \
-Dotel.instrumentation.http.enabled=true
```

## Further Reading

- [OpenTelemetry Java Agent Documentation](https://opentelemetry.io/docs/instrumentation/java/automatic/)
- [Supported Libraries and Frameworks](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/supported-libraries.md)
- [Configuration Options](https://opentelemetry.io/docs/instrumentation/java/automatic/agent-config/)
