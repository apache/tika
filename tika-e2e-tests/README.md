# Apache Tika End-to-End Tests

End-to-end integration tests for Apache Tika components.

## Overview

This module contains standalone end-to-end (E2E) tests for various Apache Tika distribution formats and deployment modes. Unlike unit and integration tests in the main Tika build, these E2E tests validate complete deployment scenarios using Docker containers and real-world test data.

**Note:** This module is intentionally **NOT** included in the main Tika parent POM. It is designed to be built and run independently to avoid slowing down the primary build process.

## Test Modules

- **tika-grpc** - E2E tests for tika-grpc server
- **tika-server** - E2E tests for tika-server (REST API) _(coming soon)_
- **tika-cli** - E2E tests for tika CLI _(coming soon)_

## Prerequisites

- Java 17 or later
- Maven 3.6 or later
- Docker and Docker Compose
- Internet connection (for downloading test documents)

## Building All E2E Tests

From this directory:

```bash
mvn clean install
```

## Running All E2E Tests

```bash
mvn test
```

## Running Specific Test Module

```bash
cd tika-grpc
mvn test
```

## Why Standalone?

The E2E tests are kept separate from the main build because they:

- Have different build requirements (Docker, Testcontainers)
- Take significantly longer to run than unit tests
- Require external resources (test corpora, Docker images)
- Can be run independently in CI/CD pipelines
- Allow developers to run them selectively

## Integration with CI/CD

These tests can be integrated into the release pipeline as a separate step. See [TIKA-4603](https://issues.apache.org/jira/browse/TIKA-4603) for the integration roadmap.

## Related JIRA Tickets

- [TIKA-4599](https://issues.apache.org/jira/browse/TIKA-4599) - Add E2E tests for Tika (parent ticket)
- [TIKA-4600](https://issues.apache.org/jira/browse/TIKA-4600) - Add E2E tests for tika-grpc
- [TIKA-4601](https://issues.apache.org/jira/browse/TIKA-4601) - Add E2E tests for tika-server
- [TIKA-4602](https://issues.apache.org/jira/browse/TIKA-4602) - Add E2E tests for tika CLI
- [TIKA-4603](https://issues.apache.org/jira/browse/TIKA-4603) - Integrate E2E tests into release pipeline

## License

Licensed under the Apache License, Version 2.0. See the main Tika LICENSE.txt file for details.
