# tika-eval for encoding-detector regression hunts

A condensed pattern for finding SBCS→CJK style charset-detector regressions
(or any "A picks encoding X, B picks encoding Y" question) without
building two tika-app distributions.

## Two configs, one build

Encoding-detector experiments don't need a "before" and "after" tika-app —
the chain composition is per-config. Run the SAME tika-app twice against
two configs, treat the outputs as `-a` and `-b`. Much faster than
`tika-eval-compare`'s two-build flow.

```bash
# build once
./mvnw clean install -pl tika-app -am -Pfast -DskipTests \
  -Dmaven.repo.local=$(pwd)/.local_m2_repo
unzip -q tika-app/target/tika-app-*.zip -d /tmp/tika-app-current

# two configs (any combination of detectors)
java -jar /tmp/tika-app-current/tika-app-*.jar \
  --config=tika-config-3x-default.json \
  -i <corpus> -o <workdir>/extracts/A -n 6
java -jar /tmp/tika-app-current/tika-app-*.jar \
  --config=tika-config-junkfilter-combiner.json \
  -i <corpus> -o <workdir>/extracts/B -n 6

# normal Compare
java -jar /tmp/tika-eval-current/tika-eval-app-*.jar Compare \
  -a <workdir>/extracts/A -b <workdir>/extracts/B -d <workdir>/extracts/A-vs-B -r -rd <workdir>/extracts/A-vs-B-reports
```

### Canonical 3.x-default encoding chain config

```json
{
  "encoding-detectors": [
    {"html-encoding-detector": {}},
    {"universal-encoding-detector": {}},
    {"icu4j-encoding-detector": {}}
  ]
}
```

### Canonical 4.x junkfilter chain config

```json
{
  "encoding-detectors": [
    {"bom-detector": {}},
    {"html-encoding-detector": {}},
    {"mojibuster-encoding-detector": {}},
    {"junk-filter-encoding-detector": {}}
  ]
}
```

### Per-detector isolation configs

Each detector wired alone lives in `<workdir>/configs/`:
`tika-config-bom.json`, `tika-config-html.json`, `tika-config-htmlstandard.json`,
`tika-config-universal.json`, `tika-config-icu4j.json`,
`tika-config-mojibuster.json`, `tika-config-junkfilter-chain.json`.
Use these for chain-attribution work (which detector did the detection).

## Encoding-pair flip query

`MIMES.MIME_STRING` for text-y mimes is `text/html; charset=X` form. Extract
the charset with a regex split, group by `(enc_a, enc_b)`, filter pairs.
A=before/`-a`, B=after/`-b`; join on `pa.ID = pb.ID` (paired by id).

```sql
SELECT
  REGEXP_REPLACE(ma.MIME_STRING, '^.*charset=', '') AS enc_a,
  REGEXP_REPLACE(mb.MIME_STRING, '^.*charset=', '') AS enc_b,
  COUNT(*) n,
  SUM(cb.NUM_COMMON_TOKENS - ca.NUM_COMMON_TOKENS) AS delta_common
FROM PROFILES_A pa
JOIN PROFILES_B pb ON pa.ID = pb.ID
JOIN MIMES ma ON pa.MIME_ID = ma.MIME_ID
JOIN MIMES mb ON pb.MIME_ID = mb.MIME_ID
JOIN CONTENTS_A ca ON ca.ID = pa.ID
JOIN CONTENTS_B cb ON cb.ID = pb.ID
WHERE ma.MIME_STRING LIKE '%charset=%' AND mb.MIME_STRING LIKE '%charset=%'
  AND REGEXP_REPLACE(ma.MIME_STRING, '^.*charset=', '') <>
      REGEXP_REPLACE(mb.MIME_STRING, '^.*charset=', '')
GROUP BY enc_a, enc_b
ORDER BY n DESC, delta_common ASC LIMIT 50;
```

Add an `IN (...)` filter on either side to constrain to a family
(e.g. SBCS-Western → CJK):

```sql
  AND REGEXP_REPLACE(ma.MIME_STRING,'^.*charset=','')
      IN ('windows-1252','ISO-8859-1','ISO-8859-15','ISO-8859-2','ISO-8859-3',
          'windows-1250','windows-1254','windows-1257','ISO-8859-13',
          'windows-1258','x-MacRoman','IBM850','IBM852')
  AND REGEXP_REPLACE(mb.MIME_STRING,'^.*charset=','')
      IN ('GB18030','GBK','GB2312','Big5','Big5-HKSCS','Shift_JIS','EUC-JP',
          'EUC-KR','x-EUC-TW','x-windows-874','x-windows-949',
          'ISO-2022-JP','ISO-2022-KR','ISO-2022-CN')
```

### Per-file drilldown

Join `CONTAINERS` to get the source path; pull `LANG_ID_1` from both sides
to see whether language detection agrees the content is Western while the
charset has flipped to CJK (the regression's defining shape):

```sql
SELECT ct.FILE_PATH,
       REGEXP_REPLACE(ma.MIME_STRING,'^.*charset=','') AS enc_a,
       REGEXP_REPLACE(mb.MIME_STRING,'^.*charset=','') AS enc_b,
       ca.NUM_COMMON_TOKENS AS ca_tok, cb.NUM_COMMON_TOKENS AS cb_tok,
       cb.NUM_COMMON_TOKENS - ca.NUM_COMMON_TOKENS AS delta,
       ca.LANG_ID_1 AS lang_a, cb.LANG_ID_1 AS lang_b
FROM PROFILES_A pa JOIN PROFILES_B pb ON pa.ID = pb.ID
JOIN MIMES ma ON pa.MIME_ID = ma.MIME_ID JOIN MIMES mb ON pb.MIME_ID = mb.MIME_ID
JOIN CONTENTS_A ca ON ca.ID = pa.ID JOIN CONTENTS_B cb ON cb.ID = pb.ID
JOIN CONTAINERS ct ON ct.CONTAINER_ID = pa.CONTAINER_ID
WHERE <enc_a/enc_b filter as above>
ORDER BY delta ASC LIMIT 15;
```

## Reading the signals — OOV, languageness, and FFFD together

No single signal is authoritative. Use `oov` as a **secondary** signal alongside
`languageness` (the junk-model coherence z-score) and the U+FFFD rate — each is
right where the others are blind, so cross-check rather than ranking on any one.
(Established 2026-06-03: a 40-file OOV-"worse" set was mostly metric artifacts
once languageness/FFFD were brought in — only ~6 were real. But OOV is also the
*correct* signal where languageness is blind, so neither dominates.)

- **OOV can mislead** when langid shifts — a CJK/UTF-8 recovery in B is scored
  against a different vocab → higher OOV though B is right — or when a wrong
  decode fragments words into more short common tokens (→ higher count for the
  WORSE decode). A common-token delta is a signal, not proof.
- **languageness can mislead** on SBCS↔SBCS cross-script mojibake — Greek decoded
  as KOI8-R is "coherent" Cyrillic, so `languageness` stays flat while `oov`
  correctly flags it. Conversely languageness catches OOV's CJK/script-recovery
  blind spot. Each covers the other's blind spot.
- **FFFD rate** flags decode failures (illegal bytes): `num_replacement /
  num_non_ascii` (un-diluted; `/ content_length` dilutes to ~0 on ASCII-heavy
  docs). Tika strips C0 controls at extraction, so legal-but-wrong (C1) mojibake
  does not surface here — that signal belongs in the detector chain, not the eval.
- **In practice:** when the signals agree, high confidence; when they disagree
  (OOV-worse but languageness-better, or vice versa), that file needs a look —
  the disagreement points you at WHICH files to inspect, it does not by itself
  declare OOV or languageness "wrong." Split OOV-worse by languageness direction
  (query in `tika-eval-regression.adoc`).

### Isolate a change against the PRIOR run, not just 3.x

To see what one chain change actually did, Compare the new run against the
*previous* 4.x run (B-new vs B-prior), not only vs 3.x. The diff should be
*surgical* — e.g. the within-Latin letter gate moved exactly 6 files
(IBM850 / x-MacRoman → windows-1252) vs the prior run and nothing else. A
bigger-than-expected diff means the change fired more broadly than intended.

## Per-file detector attribution (`tk:encoding-detection-trace`)

Every JSON extract from a chain with multiple detectors carries
`tk:encoding-detection-trace` in metadata. It's a per-detector emission
log with the META detector's arbitration tag at the end:

```
MojibusterEncodingDetector->Shift_JIS[STATISTICAL](1.00) [junk-filter-selected]
```

When investigating "why did B pick X for this file?", read this trace first
— it tells you which base detector(s) emitted candidates and which one the
meta detector chose. If the trace shows ONLY Mojibuster firing with a CJK
pick, the bug is in Mojibuster's emission (pool too narrow), not in
JunkFilter's arbitration.

`tk:encoding-detector` is the simple-name credit string;
`tk:detected-encoding` is the final answer (also in `Content-Encoding`).

## Reproducing a single-file detection without a full chain

```bash
./mvnw -q -pl tika-ml/tika-ml-junkdetect -Dmaven.repo.local=$(pwd)/.local_m2_repo \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=org.apache.tika.ml.junkdetect.TraceJunkFilter \
  -Dexec.args="--file <path> --auto-candidates --content-cleaner --head-bytes 524288 --sample 120" \
  exec:java
```

Key flags:
- `--auto-candidates` — use Mojibuster's per-file pool as the candidate set
- `--content-cleaner` — decode each candidate then run text through
  `HtmlContentCleaner` to match the live chain
- `--head-bytes 524288` — read up to 512 KB raw to match
  `AdaptiveProbe.DEFAULT_RAW_CAP`. The default `READ_LIMIT` of 16 KB will
  give a *different* probe than the live chain on long markup-heavy pages
  and lead you to disagree with the live chain's pick. Always pass this
  when reconciling a TraceJunkFilter run with a live extract.

Without `--head-bytes`, you are looking at a different probe than the
chain saw — this is the most common source of "trace says X, chain
says Y" confusion.
