# Tika gRPC Docker Build

This directory contains the Docker build configuration for Apache Tika gRPC server.

## Overview

The Docker image includes:
- Tika gRPC server JAR
- All Tika Pipes plugins (fetchers, emitters, iterators)
- Parser packages (standard, extended, ML)
- OCR support (Tesseract with multiple languages)
- GDAL for geospatial formats
- Common fonts

## Building the Docker Image

### Prerequisites

1. Build Tika from the project root (this builds all modules including plugins):
```bash
cd <tika-root>
mvn clean install -DskipTests
```

### Build Activation

The Docker build can be activated in two ways:

**Option 1: Using environment variables (recommended)**
- Set `DOCKER_ID`, `AWS_ACCOUNT_ID`, or `AZURE_REGISTRY_NAME`
- Maven profiles automatically detect these and enable the build
- No need for `-Dskip.docker.build=false`

**Option 2: Using Maven property**
- Add `-Dskip.docker.build=false` to your Maven command
- Use when you want explicit control or testing

### Building from Tika Root

**Build tika-grpc and dependencies only:**
```bash
DOCKER_ID=myusername \
  mvn clean install -DskipTests -pl :tika-grpc -am
```

**Build entire project:**
```bash
DOCKER_ID=myusername \
  mvn clean install -DskipTests
```

### Building from tika-grpc Directory

#### Controlling Docker Build with Environment Variables

All docker-build.sh environment variables are passed through from your shell. When these variables are set, the Maven profiles automatically activate the Docker build.

**Build and push to Docker Hub:**
```bash
DOCKER_ID=myusername \
  mvn package
```

**Build multi-arch and push to Docker Hub:**
```bash
MULTI_ARCH=true DOCKER_ID=myusername \
  mvn package
```

**Build and push to AWS ECR:**
```bash
AWS_ACCOUNT_ID=123456789012 AWS_REGION=us-east-1 \
  mvn package
```

**Build and push to Azure Container Registry:**
```bash
AZURE_REGISTRY_NAME=myregistry \
  mvn package
```

**Note:** When environment variables are set, you don't need `-Dskip.docker.build=false`. The Maven profiles detect the variables and automatically enable the build.

### Option 2: Run the Docker Build Script Manually

Set the required environment variable and run the script:

```bash
export TIKA_VERSION=4.0.0-SNAPSHOT
./tika-grpc/docker-build/docker-build.sh
```

### Optional Environment Variables

- `TIKA_VERSION`: Maven project version (required)
- `RELEASE_IMAGE_TAG`: Override the default tag (defaults to TIKA_VERSION without -SNAPSHOT)
- `DOCKER_ID`: Docker Hub username to push to Docker Hub
- `AWS_ACCOUNT_ID`: AWS account ID to push to ECR
- `AWS_REGION`: AWS region for ECR (default: us-west-2)
- `AZURE_REGISTRY_NAME`: Azure Container Registry name
- `MULTI_ARCH`: Build for multiple architectures (default: false)
- `PROJECT_NAME`: Docker image name (default: tika-grpc)

### Examples

**Build with Docker Hub using environment variable:**
```bash
DOCKER_ID=myusername \
  mvn package
```

**Build multi-arch with Docker Hub:**
```bash
MULTI_ARCH=true DOCKER_ID=myusername \
  mvn package
```

**Build with AWS ECR:**
```bash
AWS_ACCOUNT_ID=123456789012 AWS_REGION=us-east-1 \
  mvn package
```

**Build with explicit property (for testing/development):**
```bash
DOCKER_ID=myusername mvn package -Dskip.docker.build=false
```

**Manual script build and tag for Docker Hub:**
```bash
export TIKA_VERSION=4.0.0-SNAPSHOT
export DOCKER_ID=myusername
./tika-grpc/docker-build/docker-build.sh
docker push myusername/tika-grpc:4.0.0
```

## Running the Docker Container

```bash
docker run -p 9090:9090 tika-grpc:4.0.0
```

### Environment Variables

- `TIKA_GRPC_MAX_INBOUND_MESSAGE_SIZE`: Maximum inbound message size (default: 104857600)
- `TIKA_GRPC_MAX_OUTBOUND_MESSAGE_SIZE`: Maximum outbound message size (default: 104857600)
- `TIKA_GRPC_NUM_THREADS`: Number of gRPC server threads (default: 4)

### Example with Custom Settings

```bash
docker run -p 9090:9090 \
  -e TIKA_GRPC_MAX_INBOUND_MESSAGE_SIZE=209715200 \
  -e TIKA_GRPC_NUM_THREADS=8 \
  tika-grpc:4.0.0
```

## Included Plugins

The Docker image includes all available Tika Pipes plugins:

### Fetchers/Emitters
- tika-pipes-file-system
- tika-pipes-http
- tika-pipes-s3
- tika-pipes-az-blob
- tika-pipes-gcs
- tika-pipes-jdbc
- tika-pipes-kafka
- tika-pipes-microsoft-graph
- tika-pipes-solr
- tika-pipes-opensearch
- tika-pipes-json
- tika-pipes-csv

### Parser Packages
- tika-parsers-standard-package (included in base JAR)
- tika-parser-scientific-package
- tika-parser-sqlite3-package
- tika-parser-nlp-package

## Tesseract OCR Languages

The following Tesseract language packs are pre-installed:
- English (eng)
- Italian (ita)
- French (fra)
- Spanish (spa)
- German (deu)

Additional languages can be added by modifying the Dockerfile.
