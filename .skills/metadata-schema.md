# Metadata Key Registry & Schema Skill

Working with `tika-metadata-schema` — the committed, build-gated registry of Tika's metadata keys.
File structure and the CLOSED/OPEN/TEMPLATE/UNKNOWN classification are in that module's `README.md`;
this covers conventions, regeneration, and the traps.

## The three registries

Under `tika-metadata-schema/src/main/resources/org/apache/tika/metadata/`:

- `metadata-keys.json` — closed set: every `Property` constant + the synthesized `tk:digest:*` cross-product.
- `metadata-open-namespaces.json` — `KeyPrefix` prefixes for runtime-minted names (`html:`, `message:raw-header:`, `mdb-prop:`).
- `metadata-key-fields.json` — TIKA-4797 `{class, field, key}` table for field-identity migration.

Committed on purpose: they are the reviewable audit trail of the key space (a rename or a dropped key
shows up as a diff). Don't switch to build-time-only generation — that loses the review signal.

## Regenerate (after adding/changing a Property or KeyPrefix)

```bash
tika-metadata-schema/regen.sh
```

This does the full sequence in one shot: `-am install` so newly added Property/KeyPrefix
classes are on the scan classpath, regenerate all three registries via the forked-exec profile, print
a before/after key-count check (catches an incomplete classpath scan), `git diff --stat` the
registries, then run the gate tests. Flags: `--skip-install` (only safe if nothing outside
`tika-metadata-schema` itself changed since the last install) and `--skip-tests` for a faster inner
loop. Then review the diff and commit the Property change and the regenerated JSON together.

The manual sequence the script replaces, for reference or if you need to run a step in isolation:

```bash
# if parser Property classes changed, install them first so the scan sees them:
./mvnw -Pfast -DskipTests -pl tika-metadata-schema -am install -Dmaven.repo.local=$(pwd)/.local_m2_repo
# regenerate all three:
./mvnw -pl tika-metadata-schema -Pregen-metadata-schema process-classes -Dmaven.repo.local=$(pwd)/.local_m2_repo
```

**Trap — never use `exec:java`.** `SchemaGenerator` scans `java.class.path`, force-loads Property
classes, and swallows load failures. `exec:java` runs in-process on Maven's classpath → finds zero
keys → emits a near-empty registry that exits 0 and *passes* `MetadataNoUnderscoreTest`. The
`-Pregen-metadata-schema` profile uses the forking `exec` goal (`<classpath/>`) for a real child-JVM
classpath. A hand-rolled `java -cp` also works, but the full repo classpath (~4000 jars) overflows
the 128 KB arg limit — use the profile.

**Trap — incomplete classpath drops keys silently.** Always validate: `git diff` should show only the
intended change (a big key-count drop = classes failed to load), and `MetadataSchemaTest` regenerates
under the full test classpath and asserts a byte-match — the definitive completeness check.

## Gate tests (run WITHOUT `-Pfast`, which skips execution)

```bash
./mvnw -pl tika-metadata-schema test -Dmaven.repo.local=$(pwd)/.local_m2_repo
```

- `MetadataSchemaTest` / `MetadataFieldTableTest` — regenerate in-memory, assert committed files match.
- `MetadataNoUnderscoreTest` — no Tika-coined key/prefix may contain `_`. Scans the JSON, so it
  reflects the *registry*, not live code — regenerate before trusting it.
- `MetadataKeyValidatorTest` — registry-driven CLOSED/OPEN/TEMPLATE/UNKNOWN classifier.
- `MetadataCoverageTest` — fails if a scanned module's keys are neither in scope nor listed out-of-scope.

Failures with stale `X-TIKA:`/underscore/`SHA256` keys usually mean *regenerate*, not edit code.

## Naming conventions (frozen for 4.0, TIKA-4794)

- All keys are `Property` constants — no bare `String` keys (`metadata-string-keys.json` retired).
  Exception: a few deprecated `String` fields (`IPTC.*_WRONG_CASE`,
  `TikaCoreProperties.EMBEDDED_RESOURCE_TYPE_KEY`) exist only to construct a real `Property`'s
  name and aren't independent keys.
- Tika-coined prefix is `tk:` (`X-TIKA:` is legacy); kebab-case, no underscores.
- External-standard names verbatim, *including* the standard's prefix: `dc:`, `xmp:`, `cp:`, `extended-properties:`.
- HTTP has no namespace → `Content-Type`, `Content-Encoding`, `Location` stay bare (no `http:`).
- Tika-coined message keys *do* get a namespace: `message:`, `multipart:`.
- HttpHeaders keys are SIMPLE — Content-Type is not a bag; parsers use `set()`, not `add()`.
- Digest keys use the JCA name via `DigestDef.getJavaName()`: `tk:digest:SHA-256`, `SHA3-256` (not
  `SHA256`/`SHA3_256`). Config *input* still takes the enum name (`"SHA256"`); only the output key changes.

## After a rename: sweep for stale key literals

The compiler won't catch `metadata.get("Message-From")`. Grep the whole repo for old strings and
prefer replacing them with the constant, so the next rename fails to compile instead of at test time:

```bash
grep -rn '"Message-' --include=*.java . | grep -v /target/
grep -rn 'tk:digest:SHA[0-9]' --include=*.java . | grep -v /target/
```

## XML comments can't contain `--`

A double hyphen in an XML comment makes the POM non-parseable and breaks the module build. Reword.
