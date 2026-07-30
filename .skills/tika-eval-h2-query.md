# Query the tika-eval H2 database directly

`tika-eval` (Compare / Profile / Report) stores everything in an **H2** database
— the `-d <name>` you pass to `Compare` produces `<name>.mv.db`. The xlsx/`summary.md`
reports only surface pre-canned views; for anything else (exact counts, custom
joins, "better vs worse" tallies the reports don't compute) connect to the H2 db
and run SQL.

## Connecting (the part that trips people up)

tika-eval creates the db with **no username and no password** (`H2Util` calls
`DriverManager.getConnection(url)` with no creds). So:

- **URL:** `jdbc:h2:<absolute-path-without-the-.mv.db-suffix>`
- **user / password: empty.** `-user sa` (H2's old default) **fails** with
  "Wrong user name or password" — the db wasn't created with `sa`.
- Add `;IFEXISTS=TRUE` so a typo opens nothing instead of silently creating a new
  empty db, and `;ACCESS_MODE_DATA=r` for a safe read-only open.
- **H2 is single-writer**: don't query while a `Compare`/`Profile`/`Report` run
  has the db open (file lock). Query after the run finishes.

Use the `h2-*.jar` that ships with tika-eval-app (in its `target/dependency/` or
the unzipped runtime `lib/`).

### One-shot query (H2 Shell)

```bash
H2=path/to/h2-*.jar
DB='jdbc:h2:path/to/eval;IFEXISTS=TRUE;ACCESS_MODE_DATA=r'   # note: no .mv.db
java -cp "$H2" org.h2.tools.Shell -url "$DB" -user '' -password '' \
  -sql "SHOW TABLES"
```

Pass the SQL as a **single line** to `-sql` (a multi-line arg confuses the Shell;
it then waits on stdin and appears to hang).

## Key tables

| table | what's in it |
|---|---|
| `PROFILES_A` / `PROFILES_B` | one row per extracted file: `FILE_NAME`, `MD5`, `MIME_ID`, `CONTAINER_ID`, `EMBEDDED_FILE_PATH`, `LENGTH`, `NUM_PAGES`, … (A = "before"/-a, B = "after"/-b) |
| `CONTENTS_A` / `CONTENTS_B` | text profile per file (join on `ID`): `OOV`, `LANGUAGENESS`, `NUM_TOKENS`, `NUM_COMMON_TOKENS`, `LANG_ID_1`/`LANG_ID_PROB_1`, `TOKEN_ENTROPY_RATE`, … |
| `ENCODINGS_A` / `ENCODINGS_B` | detected-encoding per file (join on `ID`): `DETECTED_ENCODING`, `ENCODING_DETECTOR`, `DECLARED_METADATA`. **`DETECTED_ENCODING` lives HERE, not on `PROFILES` — moved out in the encodings-table refactor; querying `PROFILES_*.DETECTED_ENCODING` now errors "Column not found".** A file with no detected encoding has no row. |
| `CONTENT_COMPARISONS` | per-file A↔B comparison (`ID`): `DICE_COEFFICIENT`, `OVERLAP`, top token diffs |
| `MIMES` | `MIME_ID` → `MIME_STRING` |
| `CONTAINERS` | container id → input file path |
| `*_COMPARED` (`EXCEPTIONS_COMPARED`, `PARSE_TIME_COMPARED`) | pre-joined A/B summaries |

## Example queries

Detected charset distribution in the "after" run (fast):

```sql
SELECT m.MIME_STRING, COUNT(*) n
FROM PROFILES_B p JOIN MIMES m ON p.MIME_ID = m.MIME_ID
GROUP BY m.MIME_STRING ORDER BY n DESC LIMIT 20;
```

Worst content divergences (already A/B-paired, fast):

```sql
SELECT ID, DICE_COEFFICIENT FROM CONTENT_COMPARISONS
WHERE DICE_COEFFICIENT < 0.5 ORDER BY DICE_COEFFICIENT;
```

OOV / languageness better-vs-worse tally. **A and B are paired by `id`** — the
same row `id` is the same file in both runs (this is exactly how tika-eval's own
reports join: `join profiles_b pb on pa.id = pb.id`). Join on `id`, never on
container/path — that's a fast PK join:

```sql
SELECT SUM(CASE WHEN cb.OOV < ca.OOV THEN 1 ELSE 0 END) AS oov_better,
       SUM(CASE WHEN cb.OOV > ca.OOV THEN 1 ELSE 0 END) AS oov_worse
FROM CONTENTS_A ca JOIN CONTENTS_B cb ON ca.ID = cb.ID;
```

Net common-tokens A vs B — the headline "more real text recovered" metric (a big
positive delta is the usual sign of better charset/extraction in B):

```sql
SELECT SUM(ca.NUM_COMMON_TOKENS) AS common_a,
       SUM(cb.NUM_COMMON_TOKENS) AS common_b,
       SUM(cb.NUM_COMMON_TOKENS) - SUM(ca.NUM_COMMON_TOKENS) AS delta
FROM CONTENTS_A ca JOIN CONTENTS_B cb ON ca.ID = cb.ID;
```

To bring in mime/path, join `PROFILES_A pa ON pa.ID = ca.ID` (and `pb`/`cc`
likewise on the same `id`) — all on `id`.

Detected-encoding queries — `DETECTED_ENCODING` is on `ENCODINGS_A`/`ENCODINGS_B`
(join on `ID`), NOT `PROFILES`. CJK count in B (LOWER() — `REGEXP` is case-sensitive,
see below):

```sql
SELECT COUNT(*) FROM ENCODINGS_B
WHERE LOWER(DETECTED_ENCODING) REGEXP 'gb|big5|euc|shift|jis|2022|949';
```

Encoding flips A→B by direction (what changed between runs):

```sql
SELECT ea.DETECTED_ENCODING a_enc, eb.DETECTED_ENCODING b_enc, COUNT(*) n
FROM ENCODINGS_A ea JOIN ENCODINGS_B eb ON ea.ID = eb.ID
WHERE ea.DETECTED_ENCODING <> eb.DETECTED_ENCODING
GROUP BY a_enc, b_enc ORDER BY n DESC;
```

Map a flipped file back to its source file — `PROFILES_*.FILE_NAME` is the content
hash (the input file is `<corpus>/<first-2-hex>/<FILE_NAME>`); join `CONTENTS` for OOV:

```sql
SELECT pb.FILE_NAME, ea.DETECTED_ENCODING a_enc, eb.DETECTED_ENCODING b_enc,
       ca.OOV oov_a, cb.OOV oov_b
FROM ENCODINGS_A ea JOIN ENCODINGS_B eb ON ea.ID = eb.ID
  JOIN PROFILES_B pb ON ea.ID = pb.ID
  JOIN CONTENTS_A ca ON ea.ID = ca.ID JOIN CONTENTS_B cb ON ea.ID = cb.ID
WHERE LOWER(eb.DETECTED_ENCODING) REGEXP 'gb|big5|euc|shift|jis|2022|949'
  AND NOT (LOWER(ea.DETECTED_ENCODING) REGEXP 'gb|big5|euc|shift|jis|2022|949');
```

## Gotcha: `REGEXP` is case-sensitive (silent wrong results)

H2's `REGEXP` operator is **case-sensitive**, so `DETECTED_ENCODING REGEXP
'big5|gb|euc'` does **not** match `Big5-HKSCS` or `GB18030` — and it fails
*silently*, quietly dropping/keeping the wrong rows instead of erroring. Always
either lowercase the column or use the inline case-insensitive flag:

```sql
-- right:
WHERE LOWER(DETECTED_ENCODING) REGEXP 'big5|gb|euc|shift|jis|2022|949'
-- or:
WHERE DETECTED_ENCODING REGEXP '(?i)big5|gb|euc|shift|jis|2022|949'
-- wrong (misses Big5-HKSCS, GB18030, Shift_JIS, ...):
WHERE DETECTED_ENCODING REGEXP 'big5|gb|euc|shift|jis|2022|949'
```

(`DETECTED_ENCODING` is on `ENCODINGS_A`/`ENCODINGS_B` — join to `PROFILES`/`CONTENTS`
on `ID` — populated from `tk:detected-encoding`.)

## Tip

For a quick interactive session, drop `-sql` and you get an H2 prompt; `SHOW
COLUMNS FROM <table>;` lists a table's columns.
