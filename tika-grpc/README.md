# Tika Pipes gRPC Server

The Tika Pipes gRPC server exposes fetcher and iterator management and document
fetch-and-parse over gRPC. It runs a pool of Tika Pipes worker processes and routes
requests through the configured fetchers.

* Tika Pipes Fetcher CRUD operations
    * Create
    * Read
    * Update
    * Delete
* Fetch + Parse a given Fetch Item
* Parse bytes sent with the request (ParseBytes, v2 only)

> **Security note:** runtime fetcher/iterator management — mutations (Create/Update/Delete)
> and reading stored configs back (Read), which may contain secrets — plus per-request parse
> configuration are **disabled by default**. Enable them explicitly via
> `allowComponentManagement` / `allowPerRequestConfig` in the `grpc` section of your
> tika-config; with management off, the Read RPCs return only component id and class, never
> the config. See the
> [Tika gRPC security configuration docs](../docs/modules/ROOT/pages/using-tika/grpc/index.adoc).

## v1 and v2 services

The server exposes two gRPC services on the same port:

- **`tika.Tika` (v1)**: fetcher and iterator management, and FetchAndParse replies
  that return metadata as a `fields` map of strings.
- **`org.apache.tika.grpc.v2.TikaV2Service` (experimental)**: FetchAndParse and
  ParseBytes, which return a typed `org.apache.tika.grpc.v2.Document`. Fetchers are
  still managed through v1.

A `Document` holds:

1. **Common metadata** in typed fields on `DocumentMetadata`: the Dublin Core
   properties most formats share (title, authors, description, keywords, languages,
   publishers, identifiers, dates, rights).
2. **All other metadata** in `extra` (`repeated MetadataField`), such as PDF
   permissions, EXIF/GPS or OOXML core properties. A value is typed when Tika
   declares a type for its key, and a string otherwise.
3. **The envelope**: the detected `content_type`, the `origin` (filename, byte size,
   parser, and the source SHA-256 when a digester is configured) and a typed
   `ParseStatus`.

The extracted text (`tk:content`) is not part of the `Document`.

[docs/v2-document-contract.md](docs/v2-document-contract.md) explains the design: how
values get their types, which outcomes are a Document status and which a gRPC status,
and how v1 stays frozen.

### v2 errors, overload and cancellation

- **A failed document is a Document.** A document that could not be fetched or parsed
  comes back as gRPC OK with `ParseStatus.status = STATUS_FAILED`, and `pipes_status`
  says why. That includes a worker that timed out, ran out of memory or crashed on it:
  sending the same bytes again would most likely do the same.
- **A server that cannot take the request answers with a gRPC status**, so retry
  policies keyed on status codes work: `RESOURCE_EXHAUSTED` when no pipes worker became
  free within `pipes.maxWaitForClientMillis`, `UNAVAILABLE` when the pipes runtime or a
  fetcher failed to start, `NOT_FOUND` for an unknown fetcher.
- **Errors carry one line by default.** `ParseStatus.errors` holds the exception and its
  message, cut to 1024 characters, not the server's stack trace. Set
  `parse-context.exception-reporting` in the tika-config to choose the detail yourself;
  v2 then passes the formatted text through as is.
- **Cancelling.** A call that the client cancels, or whose deadline expires, stops
  waiting for a worker at once. A parse already running on a worker finishes, and its
  result is dropped. Set `"grpc": {"cancelRunningParse": true}` to interrupt it instead;
  in the default per-client server mode that restarts the worker JVM, so each cancelled
  or expired call then costs a JVM start.

v1 replies are unchanged: v1 still reports every outcome in-band, in `status`.

Supporting artifacts live in sibling Maven modules (also listed in `tika-bom`):

| Module | Role |
|--------|------|
| `tika-grpc-api` | Protobuf definitions (`org.apache.tika.grpc.v2`: `document.proto`, `tika_v2.proto`), generated message and service stubs, bundled `FileDescriptorSet` under `META-INF/` |
| `tika-grpc-mapper` | Maps Tika `Metadata` to `Document` with `DocumentTransformer` classes |
| `tika-grpc` | gRPC service implementation (this module): v1 + v2 |

[tika-grpc-api/README.md](../tika-grpc-api/README.md) describes the `Document` in more detail.
Mapper tests live under `tika-grpc-mapper/src/test/java`.

Build the API and mapper with the rest of the reactor:

```bash
./mvnw clean test -pl tika-grpc-api,tika-grpc-mapper,tika-grpc
```

## ParseBytes

`TikaV2Service.ParseBytes` parses bytes that the caller sends in the request. Tika
fetches nothing, so no fetcher has to be registered. The response carries the same
`Document` that FetchAndParse returns.

[docs/parse-bytes.md](docs/parse-bytes.md) explains why it exists, how the bytes reach
the worker, and how memory is bounded.

| Request field | Meaning |
|---------------|---------|
| `content` | The bytes to parse. Required, at most `parseBytesMaxContentBytes`. |
| `resource_name` | File name, used as a hint for type detection and returned as `origin.filename`. |
| `correlation_id` | Any string, returned as `Document.id` and `ParseBytesResponse.correlation_id`. |
| `source_uri`, `effective_uri`, `base_uri` | URIs the caller records for the bytes, such as the requested URL, the URL after redirects and the base URL for relative links. Returned in `origin`; Tika does not use them. |
| `truncated` | Set it when the bytes were cut short, for example at a crawler's size limit. Returned as `origin.truncated`. |
| `parse_context_json` | Parse settings for this request, refused with `PERMISSION_DENIED` unless `allowPerRequestConfig` is on. |

Content up to `pipes.maxInlineBytes` (10 MiB by default) goes to the worker JVM with
the request. Larger content goes through a temporary file that the server deletes
after the parse. ParseBytes and FetchAndParse share the same workers (see
[Concurrency](#concurrency)).

An empty `content` returns `INVALID_ARGUMENT`, and `content` above
`parseBytesMaxContentBytes` returns `RESOURCE_EXHAUSTED`. A document the parser
fails on still returns a `Document`, with the outcome in its `status`.

At most `parseBytesMaxConcurrent` ParseBytes requests (twice `pipes.numClients` by
default) are in progress at once, parsing or waiting for a worker. One more is refused
at once with `RESOURCE_EXHAUSTED`, before its content is written to a temporary file,
so a burst of large requests cannot pile up in heap or fill `java.io.tmpdir` while
they wait.

### Message size

By default the server's inbound message limit is the ParseBytes content limit plus
1 MiB for the request's other fields (257 MiB with the defaults), instead of gRPC's
own 4 MiB default. Set it with `maxInboundMessageBytes` in the `grpc` section. To
take 1 GiB WARCs, raise both limits, leaving the inbound one 1 MiB above the content:

```json
{
  "grpc": {
    "maxInboundMessageBytes": 1074790400,
    "parseBytesMaxContentBytes": 1073741824
  }
}
```

The limit applies to every RPC on the server. The replies of
[external parsers](#external-parsers-experimental-tika-4771) have their own
`maxReplyBytes` instead. gRPC holds each
request in memory in full before Tika sees it, so each request in flight takes at
least as much heap as its size, and gRPC does not limit how many it reads at once;
`parseBytesMaxConcurrent` only bounds how long ParseBytes holds them. Size the heap for the messages you expect. When the limit is
configured, the server logs a warning at startup if the maximum heap is below
three times the limit.

The limit only covers what tika-grpc receives. Clients set their own limit on what
they receive, and stock gRPC clients keep the 4 MiB default, so a client that
expects large replies (a ParseBytes reply with external results, say) has to raise
its own `maxInboundMessageSize`.

`parseBytesMaxContentBytes` (256 MiB by default, must be positive) limits the
`content` field of ParseBytes; the default fits a web-crawl WARC of 100 MB or more.
gRPC applies the inbound limit to the whole request first. When
`maxInboundMessageBytes` is set at or below `parseBytesMaxContentBytes`, the server
logs a warning at startup.

The defaults, and what they cost:

| Setting | Default | Cost |
|---|---|---|
| `parseBytesMaxContentBytes` | 256 MiB | the largest ParseBytes content |
| `maxInboundMessageBytes` | content limit + 1 MiB (257 MiB) | heap per request gRPC is reading; about 3x this for one request |
| `parseBytesMaxConcurrent` | 2 x `pipes.numClients` (8 when `numClients` is derived) | each admitted request holds its content in heap until its reply, and its spool file until its parse ends: up to 8 x 256 MiB = 2 GiB of each |

At startup the server logs a warning when the maximum heap is below three times the
inbound limit, or below `parseBytesMaxConcurrent` x `parseBytesMaxContentBytes`, and
when the spool directory (under `java.io.tmpdir`) has less free space than that
product. On a small host, lower `parseBytesMaxConcurrent` or
`parseBytesMaxContentBytes` rather than the heap warnings' advice to raise the heap.

### External parsers (experimental, TIKA-4771)

[docs/external-parsers-manual.md](docs/external-parsers-manual.md) is the user
manual: setup, configuration, reading the result, writing an adapter and
troubleshooting. [docs/external-parser-adapters.md](docs/external-parser-adapters.md)
explains the design and the trust model.

ParseBytes can also send the bytes to external gRPC parsers, written in any
language, and add what they found to Tika's Document. Each parser needs an
**adapter jar** on tika-grpc's classpath: the parser's generated stubs plus one
`org.apache.tika.grpc.adapter.ExternalParserAdapter` (module
`tika-grpc-adapter-api`), listed in `META-INF/services`. The adapter names the
content types, the unary method and its key prefix, checks its options, builds the
typed request, and turns the typed reply into an `AdapterContribution`. There is
no gRPC reflection and no JSON request assembly.

An adapter can contribute two things, and the server enforces it whatever the
adapter wrote:

- **Child documents** (a WARC's records, say), each with a path the adapter
  gives it, returned in `Document.embedded` in the adapter's order and typed
  through Tika's Property model like Tika's own metadata. A path must start with
  `/` and be unique for that producer; a child without one is dropped with a
  warning. A child the parser reported errors for has `STATUS_PARTIAL` and the
  errors.
- **Parent keys under its own prefix** (`warc:` for FastWARC), added to
  `Document.extra` only where Tika's parse recorded no key of that name, whether
  Tika's Document carries it as a typed field or in `extra`. Other parent keys are
  dropped with a warning.

Tika's own parent fields, status, origin and id are never changed by an adapter.
Each adapter also gets one `ExtensionResult` in `Document.extensions` with its
status, warnings, timing and child counts:

| Result | Meaning |
|--------|---------|
| `STATUS_OK` | Everything the adapter contributed was merged. |
| `STATUS_PARTIAL` | The parser stopped part way (a broken record); what it read before, children and parent keys, was merged, and the first warning says why. |
| `STATUS_FAILED` | Nothing was merged: the call failed or was cancelled, the reply was over `maxReplyBytes`, the adapter threw (an `Error` such as `NoClassDefFoundError` included; only an `OutOfMemoryError` or other VM error is rethrown) or returned nothing, or the adapter said the parse failed. The reason is the first line of the error, at most 1024 characters. A failed result never carries the raw reply. |
| `STATUS_SKIPPED` | The document was over `maxContentBytes` and was not sent. |

`Document.embedded` lists each producer's documents together, in the order the
producer made them, producers in the order of `externalParsers`. Tika's own
embedded documents are not in it yet; when they are, they will come first under
`producer_id` `tika`, and a WARC parsed by both Tika and FastWARC will list its
records once under each producer. `(producer_id, path)` is unique.

Adapters run inside the tika-grpc server's JVM, not in the forked parse workers.
They are trusted code: an adapter that hangs or runs out of memory does so in the
server. The limits below bound what the server keeps from an adapter, and the
contribution builder applies them as the adapter adds to it, but they do not bound
the adapter's own work.

At startup the server reads each adapter's producer id, method, content types and
key prefix once and checks them: the method must be unary, there must be at least
one type, and the prefix must end with `:` or `-`, must not overlap one of Tika's
own namespaces (`tk:`, `X-TIKA`, `dc:`, `dcterms:`, `Content-`, `pdf:`, `xmp`,
`mp4:`, `audio:`..., the list is `AdapterMerge.TIKA_NAMESPACES`, built from the
`Property` constants of tika-core's metadata classes plus the namespaces parsers write
without one), and must not overlap another configured adapter's (`warc:` and
`WARC:http:` cannot both be configured); prefixes are compared ignoring case. The
adapter checks its `options`. Any problem, or an entry whose `producerId` has no
usable adapter jar, stops the server. An adapter jar on the classpath that no entry
names is never used: if it fails to load, or two jars share a producer id, the server
logs it and starts anyway. `producerId` is 1 to 64 of `a-z`, `0-9`, `.`, `_` and
`-`.

Adapters are separate projects. FastWARC (`fastwarc-grpc`, TIKA-4951) running next to
tika-grpc, with its adapter jar on the classpath:

```json
{
  "grpc": {
    "externalParsers": [
      {"producerId": "fastwarc", "target": "localhost:50060", "plaintext": true}
    ]
  }
}
```

| Key | Meaning |
|-----|---------|
| `producerId` | The adapter to use, by its producer id. Unique in the list. Required. |
| `target` | The parser's address, `host:port`. Required. |
| `plaintext` | Connect without TLS. `false` by default: the channel uses TLS. |
| `tls` | `caFile` (PEM) to trust a private CA instead of the JVM's defaults; `certFile` and `keyFile` (PEM) for mutual TLS. Not with `plaintext`. |
| `timeoutMs` | Deadline for each call, 60000 by default. |
| `maxReplyBytes` | Largest reply read from the parser, 128 MiB by default; a larger one fails the result. |
| `maxContentBytes` | Larger documents are not sent, and the result is `STATUS_SKIPPED`. Not set by default. |
| `maxChildren` | Most embedded documents one reply adds, 100000 by default. |
| `maxEmbeddedBytes` | Most bytes those embedded documents add, 64 MiB by default. |
| `maxKeysPerDocument` | Most metadata keys per embedded document, and on the parent, 256 by default. |
| `maxValueChars` | Values and paths are cut to this many characters, 8192 by default. |
| `keepRawReply` | Also put the parser's whole reply in the result's `payload` as an `Any`. `false` by default. |
| `options` | The adapter's own settings, checked at startup. FastWARC takes `verifyDigests` and `quirksMode`, both booleans. |

Warnings are capped at 100 per result, 1000 characters each. What the external
parsers add to a ParseBytes Document, all together, is capped by
`grpc.externalParsersMaxAddedBytes` (96 MiB by default). The parsers share it: each
may add embedded documents up to what the ones before it left, and the rest of its
list is cut there and marked truncated. If the total is still over (raw replies or
parent keys), raw replies are dropped first, then embedded documents from the end,
then parent keys from the end. Tika's own Document and the result slots are not
counted, and Tika's Document is never cut, so a reply can be Tika's Document plus up
to that much and the slots.

**Memory.** A ParseBytes request keeps its `parseBytesMaxConcurrent` place until its
reply is built, external parsers included, so at most that many requests hold their
content, a reply of up to `maxReplyBytes` per parser, and the composed Document at
once. With the defaults and one parser that is up to 256 MiB of content, a 128 MiB
reply and 96 MiB added per request, about 480 MiB; eight admitted requests (the
derived `parseBytesMaxConcurrent`) approach 4 GiB, so size the heap for it or lower
`parseBytesMaxConcurrent`. A request over the limit gets `RESOURCE_EXHAUSTED`. With no client deadline,
the longest a request can take is Tika's parse plus the `timeoutMs` of every parser
that takes its type.

When a limit cuts the embedded list short, or the parser reported more children
than were kept, the result has `truncated: true` with `embedded_reported` and
`embedded_kept`, so a consumer does not take a partial list for the whole archive.
The first child a limit stops ends the list, so the children kept are the
producer's, in order, up to that point, less any dropped for an unusable or
repeated path (each counted in a warning). A contribution an adapter built with
other limits than the ones tika-grpc gave it is refused.

**How a `.warc.gz` reaches FastWARC.** Routing matches the base type of the
Document's `content_type` against the adapter's types; the FastWARC adapter takes
`application/warc` and `application/warc+gz`. A gzipped WARC gets
`application/warc+gz` by its bytes, not its name: Tika's default detection includes
`GZipSpecializationDetector` (tika-parser-pkg-module), which looks inside the gzip
stream and reports `application/warc+gz` when a WARC header is there, so
`crawl.warc.gz`, `crawl.gz` and an unnamed upload all route. When Tika's own parse
fails before recording a type, ParseBytes detects one itself from the bytes (magic,
then the same detector on the first 1 KiB of compressed bytes) before routing.
There is no filename fallback: a file named `.warc.gz` that is not a WARC does not
route.

The calls run one after another, after Tika's parse. Each call's deadline is the
earlier of the client's deadline and the entry's `timeoutMs`. A client that cancels
the ParseBytes call cancels the call in progress, nothing more is mapped or merged,
and parsers not yet called are not called (their results are `STATUS_FAILED`). Each
call gets its own copy of the entry's `options`. FetchAndParse does not call external parsers,
because the server never holds the fetched bytes.

## Distribution and Maven Artifact

**tika-grpc is designed to be run via Docker — it is not a standalone runnable artifact published to Maven Central.**

The Maven artifact for `tika-grpc` is a thin JAR (~238KB) containing only the compiled classes and resources.
It does **not** include a bundled distribution ZIP or any plugin ZIPs. This was an intentional change (TIKA-4723)
to avoid uploading hundreds of megabytes of native libraries and plugin bundles to Nexus on every release.

- The runnable Docker image is built using `tika-grpc/docker-build/docker-build.sh`, which assembles
  the full classpath and plugins at Docker build time — not during `mvn package`.
- The `tika-pipes-plugins` (fetchers, emitters, iterators) are also **not** attached as Maven artifacts.
  They are packaged as pf4j ZIP plugins and included in the Docker image.
- To build the full distribution ZIP locally (e.g. for debugging outside Docker), use the `docker` Maven profile:
  ```bash
  mvn package -Pdocker -pl tika-grpc
  ```
  This produces `tika-grpc/target/tika-grpc-<version>.zip` but does **not** deploy it to Nexus.

## Concurrency

FetchAndParse and ParseBytes run on a pool of forked worker JVMs sized by
`pipes.numClients` (when unset, derived from host cores, at most 4). A call that
cannot get a worker within `pipes.maxWaitForClientMillis` (default 60s) returns
the in-band status `CLIENT_UNAVAILABLE_WITHIN_MS`, which means the server is at
capacity. With `pipes.useSharedServer: true` the workers share one JVM instead.

## Quick Start - Development Mode

The fastest way to run tika-grpc in development mode with plugin hot-reloading:

```bash
# 1. Build Tika and all plugins (from tika project root)
./mvnw clean install -DskipTests

# 2. Run in development mode (from tika-grpc directory)
cd tika-grpc
./run-dev.sh
```

This will:
- Automatically enable `tika.plugin.dev.mode=true`
- Load plugins from `../tika-pipes/tika-pipes-plugins/*/target/classes` directories
- Start gRPC server on port 50052
- Use the `dev-tika-config.json` configuration

You can also specify a custom config file:
```bash
./run-dev.sh my-custom-config.json
```

## Plugin Development Mode

When developing plugins, you can use pf4j's development mode to load plugins directly from their `target/classes` directories without needing to package them as ZIP files. This significantly speeds up the development cycle.

### Enabling Development Mode

Set one of the following:

**System Property:**
```bash
-Dtika.plugin.dev.mode=true
```

**Environment Variable:**
```bash
export TIKA_PLUGIN_DEV_MODE=true
```

**Maven Dev Profile:** (Recommended)
```bash
./mvnw exec:java -Pdev -Dconfig.file=dev-tika-config.json
```

### Configuration Example

The `dev-tika-config.json` file shows how to configure plugin-roots with relative paths:

```json
{
  "plugin-roots": [
    "../tika-pipes/tika-pipes-plugins/tika-pipes-file-system/target/classes",
    "../tika-pipes/tika-pipes-plugins/tika-pipes-http/target/classes",
    "../tika-pipes/tika-pipes-plugins/tika-pipes-s3/target/classes"
  ],
  "fetchers": [
    {
      "fs": {
        "myFetcher": {
          "basePath": "/tmp/input"
        }
      }
    }
  ]
}
```

**Note:** Paths are relative to the `tika-grpc` directory (where you run the server from). You can also use absolute paths if preferred:

```json
{
  "plugin-roots": [
    "/home/user/tika/tika-pipes/tika-pipes-plugins/tika-pipes-s3/target/classes",
    "/home/user/tika/tika-pipes/tika-pipes-plugins/tika-pipes-file-system/target/classes"
  ]
}
```

### Development Workflow

1. **Build the plugin modules** (only needed once or when dependencies change):
   ```bash
   cd tika-pipes/tika-pipes-plugins
   ./mvnw clean compile
   ```

2. **Run in development mode** using the convenience script:
   ```bash
   cd tika-grpc
   ./run-dev.sh
   ```

3. **Make code changes** to your plugin

4. **Recompile just the changed plugin** (much faster than full rebuild):
   ```bash
   # From the project root
   ./mvnw compile -pl :tika-pipes-s3
   ```

5. **Restart the server** - changes are immediately picked up

### What Happens in Development Mode

- **ZIP extraction is skipped** - TikaPluginManager doesn't try to unzip plugins
- **Plugins loaded from directories** - pf4j loads classes directly from `target/classes`
- **Each plugin directory must contain** `plugin.properties` in the root (automatically present after `./mvnw compile`)
- **Dependencies are available** - The dev profile includes all plugin modules as dependencies

### Expected Directory Structure

When pointing to `target/classes`, pf4j expects this structure:

```
tika-pipes-s3/target/classes/
├── plugin.properties          # Required: plugin metadata
├── META-INF/
│   └── extensions.idx         # Generated by pf4j annotation processor
└── org/
    └── apache/
        └── tika/
            └── pipes/
                └── fetcher/
                    └── s3/
                        ├── S3Fetcher.class
                        └── S3FetcherFactory.class
```

### IntelliJ IDEA Setup

For IntelliJ IDEA development, here's a complete workflow for developing plugins:

#### Initial Setup

1. **Import the Tika project** as a Maven project in IntelliJ

2. **Build all plugins once** (required before first run):
   - Open the Maven tool window: View → Tool Windows → Maven
   - Navigate to: **tika-pipes-plugins** (the parent module)
   - Right-click → Lifecycle → **compile**
   - Or use terminal:
     ```bash
     cd tika-pipes/tika-pipes-plugins
     ./mvnw clean compile
     ```

3. **Create a Run Configuration** for tika-grpc:
   - Go to: Run → Edit Configurations
   - Click `+` → Application
   - **Name:** Tika gRPC Server (Dev Mode)
   - **Main class:** `org.apache.tika.pipes.grpc.TikaGrpcServer`
   - **VM options:** `-Dtika.plugin.dev.mode=true`
   - **Program arguments:** `--config dev-tika-config.json`
   - **Working directory:** `$PROJECT_DIR$/tika-grpc`
   - **Use classpath of module:** `tika-grpc`
   - Click OK

4. **Run the configuration** - You should see output like:
   ```
   INFO  TikaPluginManager running in DEVELOPMENT mode
   INFO  PF4J version 3.14.0 in 'development' mode
   INFO  Plugin 'tika-pipes-file-system-plugin@4.0.0-SNAPSHOT' resolved
   INFO  Plugin 'tika-pipes-s3-plugin@4.0.0-SNAPSHOT' resolved
   ...
   INFO  Server started, listening on 50052
   ```

#### Development Workflow: Making Plugin Changes

**Scenario:** You want to modify the S3 Fetcher plugin

1. **Navigate to the plugin code:**
   ```
   tika-pipes/tika-pipes-plugins/tika-pipes-s3/src/main/java/
   ```

2. **Make your code changes** in the plugin source files
   - Edit fetcher, emitter, or iterator classes
   - Modify configuration handling
   - Add new features

3. **Build just the modified plugin module:**
   - In Project view, right-click on **tika-pipes-s3** module
   - Select **Build Module 'tika-pipes-s3.main'**
   - Or use terminal:
     ```bash
     # From project root
     ./mvnw compile -pl :tika-pipes-s3
     ```
   - Build time: ~5-10 seconds (much faster than full rebuild!)

4. **Restart the tika-grpc server:**
   - **You MUST restart** for changes to take effect
   - Click the stop button (red square) in IntelliJ
   - Click run button (green arrow) to restart
   - PF4J loads plugins at startup - no hot reload without restart

5. **Verify your changes:**
   - Check the server logs for successful plugin loading
   - Test your changes via gRPC client
   - Make additional changes and repeat steps 3-4 as needed

#### Tips for Faster Development

**Use IntelliJ's Build shortcuts:**
- `Ctrl+F9` (Windows/Linux) or `Cmd+F9` (Mac) - Build project/module
- Configure to build only changed files for even faster iteration

**Debug mode:**
- Use "Debug" instead of "Run" to set breakpoints in plugin code
- Step through plugin initialization and execution
- Inspect variables and plugin state

**Multiple terminal windows:**
- Terminal 1: Run `./run-dev.sh` 
- Terminal 2: Quick builds with `./mvnw compile -pl :plugin-name` from project root
- Restart server with Ctrl+C and up-arrow to re-run

**Keyboard shortcut for restart:**
- Set up: Preferences → Keymap → Search for "Rerun"
- Assign a hotkey to "Rerun 'Tika gRPC Server (Dev Mode)'"
- Quick restart: One keystroke instead of mouse clicking

#### Common Issues and Solutions

**"ClassNotFoundException" after changes:**
- Make sure you ran **Build Module** on the changed plugin
- Check that `target/classes` was updated (look at file timestamps)
- Do a clean compile: `./mvnw clean compile`

**Changes not visible after restart:**
- Verify you built the correct module (check module name in IntelliJ)
- Ensure the config file points to the right `target/classes` directory
- Check that development mode is enabled (look for "DEVELOPMENT mode" in logs)

**Server won't start:**
- Build ALL plugins first: `./mvnw compile -pl tika-pipes/tika-pipes-plugins`
- Check that `dev-tika-config.json` exists in the working directory
- Verify working directory is set to `$PROJECT_DIR$/tika-grpc`

**Want to see what changed:**
- Before restarting, add a log statement to verify your change
- Check the plugin's version in startup logs
- Use debugger to set breakpoint in modified code

#### Alternative: Maven Exec from IntelliJ

Instead of creating an Application run configuration, you can also run the Maven goal directly:

1. **Open Maven tool window**
2. **Navigate to:** tika-grpc → Profiles → dev (check the checkbox)
3. **Navigate to:** tika-grpc → Plugins → exec → exec:java
4. **Right-click exec:java** → Run
5. **To use custom config:** Edit the run configuration and add:
   ```
   -Dconfig.file=my-config.json
   ```

Same workflow applies: make changes → build module → restart Maven goal

### Why You Must Restart

**PF4J plugin loading happens at server startup:**
- `TikaPluginManager.loadPlugins()` discovers and loads plugins from directories
- Creates `PluginClassLoader` instances for each plugin
- Instantiates plugin classes and calls `start()` methods
- This happens **once** when the server starts

**No hot reload support (yet):**
- PF4J doesn't automatically detect changed class files
- ClassLoaders are not refreshed during runtime
- Server must be restarted to reload plugin code

**Fast iteration cycle:**
- Compile plugin: ~5-10 seconds
- Restart server: ~10-15 seconds  
- Total: ~20-25 seconds per change
- Much faster than packaging ZIPs (2-3 minutes) or full rebuild (5+ minutes)

### Switching Back to Production Mode

For production deployments, use packaged ZIP files:

1. **Remove or set development mode to false:**
   ```bash
   unset TIKA_PLUGIN_DEV_MODE
   # OR
   export TIKA_PLUGIN_DEV_MODE=false
   ```

2. **Build plugin ZIPs:**
   ```bash
   ./mvnw clean package -pl tika-pipes/tika-pipes-plugins
   ```

3. **Update plugin-roots** to point to the directory containing ZIP files:
   ```json
   {
     "plugin-roots": [
       "/opt/tika/plugins"
     ]
   }
   ```

4. **Place plugin ZIPs** in the configured directory:
   ```bash
   cp tika-pipes-plugins/*/target/*.zip /opt/tika/plugins/
   ```

### Troubleshooting

**Plugin not loading?**
- Ensure `./mvnw compile` was run on the plugin module
- Check that `plugin.properties` exists in `target/classes/`
- Verify development mode is enabled
- Look for "DEVELOPMENT mode" in the logs on startup

**Changes not picked up?**
- Recompile the plugin module: `./mvnw compile -pl :plugin-name`
- Restart the application
- Check that you're editing the correct plugin module

**ClassNotFoundException errors?**
- Make sure you built all plugins first with `./mvnw clean install -DskipTests`
- The dev profile includes all plugin dependencies, but they must be compiled first

### References

- [pf4j Development Mode Documentation](https://pf4j.org/doc/development-mode.html)
- [JIRA TIKA-4587](https://issues.apache.org/jira/browse/TIKA-4587) - Development mode implementation

## Docker and Kubernetes Deployment

### Docker Image

The official Tika gRPC Docker images are published to Docker Hub at `apache/tika-grpc`.

**Pull the latest image:**
```bash
docker pull apache/tika-grpc:latest
```

**Run with default configuration:**
```bash
docker run -p 50052:50052 apache/tika-grpc:latest
```

**Run with custom configuration:**
```bash
docker run -p 50052:50052 \
  -v $(pwd)/my-config.json:/config/tika-config.json \
  apache/tika-grpc:latest --config /config/tika-config.json
```

### Kubernetes Deployment

#### Single Instance Deployment

For simple deployments without distributed configuration:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: tika-grpc
spec:
  replicas: 3
  selector:
    matchLabels:
      app: tika-grpc
  template:
    metadata:
      labels:
        app: tika-grpc
    spec:
      containers:
      - name: tika-grpc
        image: apache/tika-grpc:latest
        ports:
        - containerPort: 50052
          name: grpc
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
        volumeMounts:
        - name: config
          mountPath: /config
      volumes:
      - name: config
        configMap:
          name: tika-grpc-config
---
apiVersion: v1
kind: Service
metadata:
  name: tika-grpc
spec:
  selector:
    app: tika-grpc
  ports:
  - port: 50052
    targetPort: 50052
    name: grpc
  type: ClusterIP
```

#### Deployment with Apache Ignite ConfigStore

> **Not available in the published `apache/tika-grpc` image.** tika-grpc no longer ships the
> Ignite jars — they pulled in ~196 transitive artifacts for a store that, in practice, only ever
> talked to an embedded node in the same JVM (`IgniteConfigStore.init()` hardcodes
> `127.0.0.1:10800`). Setting `configStoreType: ignite` without those jars fails at startup with
> an explanatory error. To use it, build an image that adds `tika-pipes-config-store-ignite` and
> its Ignite dependencies to the classpath (the `dev` Maven profile wires them up for local runs).
> `memory` and `file` are the config stores available out of the box.

For distributed deployments with shared configuration using Apache Ignite:

**1. Create ConfigMap with Tika configuration:**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: tika-grpc-config
data:
  tika-config.json: |
    {
      "pipes": {
        "configStoreType": "ignite",
        "configStoreParams": "{\"cacheName\":\"tika-config-cache\",\"cacheMode\":\"REPLICATED\",\"igniteInstanceName\":\"TikaCluster\"}"
      },
      "fetchers": [
        {
          "s3": {
            "myS3Fetcher": {
              "region": "us-east-1",
              "bucket": "my-bucket"
            }
          }
        }
      ],
      "emitters": [
        {
          "s3": {
            "myS3Emitter": {
              "region": "us-east-1",
              "bucket": "my-output-bucket"
            }
          }
        }
      ]
    }
```

**2. Create StatefulSet for Ignite cluster discovery:**

```yaml
apiVersion: v1
kind: Service
metadata:
  name: tika-grpc-ignite
  labels:
    app: tika-grpc
spec:
  clusterIP: None  # Headless service for Ignite discovery
  selector:
    app: tika-grpc
  ports:
  - port: 47100
    name: ignite-comm
  - port: 47500
    name: ignite-disco
  - port: 50052
    name: grpc
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: tika-grpc
spec:
  serviceName: tika-grpc-ignite
  replicas: 3
  selector:
    matchLabels:
      app: tika-grpc
  template:
    metadata:
      labels:
        app: tika-grpc
    spec:
      containers:
      - name: tika-grpc
        image: apache/tika-grpc:latest
        ports:
        - containerPort: 50052
          name: grpc
        - containerPort: 47100
          name: ignite-comm
        - containerPort: 47500
          name: ignite-disco
        env:
        - name: JAVA_OPTS
          value: >-
            -Xmx2g
            -Xms2g
            --add-opens=java.base/java.nio=ALL-UNNAMED
            --add-opens=java.base/sun.nio.ch=ALL-UNNAMED
            --add-opens=java.base/java.lang=ALL-UNNAMED
            --add-opens=java.base/java.util=ALL-UNNAMED
            --add-opens=java.management/com.sun.jmx.mbeanserver=ALL-UNNAMED
        - name: IGNITE_KUBERNETES_NAMESPACE
          valueFrom:
            fieldRef:
              fieldPath: metadata.namespace
        - name: IGNITE_KUBERNETES_SERVICE_NAME
          value: "tika-grpc-ignite"
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
        volumeMounts:
        - name: config
          mountPath: /config
        readinessProbe:
          exec:
            command:
            - grpc_health_probe
            - -addr=:50052
          initialDelaySeconds: 10
          periodSeconds: 5
        livenessProbe:
          exec:
            command:
            - grpc_health_probe
            - -addr=:50052
          initialDelaySeconds: 30
          periodSeconds: 10
      volumes:
      - name: config
        configMap:
          name: tika-grpc-config
---
apiVersion: v1
kind: Service
metadata:
  name: tika-grpc
spec:
  selector:
    app: tika-grpc
  ports:
  - port: 50052
    targetPort: 50052
    name: grpc
  type: LoadBalancer  # Or ClusterIP for internal-only access
```

**3. RBAC permissions for Kubernetes API access (Ignite discovery):**

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: tika-grpc
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: tika-grpc
rules:
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["get", "list"]
- apiGroups: [""]
  resources: ["endpoints"]
  verbs: ["get", "list"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: tika-grpc
subjects:
- kind: ServiceAccount
  name: tika-grpc
roleRef:
  kind: Role
  name: tika-grpc
  apiGroup: rbac.authorization.k8s.io
```

Then update the StatefulSet to use the service account:

```yaml
spec:
  template:
    spec:
      serviceAccountName: tika-grpc
      containers:
      # ... rest of container spec
```

### Ignite Cluster Configuration

When using Apache Ignite ConfigStore in Kubernetes, the Ignite nodes automatically discover each other using Kubernetes API. Here's how it works:

1. **Headless Service** (`clusterIP: None`) allows each pod to have its own DNS entry
2. **StatefulSet** ensures predictable pod names: `tika-grpc-0`, `tika-grpc-1`, `tika-grpc-2`
3. **Environment Variables** tell Ignite how to discover other nodes:
   - `IGNITE_KUBERNETES_NAMESPACE`: Current namespace
   - `IGNITE_KUBERNETES_SERVICE_NAME`: Service name for discovery
4. **RBAC** grants permission to query Kubernetes API for pod discovery

**Key Ports:**
- **50052**: gRPC API for Tika operations
- **47100**: Ignite communication port
- **47500**: Ignite discovery port

**Benefits of Ignite ConfigStore in Kubernetes:**
- **Shared Configuration**: All pods share fetcher/emitter configurations
- **Configuration Updates**: Update config on one pod, all pods see the change
- **High Availability**: Config survives pod restarts
- **Automatic Discovery**: Pods automatically find each other

### Horizontal Pod Autoscaling

Scale based on CPU/memory or custom metrics:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: tika-grpc
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: StatefulSet
    name: tika-grpc
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

### Monitoring and Observability

**Prometheus metrics endpoint:**

Add to the StatefulSet:
```yaml
annotations:
  prometheus.io/scrape: "true"
  prometheus.io/port: "8081"
  prometheus.io/path: "/metrics"
```

**Logging:**

Configure structured JSON logging for better observability:
```yaml
env:
- name: LOG_LEVEL
  value: "INFO"
- name: LOG_FORMAT
  value: "json"
```

### Best Practices

1. **Resource Limits**: Always set memory/CPU requests and limits
2. **Readiness/Liveness Probes**: Use gRPC health checks
3. **Pod Disruption Budget**: Ensure high availability during updates
4. **Network Policies**: Restrict access to gRPC and Ignite ports
5. **Persistent Storage**: For file-based fetchers/emitters, use PersistentVolumeClaims
6. **Configuration Management**: Use ConfigMaps/Secrets for sensitive data
7. **Ignite Memory**: Allocate sufficient heap for Ignite cache (typically 2-4GB)

### Troubleshooting

**Pods can't discover each other:**
- Check RBAC permissions
- Verify headless service exists
- Check environment variables are set
- Review Ignite logs: `kubectl logs tika-grpc-0 | grep Ignite`

**Out of Memory errors:**
- Increase JVM heap size via `JAVA_OPTS`
- Increase pod memory limits
- Adjust Ignite data region sizes

**Slow gRPC responses:**
- Scale horizontally with HPA
- Check resource utilization
- Review parser performance
- Consider adding caching layer

### References

- [Apache Ignite Kubernetes Documentation](https://ignite.apache.org/docs/latest/installation/kubernetes/amazon-eks-deployment)
- [Kubernetes StatefulSets](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/)
- [gRPC Health Checking Protocol](https://github.com/grpc/grpc/blob/master/doc/health-checking.md)
