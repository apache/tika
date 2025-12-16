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

1. Build Tika from the project root:
```bash
mvn clean install -DskipTests
```

2. Set the required environment variable:
```bash
export TIKA_VERSION=4.0.0-SNAPSHOT
```

### Run the Docker Build Script

From the project root directory:

```bash
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

Build and tag for Docker Hub:
```bash
export TIKA_VERSION=4.0.0-SNAPSHOT
export DOCKER_ID=myusername
./tika-grpc/docker-build/docker-build.sh
docker push myusername/tika-grpc:4.0.0
```

Build and push to AWS ECR:
```bash
export TIKA_VERSION=4.0.0-SNAPSHOT
export AWS_ACCOUNT_ID=123456789012
export AWS_REGION=us-east-1
./tika-grpc/docker-build/docker-build.sh
```

Build multi-architecture image:
```bash
export TIKA_VERSION=4.0.0-SNAPSHOT
export DOCKER_ID=myusername
export MULTI_ARCH=true
./tika-grpc/docker-build/docker-build.sh
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
