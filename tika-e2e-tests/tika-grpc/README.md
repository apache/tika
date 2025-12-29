# Tika gRPC End-to-End Tests

End-to-end integration tests for Apache Tika gRPC Server using Testcontainers.

## Overview

This test module validates the functionality of Apache Tika gRPC Server by:
- Starting a tika-grpc Docker container using Docker Compose
- Loading test documents from the GovDocs1 corpus
- Testing various fetchers (filesystem, Ignite config store, etc.)
- Verifying parsing results and metadata extraction

## Prerequisites

- Java 17 or later
- Maven 3.6 or later
- Docker and Docker Compose
- Internet connection (for downloading test documents)
- Docker image `apache/tika-grpc:local` (see below)

## Building

```bash
mvn clean install
```

## Running Tests

### Run all tests

```bash
mvn test
```

### Run specific test

```bash
mvn test -Dtest=FileSystemFetcherTest
mvn test -Dtest=IgniteConfigStoreTest
```

### Configure test document range

By default, only the first batch of GovDocs1 documents (001.zip) is downloaded. To test with more documents:

```bash
mvn test -Dgovdocs1.fromIndex=1 -Dgovdocs1.toIndex=5
```

This will download and test with batches 001.zip through 005.zip.

### Limit number of documents to process

To limit the test to only process a specific number of documents (useful for quick testing):

```bash
mvn test -Dcorpa.numdocs=10
```

This will process only the first 10 documents instead of all documents in the corpus. Omit this parameter or set to -1 to process all documents.

**Examples:**

```bash
# Test with just 5 documents
mvn test -Dcorpa.numdocs=5

# Test with 100 documents from multiple batches
mvn test -Dgovdocs1.fromIndex=1 -Dgovdocs1.toIndex=2 -Dcorpa.numdocs=100

# Test all documents (default behavior)
mvn test
```

## Test Structure

- `ExternalTestBase.java` - Base class for all tests
  - Manages Docker Compose containers
  - Downloads and extracts GovDocs1 test corpus
  - Provides utility methods for gRPC communication

- `filesystem/FileSystemFetcherTest.java` - Tests for filesystem fetcher
  - Tests fetching and parsing files from local filesystem
  - Verifies all documents are processed

- `ignite/IgniteConfigStoreTest.java` - Tests for Ignite config store
  - Tests configuration storage and retrieval via Ignite
  - Validates config persistence

## GovDocs1 Test Corpus

The tests use the [GovDocs1](https://digitalcorpora.org/corpora/govdocs) corpus, a collection of real-world documents from US government websites. Documents are automatically downloaded and cached in `target/govdocs1/`.

## Docker Image

The tests expect a Docker image named `apache/tika-grpc:local`. Build one using:

```bash
cd /path/to/tika-docker/tika-grpc
./build-from-branch.sh -l /path/to/tika -t local
```

Or build from the main Tika repository and tag it:

```bash
cd /path/to/tika
mvn clean install -DskipTests
cd tika-grpc
# Follow tika-grpc Docker build instructions
```

## Sample Configurations

The `sample-configs/` directory contains example Tika configuration files for various scenarios:
- `customocr/` - Custom OCR configurations
- `grobid/` - GROBID PDF parsing configuration
- `ignite/` - Ignite config store examples
- `ner/` - Named Entity Recognition configuration
- `vision/` - Computer vision and image analysis configs

## Logs

Test logs are output to console. Docker container logs are also captured and displayed.

## Troubleshooting

**Container fails to start:**
- Ensure Docker is running
- Check that port 50052 is available
- Verify the `apache/tika-grpc:local` image exists: `docker images | grep tika-grpc`

**Tests timeout:**
- Increase timeout in test class
- Check Docker container logs for errors
- Ensure sufficient memory is available to Docker

**Download failures:**
- Check internet connection
- GovDocs1 files are downloaded from digitalcorpora.org
- Downloaded files are cached in `target/govdocs1/`

## Related JIRA

- [TIKA-4600](https://issues.apache.org/jira/browse/TIKA-4600) - Add E2E tests for tika-grpc

## License

Licensed under the Apache License, Version 2.0. See the main Tika LICENSE.txt file for details.
