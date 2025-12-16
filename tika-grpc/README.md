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

**Option 1: Build with Maven (recommended)**

```bash
cd tika-grpc
mvn package -Dskip.docker.build=false
```

Or from the project root:
```bash
mvn clean install -DskipTests -Dskip.docker.build=false
```

**Option 2: Run the build script manually**

```bash
export TIKA_VERSION=4.0.0-SNAPSHOT
./docker-build/docker-build.sh
```

#### Build and Push to Docker Hub

```bash
MULTI_ARCH=false DOCKER_ID=myusername PROJECT_NAME=tika-grpc RELEASE_IMAGE_TAG=4.0.0-SNAPSHOT \
  mvn clean package -Dskip.docker.build=false
```

#### Build and Push to AWS ECR

```bash
AWS_ACCOUNT_ID=123456789012 AWS_REGION=us-east-1 \
  mvn clean package -Dskip.docker.build=false
```

#### Build and Push to Azure Container Registry

```bash
AZURE_REGISTRY_NAME=myregistry \
  mvn clean package -Dskip.docker.build=false
```

For more build options and configuration details, see [docker-build/README.md](docker-build/README.md).

