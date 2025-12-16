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
mvn clean install -DskipTests
```

### Option 1: Run Docker Build During Maven Package

The Docker build can be triggered automatically during the Maven package phase:

```bash
cd tika-grpc
mvn package -Dskip.docker.build=false
```

Or from the project root:
```bash
mvn clean install -DskipTests -Dskip.docker.build=false
```

**Note:** By default, `skip.docker.build=true` to avoid running Docker builds during normal development.

#### Controlling Docker Build with Environment Variables

All docker-build.sh environment variables are passed through from your shell:

```bash
# Build and push to Docker Hub
MULTI_ARCH=false DOCKER_ID=ndipiazza PROJECT_NAME=tika-grpc RELEASE_IMAGE_TAG=4.0.0-SNAPSHOT \
  mvn clean package -Dskip.docker.build=false
```

```bash
# Build multi-arch and push to Docker Hub
MULTI_ARCH=true DOCKER_ID=myusername PROJECT_NAME=tika-grpc \
  mvn clean package -Dskip.docker.build=false
```

```bash
# Build and push to AWS ECR
AWS_ACCOUNT_ID=123456789012 AWS_REGION=us-east-1 \
  mvn clean package -Dskip.docker.build=false
```

```bash
# Build and push to Azure Container Registry
AZURE_REGISTRY_NAME=myregistry \
  mvn clean package -Dskip.docker.build=false
```

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

**Maven build with Docker Hub (recommended):**
```bash
MULTI_ARCH=false DOCKER_ID=ndipiazza PROJECT_NAME=tika-grpc RELEASE_IMAGE_TAG=4.0.0-SNAPSHOT \
  mvn clean package -Dskip.docker.build=false
```

**Maven build with multi-arch:**
```bash
MULTI_ARCH=true DOCKER_ID=ndipiazza PROJECT_NAME=tika-grpc \
  mvn clean package -Dskip.docker.build=false
```

**Maven build with AWS ECR:**
```bash
AWS_ACCOUNT_ID=123456789012 AWS_REGION=us-east-1 \
  mvn clean package -Dskip.docker.build=false
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
