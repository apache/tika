# Running Ignite E2E Tests Locally

## Running with Local tika-grpc Server

To run the Ignite E2E tests using the locally built tika-grpc instead of Docker:

1. **Build tika-grpc first:**
   ```bash
   cd /path/to/tika
   mvn clean install -DskipTests
   ```

2. **Run the e2e test with local server:**
   ```bash
   cd tika-e2e-tests/tika-grpc
   mvn test -Dtika.e2e.useLocalServer=true -Dtest=IgniteConfigStoreTest
   ```

3. **Optional: Use a different port:**
   ```bash
   mvn test -Dtika.e2e.useLocalServer=true -Dtika.e2e.grpcPort=50053 -Dtest=IgniteConfigStoreTest
   ```

4. **Limit documents for faster testing:**
   ```bash
   mvn test -Dtika.e2e.useLocalServer=true -Dcorpa.numdocs=10 -Dtest=IgniteConfigStoreTest
   ```

## System Properties

- `tika.e2e.useLocalServer` - Set to `true` to use local build instead of Docker (default: `false`)
- `tika.e2e.grpcPort` - Port for local server (default: `50052`)
- `govdocs1.fromIndex` - Start index for govdocs1 download (default: `1`)
- `govdocs1.toIndex` - End index for govdocs1 download (default: `1`)
- `corpa.numdocs` - Limit number of documents to process (default: `-1` for all)

## Benefits of Local Testing

- ✅ **Faster iteration** - No Docker image rebuild needed
- ✅ **Better debugging** - Direct access to logs and debugger
- ✅ **Test latest changes** - Uses code from your workspace
- ✅ **Easier troubleshooting** - Can attach debugger to running process

## Running with Docker (Original Method)

To run with Docker Compose (requires Docker image to be built first):

```bash
mvn test -Dtest=IgniteConfigStoreTest
```

Note: This requires the `apache/tika-grpc:local` Docker image to be available.
