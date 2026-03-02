# Tika gRPC End-to-End Tests

End-to-end integration tests for Apache Tika gRPC Server.

## Overview

This test module validates the functionality of Apache Tika gRPC Server by:
- Starting a local tika-grpc server using the Maven exec plugin (default)
- Parsing small committed test fixture documents
- Testing various fetchers (filesystem, Ignite config store, etc.)
- Verifying parsing results and metadata extraction

## Prerequisites

- Java 17 or later
- Maven 3.6 or later
- Docker and Docker Compose (only required when using `tika.e2e.useLocalServer=false`)

## Building

```bash
../../mvnw clean install
```

## Running Tests

### Run all tests (default: local server mode, committed fixtures)

```bash
../../mvnw test
```

### Run specific test

```bash
../../mvnw test -Dtest=FileSystemFetcherTest
../../mvnw test -Dtest=IgniteConfigStoreTest
```

### Test with the full GovDocs1 corpus (opt-in)

By default tests use small committed fixture files. To run against the real GovDocs1 corpus, set `govdocs1.fromIndex` to trigger a download:

```bash
../../mvnw test -Dgovdocs1.fromIndex=1 -Dgovdocs1.toIndex=1
```

To test with more documents, increase the range or set `corpa.numdocs`:

```bash
../../mvnw test -Dgovdocs1.fromIndex=1 -Dgovdocs1.toIndex=5 -Dcorpa.numdocs=100
```

## Test Structure

- `ExternalTestBase.java` - Base class for all tests
  - Manages local server or Docker Compose containers
  - Provides utility methods for gRPC communication

- `filesystem/FileSystemFetcherTest.java` - Tests for filesystem fetcher
  - Tests fetching and parsing files from local filesystem

- `ignite/IgniteConfigStoreTest.java` - Tests for Ignite config store
  - Tests configuration storage and retrieval via Ignite

## Docker Mode

To run against a Docker Compose deployment instead of a local server:

```bash
../../mvnw test -Dtika.e2e.useLocalServer=false -Dtika.docker.compose.file=/path/to/docker-compose.yml
```

The Docker image `apache/tika-grpc:local` can be built from the Tika root:

```bash
cd /path/to/tika
./mvnw clean install -DskipTests
# then follow tika-grpc Docker build instructions
```

## License

Licensed under the Apache License, Version 2.0. See the main Tika LICENSE.txt file for details.
