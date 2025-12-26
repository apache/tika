# Tika Pipes GRPC Server

The following is the Tika Pipes GRPC Server.

This server will manage a pool of Tika Pipes clients.

* Tika Pipes Fetcher CRUD operations
    * Create
    * Read
    * Update
    * Delete
* Fetch + Parse a given Fetch Item

## Building

### Standard Build

```bash
mvn clean install -DskipTests
```

### Building Docker Image

The tika-grpc module includes Docker build support. See [docker-build/README.md](docker-build/README.md) for complete documentation.

#### Quick Start - Docker Build

**Prerequisites:**
First, build Tika from the project root to ensure all dependencies (plugins, parsers) are available:
```bash
cd <tika-root>
mvn clean install -DskipTests
```

**Build with environment variable activation (recommended):**

From tika-grpc directory:
```bash
DOCKER_ID=myusername mvn package
```

Or from project root (builds tika-grpc and dependencies only):
```bash
DOCKER_ID=myusername mvn package -pl :tika-grpc -am
```

**Build with explicit property:**
```bash
mvn package -Dskip.docker.build=false
```

**Manual script execution:**
```bash
export TIKA_VERSION=4.0.0-SNAPSHOT
export DOCKER_ID=myusername
./docker-build/docker-build.sh
```

#### Build and Push to Docker Hub

```bash
DOCKER_ID=myusername \
  mvn package
```

#### Build Multi-Arch

```bash
MULTI_ARCH=true DOCKER_ID=myusername \
  mvn package
```

**Note:** Multi-arch builds automatically push to configured registries. Ensure you're authenticated before building.

#### Build and Push to AWS ECR

```bash
AWS_ACCOUNT_ID=123456789012 AWS_REGION=us-east-1 \
  mvn package
```

#### Build and Push to Azure Container Registry

```bash
AZURE_REGISTRY_NAME=myregistry \
  mvn package
```

For more build options and configuration details, see [docker-build/README.md](docker-build/README.md).

**Note:** For production deployments using official Apache releases (once Tika 4.0.0 is released), use the [tika-grpc-docker](https://github.com/apache/tika-grpc-docker) repository which builds from GPG-signed release artifacts.

