# Tika Pipes GRPC Server

The following is the Tika Pipes GRPC Server.

This server will manage a pool of Tika Pipes clients.

* Tika Pipes Fetcher CRUD operations
    * Create
    * Read
    * Update
    * Delete
* Fetch + Parse a given Fetch Item

## Security

tika-grpc binds to all interfaces with no application-level authentication, so restrict it to a
trusted network and/or enable TLS (`--secure`, with `--cert-chain`/`--private-key`/
`--trust-cert-collection`/`--client-auth-required`). A startup warning is logged when running
without TLS.

Dangerous capabilities are **denied by default** and must be opted into in the `<grpc>` section of
the tika-config:

```xml
<properties>
  <grpc>
    <!-- allow SaveFetcher/DeleteFetcher (runtime Create/Update/Delete) -->
    <allowComponentModifications>true</allowComponentModifications>
    <!-- allow per-request config (additional_fetch_config_json) -->
    <allowPerRequestConfig>true</allowPerRequestConfig>
  </grpc>
</properties>
```

When `allowComponentModifications` is false, fetchers must be declared in the `<fetchers>` section
of the tika-config; these are loaded at startup. Read operations (GetFetcher/ListFetchers,
Fetch+Parse) are always available.

