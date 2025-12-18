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

## Kubernetes Deployment

Apache Ignite requires special configuration to enable pod-to-pod discovery in Kubernetes. Below is a complete example of deploying tika-grpc with Ignite clustering.

### Prerequisites

1. Kubernetes cluster with RBAC enabled
2. Docker image with tika-grpc and tika-ignite-config-store dependency
3. Network policies allowing pod-to-pod communication

### Step 1: Create Ignite Configuration XML

Create an `ignite-config.xml` file to configure Kubernetes IP finder:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
                           http://www.springframework.org/schema/beans/spring-beans.xsd">
    
    <bean id="ignite.cfg" class="org.apache.ignite.configuration.IgniteConfiguration">
        <!-- Cluster name -->
        <property name="igniteInstanceName" value="TikaIgniteCluster"/>
        
        <!-- Enable peer class loading -->
        <property name="peerClassLoadingEnabled" value="false"/>
        
        <!-- Discovery configuration -->
        <property name="discoverySpi">
            <bean class="org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi">
                <property name="ipFinder">
                    <bean class="org.apache.ignite.spi.discovery.tcp.ipfinder.kubernetes.TcpDiscoveryKubernetesIpFinder">
                        <property name="namespace" value="default"/>
                        <property name="serviceName" value="tika-grpc-headless"/>
                    </bean>
                </property>
                <property name="socketTimeout" value="10000"/>
                <property name="networkTimeout" value="10000"/>
            </bean>
        </property>
        
        <!-- Communication SPI -->
        <property name="communicationSpi">
            <bean class="org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi">
                <property name="localPort" value="47100"/>
                <property name="localPortRange" value="10"/>
                <property name="socketWriteTimeout" value="10000"/>
            </bean>
        </property>
    </bean>
</beans>
```

### Step 2: Create Kubernetes Resources

#### ServiceAccount and RBAC

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: tika-grpc
  namespace: default

---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: tika-grpc-ignite
  namespace: default
rules:
  - apiGroups: [""]
    resources: ["pods", "endpoints"]
    verbs: ["get", "list", "watch"]

---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: tika-grpc-ignite
  namespace: default
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: tika-grpc-ignite
subjects:
  - kind: ServiceAccount
    name: tika-grpc
    namespace: default
```

#### Headless Service for Ignite Discovery

```yaml
apiVersion: v1
kind: Service
metadata:
  name: tika-grpc-headless
  namespace: default
  labels:
    app: tika-grpc
spec:
  clusterIP: None  # Headless service for pod discovery
  selector:
    app: tika-grpc
  ports:
    - name: ignite-discovery
      port: 47500
      protocol: TCP
    - name: ignite-communication
      port: 47100
      protocol: TCP
    - name: grpc
      port: 50052
      protocol: TCP
```

#### LoadBalancer Service for gRPC Access

```yaml
apiVersion: v1
kind: Service
metadata:
  name: tika-grpc
  namespace: default
  labels:
    app: tika-grpc
spec:
  type: LoadBalancer
  selector:
    app: tika-grpc
  ports:
    - name: grpc
      port: 50052
      targetPort: 50052
      protocol: TCP
```

#### ConfigMap for Tika Configuration

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: tika-config
  namespace: default
data:
  tika-config.json: |
    {
      "pipes": {
        "configStoreType": "ignite",
        "configStoreParams": {
          "cacheName": "tika-config-store",
          "cacheMode": "REPLICATED",
          "igniteInstanceName": "TikaIgniteCluster",
          "autoClose": true
        }
      },
      "fetchers": [],
      "emitters": []
    }
```

#### StatefulSet Deployment

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: tika-grpc
  namespace: default
spec:
  serviceName: tika-grpc-headless
  replicas: 3
  selector:
    matchLabels:
      app: tika-grpc
  template:
    metadata:
      labels:
        app: tika-grpc
    spec:
      serviceAccountName: tika-grpc
      containers:
        - name: tika-grpc
          image: your-registry/tika-grpc:4.0.0-SNAPSHOT
          ports:
            - containerPort: 50052
              name: grpc
              protocol: TCP
            - containerPort: 47500
              name: ignite-disc
              protocol: TCP
            - containerPort: 47100
              name: ignite-comm
              protocol: TCP
          env:
            - name: IGNITE_QUIET
              value: "false"
            - name: IGNITE_NO_SHUTDOWN_HOOK
              value: "true"
            - name: JVM_OPTS
              value: "-Xms2g -Xmx4g -DIGNITE_CONFIG_URL=/config/ignite-config.xml"
          command:
            - java
            - -jar
            - /app/tika-grpc.jar
            - -c
            - /config/tika-config.json
            - -p
            - "50052"
          volumeMounts:
            - name: config
              mountPath: /config
              readOnly: true
          resources:
            requests:
              memory: "2Gi"
              cpu: "1000m"
            limits:
              memory: "4Gi"
              cpu: "2000m"
          livenessProbe:
            exec:
              command:
                - grpc_health_probe
                - -addr=:50052
            initialDelaySeconds: 30
            periodSeconds: 10
            timeoutSeconds: 5
          readinessProbe:
            exec:
              command:
                - grpc_health_probe
                - -addr=:50052
            initialDelaySeconds: 10
            periodSeconds: 5
            timeoutSeconds: 3
      volumes:
        - name: config
          configMap:
            name: tika-config
```

### Step 3: Deploy to Kubernetes

```bash
# Apply RBAC and ServiceAccount
kubectl apply -f rbac.yaml

# Create services
kubectl apply -f services.yaml

# Create ConfigMap
kubectl apply -f configmap.yaml

# Deploy StatefulSet
kubectl apply -f statefulset.yaml

# Check pod status
kubectl get pods -l app=tika-grpc

# Check Ignite cluster formation
kubectl logs tika-grpc-0 | grep "Topology snapshot"
```

### Step 4: Verify Cluster Formation

Check that all pods have joined the Ignite cluster:

```bash
# View logs from first pod
kubectl logs tika-grpc-0 | grep -A 5 "Topology snapshot"

# You should see output like:
# Topology snapshot [ver=3, servers=3, clients=0, CPUs=6, offheap=12GB, heap=12GB]
```

### Dockerfile Example

Create a Dockerfile that includes the Ignite ConfigStore:

```dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy tika-grpc jar with dependencies
COPY tika-grpc/target/tika-grpc-4.0.0-SNAPSHOT.jar /app/tika-grpc.jar

# Copy Ignite config store plugin
COPY tika-ignite-config-store/target/tika-ignite-config-store-4.0.0-SNAPSHOT.jar /app/plugins/

# Install grpc_health_probe for health checks
RUN apk add --no-cache curl && \
    GRPC_HEALTH_PROBE_VERSION=v0.4.19 && \
    curl -L https://github.com/grpc-ecosystem/grpc-health-probe/releases/download/${GRPC_HEALTH_PROBE_VERSION}/grpc_health_probe-linux-amd64 \
    -o /usr/local/bin/grpc_health_probe && \
    chmod +x /usr/local/bin/grpc_health_probe

EXPOSE 50052 47500 47100

ENTRYPOINT ["java", "-jar", "/app/tika-grpc.jar"]
```

### Important Notes

1. **Network Policies**: Ensure your Kubernetes network policies allow:
   - Port 47500 for Ignite discovery
   - Port 47100 for Ignite communication
   - Port 50052 for gRPC traffic

2. **Resource Requirements**: Ignite requires adequate memory. Allocate at least 2GB per pod.

3. **Pod Anti-Affinity**: Consider adding pod anti-affinity to spread pods across nodes:

```yaml
affinity:
  podAntiAffinity:
    preferredDuringSchedulingIgnoredDuringExecution:
      - weight: 100
        podAffinityTerm:
          labelSelector:
            matchExpressions:
              - key: app
                operator: In
                values:
                  - tika-grpc
          topologyKey: kubernetes.io/hostname
```

4. **Monitoring**: Monitor Ignite metrics:
   - Cluster size: Check topology logs
   - Cache size: Monitor memory usage
   - Network latency: Check communication delays

### Troubleshooting

**Pods can't discover each other:**
- Verify RBAC permissions: `kubectl auth can-i list pods --as=system:serviceaccount:default:tika-grpc`
- Check headless service: `kubectl get endpoints tika-grpc-headless`
- Review Ignite logs: `kubectl logs <pod-name> | grep Discovery`

**Ignite cluster fails to form:**
- Ensure all pods are in same namespace
- Verify service name matches Ignite configuration
- Check network policies aren't blocking ports 47100-47110, 47500

**Memory issues:**
- Increase pod memory limits
- Adjust JVM heap: `-Xms` and `-Xmx` parameters
- Consider PARTITIONED cache mode for large deployments

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
