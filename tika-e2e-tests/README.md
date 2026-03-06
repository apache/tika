# Apache Tika End-to-End Tests

End-to-end integration tests for Apache Tika components.

## Overview

This module contains standalone end-to-end (E2E) tests for various Apache Tika distribution formats and deployment modes. Unlike unit and integration tests in the main Tika build, these E2E tests validate complete deployment scenarios using Docker containers and real-world test data.

**Note:** This module is included in the main Tika build under the `e2e` Maven profile (`-Pe2e`). Run `mvn test -Pe2e` from the repo root to execute these tests.

## Test Modules

- **tika-grpc** - E2E tests for tika-grpc server

## Prerequisites

- Java 17 or later
- Maven 3.6 or later
- Internet connection (only when running tests that download external corpora, e.g. with `-Dtika.e2e.useGovdocs=true`)
- Docker and Docker Compose (only required for Docker Compose mode; not needed for the default local-server mode)

## Building All E2E Tests

From this directory:

```bash
../mvnw clean install
```

## Running All E2E Tests

```bash
../mvnw test
```

## Running Specific Test Module

```bash
cd tika-grpc
../../mvnw test
```

## Why Standalone?

The E2E tests are kept separate from the main build because they:

- Have different build requirements (Docker, Testcontainers)
- Take significantly longer to run than unit tests
- Require external resources (test corpora, Docker images)
- Can be run independently in CI/CD pipelines
- Allow developers to run them selectively

## Integration with CI/CD

These tests can be integrated into the release pipeline as a separate step.

## License

Licensed under the Apache License, Version 2.0. See the main Tika LICENSE.txt file for details.
