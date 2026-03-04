# tika-ml-chardetect — Charset Detection

A lightweight, production-ready charset/encoding detector for Apache Tika.
It is designed as a drop-in replacement for the existing ICU4J-based detector
(`Icu4jEncodingDetector`) and integrates with the standard Tika
`EncodingDetector` interface.

---

## Drop-in Replacement for ICU4J and juniversalchardet

`MojibusterEncodingDetector` implements the same `org.apache.tika.detect.EncodingDetector`
interface as `Icu4jEncodingDetector` and `UniversalEncodingDetector`.  There are
three ways to use it.

### Option A — SPI auto-discovery (simplest)

Add `tika-ml-chardetect` to your classpath alongside the existing Tika jars.
`DefaultEncodingDetector` (which backs `AutoDetectReader` and the text parsers)
loads all registered `EncodingDetector` implementations via `ServiceLoader`.
`MojibusterEncodingDetector` carries a `@TikaComponent` annotation that the Tika
annotation processor uses at compile time to generate the
`META-INF/services/org.apache.tika.detect.EncodingDetector` SPI entry, so
it is discovered automatically.

**Ordering caveat**: when multiple jars each provide an
`EncodingDetector` service entry, `DefaultEncodingDetector` uses the first
non-`null` result across all of them.  The order in which jars are consulted
depends on classloader ordering (typically classpath order).  If you need a
guaranteed result, use Option B or C.

```xml
<!-- Maven dependency -->
<dependency>
  <groupId>org.apache.tika</groupId>
  <artifactId>tika-ml-chardetect</artifactId>
  <version>${tika.version}</version>
</dependency>
```

### Option B — Explicit Java API

Construct `MojibusterEncodingDetector` directly, bypassing the service registry
entirely.  Use this when you control the calling code and want a single,
deterministic detector.

```java
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.detect.WideUnicodeDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.ml.chardetect.MojibusterEncodingDetector;
import org.apache.tika.parser.ParseContext;

// Full production pipeline: WideUnicodeDetector first, then ML.
WideUnicodeDetector wide = new WideUnicodeDetector();
MojibusterEncodingDetector ml   = new MojibusterEncodingDetector(); // loads bundled model

try (TikaInputStream tis = TikaInputStream.get(bytes)) {
    Metadata meta = new Metadata();
    ParseContext ctx = new ParseContext();
    List<EncodingResult> result = wide.detect(tis, meta, ctx);
    if (result.isEmpty()) {
        result = ml.detect(tis, meta, ctx);
    }
}
```

The `WideUnicodeDetector` pre-step is optional but recommended: it handles
UTF-16/32 via null-byte structural analysis before the ML model runs, matching
what `EvalCharsetDetectors` calls the "Pipeline" configuration.

### Option C — Tika JSON configuration (exclusive replacement)

```json
{
  "encodingDetectors": [
    { "type": "mojibuster-encoding-detector" }
  ]
}
```

Load the config at startup:

```java
TikaConfig config = TikaConfig.load(Paths.get("tika-config.json"));
AutoDetectParser parser = new AutoDetectParser(config);
```

### Comparison with existing detectors

Numbers are from the held-out MADLAD + zh_yuewiki test set at **full probe
length** (5 000 samples/charset, 36 charsets including structurally-detected
ones like HZ and US-ASCII).  "Pipeline" = `WideUnicodeDetector` +
`MojibusterEncodingDetector`; see [Evaluation Results](#evaluation-results) for the
full per-length table.

| Feature | **Pipeline** | `Icu4jEncodingDetector` | `UniversalEncodingDetector` |
|---|---|---|---|
| Approach | Wide-unicode pre-filter + structural rules + ML | Byte-frequency statistics (ICU4J) | Mozilla-derived heuristics |
| Overall strict accuracy (full) | **78.0%** | 51.7% | 40.7% |
| Overall soft accuracy (full) | **91.4%** | 74.0% | 56.5% |
| Latency (full probe) | **8.5 µs/call** | 167 µs/call | 18 µs/call |
| UTF-16/UTF-32 | **98–100%** | 86–100% | <1% |
| IBM424 / IBM500 / IBM855 | **Yes** | Partial | No |
| ISO-8859-3, TIS-620, KOI8-U, windows-1258 | **Yes** | No | No |
| x-mac-cyrillic | **Yes** | No | Partial |
| Model size | **257 KB** (8 192 buckets) | icu4j.jar (~12 MB) | ~100 KB |
| External dependencies | None (model bundled) | `com.ibm.icu:icu4j` | `com.github.albfernandez:juniversalchardet` |

---

## Algorithm Overview

Detection is a three-tier pipeline: wide-character pre-filter → structural
rules → statistical model.

### Tier 0 — Wide Unicode pre-filter (`WideUnicodeDetector`)

Runs before anything else.  Identifies UTF-16 LE/BE and UTF-32 LE/BE purely
from structural null-byte patterns (BOM or column-based analysis).  Because
wide encodings have systematic 0x00 bytes in predictable positions, this
detector achieves 98–100% accuracy at probe lengths as short as 20 bytes.
If it fires, the ML model is never invoked.

### Tier 1 — Structural Rules (`StructuralEncodingRules`)

Fast, deterministic checks that run before the statistical model and return
a definitive answer when possible.

| Rule | Encodings detected | Rationale |
|---|---|---|
| `checkHz` | HZ-GB-2312 | `~{`/`~}` switching sequences are unique |
| `detectIso2022` | ISO-2022-JP, ISO-2022-KR, ISO-2022-CN | ESC designation sequences are unique to the ISO-2022 family |
| `checkAscii` | UTF-8 (= US-ASCII) | No bytes ≥ 0x80 → pure 7-bit ASCII, a strict UTF-8 subset |
| `checkIbm424` | IBM424-ltr / IBM424-rtl | Hebrew letters (0x41–0x6A) and EBCDIC space (0x40) are all below 0x80, invisible to the ML model |
| `checkIbm500` | IBM500 | EBCDIC space (0x40) dominance + high-byte Latin letter density (six clusters 0x81–0xE9) |
| `checkUtf8` (negative only) | — | Provably invalid UTF-8 sequences exclude UTF-8 from the model's candidate set |

**GB18030 4-byte upgrade** (post-model, `StructuralEncodingRules.hasGb18030FourByteSequence`):
GB18030-specific 4-byte sequences have digit bytes (0x30–0x39) in the second
and fourth positions, which is impossible in GBK/GB2312 (trail bytes are
0x40–0xFE).  A single matching 4-tuple is definitive proof that a GB18030
codec is required.  Applied after the model: if the model returns GBK or
GB2312 and such a sequence is found in the probe, the result is upgraded to
GB18030.

**ISO-8859 → Windows-12XX upgrade** (post-model, `upgradeIsoToWindows`):
C1 bytes (0x80–0x9F) are control characters in every ISO-8859-X standard but
printable characters in every Windows-12XX encoding.  Their presence is
definitive proof the content is not ISO-8859-X; the corresponding Windows
variant is substituted while preserving the model's confidence score.

See [Windows-12XX vs ISO-8859-X](#windows-12xx-vs-iso-8859-x) below for the
full rationale and the one exception (ISO-8859-3).

### Tier 2 — Statistical Model (`MojibusterEncodingDetector`)

A **multinomial logistic regression** classifier trained on byte n-gram
features.  Handles ambiguous cases that structural rules cannot resolve:
single-byte encodings sharing the same script (KOI8-R vs windows-1251 vs
IBM855), CJK multibyte families (GBK vs GB2312 vs GB18030), and everything
else.

**Features (`ByteNgramFeatureExtractor`)**

Only bytes ≥ 0x80 contribute features.  HTML tag markup (all ASCII) is
ignored automatically; no HTML stripping is needed.

- **Unigrams**: each high byte hashed individually — encodes byte-frequency
  distributions that separate SBCS encodings.
- **Bigrams**: consecutive pair `(b[i], b[i+1])` where `b[i] ≥ 0x80` —
  captures multibyte character structure (Big5 lead/trail, GBK, Shift-JIS,
  EUC-* pairs).

Features are hashed with FNV-1a into a fixed-width bucket array.  The
production model uses **8 192 buckets** (257 KB); evaluation across 4 096,
8 192, 16 384, 32 768, and 65 536 buckets showed negligible accuracy
differences (< 0.5 pp strict at full length), confirming this dataset is
not bucket-limited.

**Training**: multinomial logistic regression with SGD, learning rate 0.05,
5 epochs, no L2 regularisation.

**Confusable sets (`CharsetConfusables`)**: groups of encodings that are
difficult or impossible to distinguish at the byte level (e.g. GBK ⊂ GB18030,
Big5 ⊂ Big5-HKSCS) are defined in `CharsetConfusables`.  During inference,
probability mass is pooled across each group.  During evaluation, predicting
any group member counts as a "soft" hit.

**CJK grammar walkers** (`CjkEncodingRules`, `Rule.CJK_GRAMMAR`): after the
model nominates a CJK encoding, a grammar walker validates the byte sequences
against the encoding's formal grammar (Shift_JIS, EUC-JP, EUC-KR, Big5,
GB18030).  A grammar score of 0 means the probe violates the encoding's byte
grammar and the candidate is dropped; scores above 10 replace the model
confidence with a grammar-derived confidence.

See [CJK Grammar Walkers](#cjk-grammar-walkers) below for the per-encoding
byte rules and the Shift_JIS design considerations.

---

## Windows-12XX vs ISO-8859-X

Every ISO-8859-X encoding reserves bytes `0x80–0x9F` for C1 control characters.
These bytes never appear in real text — they are escape sequences and device
controls that have been obsolete since the 1980s.  The corresponding
Windows-12XX encodings use that same range for printable characters: curly
quotes, em-dash, Euro sign, ellipsis, and similar typographic symbols common
in modern documents.

This means that any byte in `0x80–0x9F` is **impossible** in ISO-8859-X but
entirely normal in Windows-12XX.  A single C1 byte is therefore definitive
proof that the file uses the Windows variant.

### Design choice: train on Windows variants, upgrade at inference

Rather than training the model on both ISO and Windows variants (which would
split probability mass between near-identical byte distributions), we:

1. **Train only on Windows-12XX variants** — the model learns the Windows
   character distributions directly.
2. **At inference, if C1 bytes are present**, apply `upgradeIsoToWindows` to
   replace any ISO-8859-X result with its Windows-12XX equivalent.
3. **At inference, if no C1 bytes are present**, the ISO and Windows variants
   are byte-for-byte identical in the non-C1 range — the choice of label is
   arbitrary, and `CharsetConfusables` treats them as a symmetric confusable
   group so neither counts as an error in evaluation.

The full ISO-to-Windows mapping (from `CharsetConfusables.ISO_TO_WINDOWS`):

| ISO-8859-X | Windows equivalent | Script |
|---|---|---|
| ISO-8859-1 | windows-1252 | Western European (Latin-1) |
| ISO-8859-15 | windows-1252 | Western European (Latin-9, adds € and four other chars) |
| ISO-8859-2 | windows-1250 | Central / Eastern European |
| ISO-8859-4 | windows-1257 | Baltic |
| ISO-8859-5 | windows-1251 | Cyrillic |
| ISO-8859-6 | windows-1256 | Arabic |
| ISO-8859-7 | windows-1253 | Greek |
| ISO-8859-8 | windows-1255 | Hebrew |
| ISO-8859-9 | windows-1254 | Turkish (Latin-5) |
| ISO-8859-13 | windows-1257 | Baltic (Estonian/Latvian/Lithuanian) |

Note that both ISO-8859-1 and ISO-8859-15 map to windows-1252.  ISO-8859-15
differs from ISO-8859-1 only in eight code points in the `0xA0–0xFF` range
(adding `€`, `Š`, `š`, `Ž`, `ž`, `Œ`, `œ`, `Ÿ`); its C1 range is equally
unused.  The `CharsetConfusables` symmetric group `{ISO-8859-1, ISO-8859-15,
windows-1252}` handles this: any of the three is acceptable as a lenient match.

### The ISO-8859-3 exception

**ISO-8859-3** (Latin-3, used primarily for Maltese) has no Windows equivalent.
The characters unique to Maltese — `ħ`, `ġ`, `ċ`, `ż` and their uppercase forms —
are not representable in any Windows-12XX code page.  ISO-8859-3 is therefore
retained as a distinct training class and is never subject to the Windows
upgrade rule.

This is the only ISO-8859 variant kept in the model.

---

## CJK Grammar Walkers

The ML model reliably separates CJK content from non-CJK content but can
confuse related CJK encodings — most critically **Shift_JIS vs EUC-JP**, where
both encode Japanese and language-level arbitration cannot help.  Grammar
walkers in `CjkEncodingRules` provide a hard structural check: if the model
nominates a CJK encoding, the walker either confirms it, weakly confirms it,
or rejects it based purely on byte-sequence validity.

Walkers are only invoked when the model has already placed a CJK encoding in
its output — never unconditionally — to avoid false positives on Latin, Arabic,
or Cyrillic content where some byte values happen to fall in CJK lead-byte
ranges.

### Confidence scoring (ICU4J-inspired)

Each walker returns a score on a 0–100 scale:

| Score | Meaning |
|---|---|
| 0 | Invalid byte sequences detected — reject this encoding |
| 10 | Valid grammar but ≤ 10 double-byte characters — too little evidence; retain model confidence |
| 11–100 | `30 + doubleByte − 20 × bad`, capped at 100 — replace model confidence with grammar confidence |

Early exit: bail when `bad ≥ 2` and `bad × 5 ≥ doubleByte` (bad sequences
outnumber good at more than 1:5 — same condition as ICU4J's `CharsetRecog_mbcs`).

### Per-encoding byte rules

**Shift_JIS / windows-31j (CP932)**

Shift_JIS has three byte categories:
- `0x00–0x7F` — single-byte ASCII
- `0xA1–0xDF` — single-byte half-width katakana
- `0x81–0x9F` or `0xE0–0xFC` — lead byte of a double-byte character; trail
  must be `0x40–0x7F` or `0x80–0xFF` (note: `0x7F` is excluded in strict JIS
  but the grammar walker permits it since CP932 uses it)
- `0x80`, `0xA0`, `0xFD–0xFF` — invalid

**Shift_JIS / IBM424 ambiguity**: EBCDIC space is `0x40`.  In Shift_JIS, `0x40`
is a valid trail byte (it encodes several double-byte characters when preceded
by a lead byte in `0x81–0x9F` or `0xE0–0xFC`).  The `checkIbm424` structural
rule guards against this false positive: when counting `0x40` as EBCDIC space,
it first checks whether the preceding byte is a Shift_JIS lead byte and, if so,
discounts the `0x40` from the EBCDIC space count.  The same guard is applied in
`checkIbm500`.

**EUC-JP**
- `0x00–0x8D` — single byte
- `0xA1–0xFE` — lead byte; trail must be `≥ 0xA1`
- `0x8E` — SS2 (single-shift 2, half-width katakana); next byte `≥ 0xA1`
- `0x8F` — SS3 (single-shift 3, JIS X 0212 supplementary); next two bytes both `≥ 0xA1`
- `0x8E–0x9F` or `0xFF` — invalid outside SS2/SS3 context

**EUC-KR**: same two-byte structure as EUC-JP without SS2/SS3 extensions.
The shared EUC walker handles both, since any SS2/SS3 sequences that
validate grammatically are simply counted as double-byte characters.

**Big5 / Big5-HKSCS**
- `0x00–0x7F` or `0xFF` — single byte
- `0x81–0xFE` — lead byte; trail must be `0x40–0x7E` or `0x80–0xFE`
  (`0x7F` and `0xFF` are explicitly invalid trail bytes)
- `0x80` — invalid

The same walker covers both Big5 and Big5-HKSCS since their byte grammars are
identical; the difference is only in which code points the double-byte pairs
map to.

**GB18030 / GBK / GB2312**
- `0x00–0x80` — single byte
- `0x81–0xFE` — lead byte of either:
  - A **two-byte** sequence: trail `0x40–0x7E` or `0x80–0xFE` (GBK/GB2312 range)
  - A **four-byte** sequence: second `0x30–0x39`, third `0x81–0xFE`, fourth
    `0x30–0x39` (GB18030-only extension for rare Unicode code points)

The four-byte case is the structural fingerprint used by
`hasGb18030FourByteSequence` to upgrade GBK/GB2312 model predictions to
GB18030 when minority-language or rare-Unicode content is present.  The digit
bytes (`0x30–0x39`) in positions 2 and 4 are impossible in GBK/GB2312 trail
position, making a single valid 4-tuple unambiguous.

---

## Supported Encodings

The pipeline supports two tiers of detection:

- **Structural-only**: detected by deterministic rules before the model runs;
  if the rule fires the model is never invoked
- **ML + structural**: in the ML model's 32-class training set, but also
  covered by a structural pre-filter that fires first on clear cases
- **ML-only**: detected solely by the statistical model

### Full encoding list

| Encoding | Detection | Family | Notes |
|---|---|---|---|
| UTF-8 | ML + structural | Unicode | `checkAscii` → UTF-8 for pure-7-bit input; model handles high-byte UTF-8 |
| UTF-16-LE | ML + structural | Unicode | ML class; `WideUnicodeDetector` fires first via null-byte analysis |
| UTF-16-BE | ML + structural | Unicode | ML class; `WideUnicodeDetector` fires first via null-byte analysis |
| UTF-32-LE | ML + structural | Unicode | ML class; `WideUnicodeDetector` fires first via null-byte analysis |
| UTF-32-BE | ML + structural | Unicode | ML class; `WideUnicodeDetector` fires first via null-byte analysis |
| US-ASCII | Structural only | Unicode | `checkAscii` → UTF-8 (ASCII is a strict UTF-8 subset); model never runs |
| Shift_JIS | ML-only | Japanese | CJK grammar walker validates lead/trail byte structure post-model |
| EUC-JP | ML-only | Japanese | CJK grammar walker with SS2/SS3 extension handling |
| ISO-2022-JP | Structural only | Japanese | `detectIso2022` ESC sequence `ESC $ B` or `ESC $ @` |
| EUC-KR | ML-only | Korean | CJK grammar walker (shares EUC structure with EUC-JP) |
| ISO-2022-KR | Structural only | Korean | `detectIso2022` ESC sequence `ESC $ ) C` |
| GB18030 | ML-only | Chinese (Simplified) | CJK grammar walker; 4-byte sequences upgrade GBK/GB2312 predictions |
| GBK | ML-only | Chinese (Simplified) | CJK grammar walker |
| GB2312 | ML-only | Chinese (Simplified) | CJK grammar walker |
| Big5 | ML-only | Chinese (Traditional) | CJK grammar walker; sourced from Cantonese Wikipedia |
| Big5-HKSCS | ML-only | Chinese (Traditional) | CJK grammar walker; superset of Big5 |
| HZ-GB-2312 | Structural only | Chinese (Simplified) | `checkHz` — `~{`/`~}` switching markers; 7-bit encoding, no high bytes |
| ISO-2022-CN | Structural only | Chinese (Simplified) | `detectIso2022` ESC sequence `ESC $ ) A` or `ESC $ ) G` |
| windows-1252 | ML-only | Western European | Covers ISO-8859-1 and ISO-8859-15 via C1-byte upgrade rule |
| windows-1250 | ML-only | Central/Eastern European | Covers ISO-8859-2 via C1-byte upgrade rule |
| windows-1251 | ML-only | Cyrillic | Covers ISO-8859-5 via C1-byte upgrade rule |
| windows-1253 | ML-only | Greek | Covers ISO-8859-7 via C1-byte upgrade rule |
| windows-1254 | ML-only | Turkish | Covers ISO-8859-9 via C1-byte upgrade rule |
| windows-1255 | ML-only | Hebrew | Covers ISO-8859-8 via C1-byte upgrade rule |
| windows-1256 | ML-only | Arabic | Covers ISO-8859-6 via C1-byte upgrade rule |
| windows-1257 | ML-only | Baltic | Covers ISO-8859-4 and ISO-8859-13 via C1-byte upgrade rule |
| windows-1258 | ML-only | Vietnamese | No ISO-8859 equivalent; uses combining diacritics (NFD-style) |
| ISO-8859-3 | ML-only | Maltese | Only ISO-8859-X without a Windows equivalent (ħ, ġ, ċ, ż) |
| KOI8-R | ML-only | Cyrillic | Russian; soft-confusable with KOI8-U |
| KOI8-U | ML-only | Cyrillic | Ukrainian; soft-confusable with KOI8-R |
| IBM855 | ML-only | Cyrillic | Old Cyrillic EBCDIC |
| IBM866 | ML-only | Cyrillic | DOS Cyrillic |
| x-mac-cyrillic | ML-only | Cyrillic | Mac OS Cyrillic |
| TIS-620 | ML-only | Thai | Also known as CP874 |
| IBM500 | ML + structural | EBCDIC | `checkIbm500` fires first (EBCDIC space + Latin letter density); model is fallback |
| IBM424-ltr | ML + structural | EBCDIC Hebrew | `checkIbm424` fires first; model resolves ltr/rtl when rule is insufficient |
| IBM424-rtl | ML + structural | EBCDIC Hebrew | Same code page as IBM424-ltr, differs only in text-reversal convention |
| IBM420-ltr | Aspirational | EBCDIC Arabic | In `LANG_CHARSETS`; skipped when Python `cp420` codec is unavailable |
| IBM420-rtl | Aspirational | EBCDIC Arabic | Same; no structural rule implemented yet |

### Counts

| Category | Count |
|---|---|
| ML model classes | 32 |
| Structural-only (no ML) | 6 (US-ASCII, UTF-16/32 ×4 via Wide, ISO-2022-JP/KR/CN, HZ) |
| ML + structural pre-filter | 5 (UTF-16/32 ×4, IBM500, IBM424-ltr/rtl) |
| Aspirational (codec-dependent) | 2 (IBM420-ltr/rtl) |
| **Total encodings handled** | **43** |

> **Note on IBM420**: Arabic EBCDIC is included in the language-to-charset
> mapping but training is skipped at runtime when the `cp420` Python codec is
> unavailable (common on macOS and Python 3.14+).  No structural rule for
> IBM420 exists yet.  Contributions welcome.

---

## Data Sources

Training, devtest, and test splits are generated from two sources:

### 1. MADLAD-400

[MADLAD-400](https://arxiv.org/abs/2309.04662) is a multilingual document-level
dataset covering 400+ languages, released under Creative Commons.  For this
project the per-language `sentences_madlad.txt` files are used (one sentence
per line, no tab prefix).  It provides the primary training signal for all
charsets except Traditional Chinese.

Download with the bundled helper:

```bash
python download_madlad.py ~/datasets/madlad/data
```

### 2. Cantonese Wikipedia (zh_yuewiki)

Big5 and Big5-HKSCS require Traditional Chinese text.  MADLAD's Traditional
Chinese coverage is limited to Simplified-leaning sources; Cantonese Wikipedia
(`zh_yuewiki`) provides a corpus of ~940 000 clean Cantonese Traditional
Chinese sentences extracted from the MediaWiki XML dump.

**Why zh_yuewiki:**
- Native Traditional Chinese script (not OpenCC-converted)
- Large enough for 20 000 train + 2 000 devtest + 5 000 test samples each for
  Big5 and Big5-HKSCS without repetition
- 100% of sentences round-trip through both Big5 and Big5-HKSCS codecs without
  loss (typical Cantonese text stays within the common Big5 code range)

**Extraction**: The XML dump cannot be processed with `wikiextractor` on
Python 3.14+ due to regex compatibility issues.  A custom script
`extract_wiki_sentences.py` (in `src/test/python/`) parses the bzip2-compressed
XML directly, strips wikitext markup with lightweight regexes, and writes one
sentence per line.

```bash
# Download the Cantonese Wikipedia dump (~123 MB compressed)
wget https://dumps.wikimedia.org/zh_yuewiki/latest/zh_yuewiki-latest-pages-articles.xml.bz2

# Extract sentences (~940K sentences, ~130 MB plain text)
python extract_wiki_sentences.py zh_yuewiki-latest-pages-articles.xml.bz2 \
    > ~/datasets/zh_yuewiki/sentences_zh_yue.txt
```

### Encoding coverage

Not all source languages can be encoded into all charsets.  The following
charsets were **excluded** because they are superseded by a Windows equivalent
and the ISO/Windows distinction is better resolved by the C1-byte structural
rule at inference time:

> ISO-8859-1, ISO-8859-2, ISO-8859-4, ISO-8859-5, ISO-8859-6,
> ISO-8859-7, ISO-8859-8, ISO-8859-9, ISO-8859-13, ISO-8859-15

**ISO-8859-3 is kept** (Maltese — ħ, ġ, ċ, ż are not representable in any
Windows charset).

**EUC-TW is omitted**: Python has no `euc_tw` codec (JDK-only extension)
and the encoding is vanishingly rare in practice, superseded by Big5 and UTF-8.

---

## Generating the Dataset

All scripts live in `src/test/python/`.

### Step 1 — Build training, devtest, and test splits

`build_charset_training.py` loads sentences from MADLAD (and zh_yuewiki for
the `yue` virtual language), applies quality gates, and writes per-charset
gzipped binary files in each split directory.

**Encoding quality gates (both must pass):**

1. **Round-trip verification**: encode with `errors='strict'`, decode back,
   verify the result is identical to the original.  Any character that fails
   strict encoding causes the sentence to be skipped.  This catches all silent
   failures without needing a survival-ratio heuristic.

2. **High-byte ratio**: the encoded chunk must have enough bytes ≥ 0x80 to
   carry encoding-specific signal.  Thresholds by family:

   | Encoding family | Min high-byte ratio | Rationale |
   |---|---|---|
   | CJK multibyte (Big5, EUC-*, GBK, …) | ≥ 20% | Each CJK character = 2 high bytes; sparse chunks indicate wrong source language |
   | UTF-8 | ≥ 5% | Ensures enough lead/continuation byte pairs for the model to learn Latin-Extended patterns |
   | SBCS / other legacy | ≥ 2% | Even 1 accented character per 50 bytes is informative |
   | ASCII, ISO-2022, HZ, UTF-16/32 | exempt | No high bytes by design |

**Split sizes (defaults):**

| Split | Samples/charset |
|---|---|
| train | 20 000 |
| devtest | 2 000 |
| test | 5 000 |

```bash
python build_charset_training.py \
    --madlad-dir  ~/datasets/madlad/data \
    --zh-yue-file ~/datasets/zh_yuewiki/sentences_zh_yue.txt \
    --output-dir  ~/datasets/madlad/charset-detect3
```

The script prints a summary of samples written per charset per split.

### Step 2 — Compile the tools

```bash
cd /path/to/tika-main-chardet
mvn -pl tika-core -am -DskipTests install -q
mvn -pl tika-ml/tika-ml-chardetect -am -DskipTests compile test-compile -q

# Export classpath once for subsequent Java invocations
CP=$(mvn -pl tika-ml/tika-ml-chardetect -q dependency:build-classpath \
         -Dmdep.outputFile=/dev/stdout 2>/dev/null)
JARS="tika-ml/tika-ml-chardetect/target/classes:\
tika-ml/tika-ml-chardetect/target/test-classes:$CP"
```

### Step 3 — Train

```bash
java -cp "$JARS" \
  org.apache.tika.ml.chardetect.tools.TrainCharsetModel \
  --data    ~/datasets/madlad/charset-detect3/train \
  --output  ~/datasets/madlad/chardetect.bin \
  --buckets 8192 \
  --epochs  5
```

The trainer prints per-epoch loss and in-sample accuracy, then a per-charset
breakdown on the training data.  Copy the model to its classpath resource
location to make it the active bundled model:

```bash
cp ~/datasets/madlad/chardetect.bin \
   src/main/resources/org/apache/tika/ml/chardetect/chardetect.bin
```

### Step 4 — Evaluate

```bash
java -cp "$JARS" \
  org.apache.tika.ml.chardetect.tools.EvalCharsetDetectors \
  --model   ~/datasets/madlad/chardetect.bin \
  --data    ~/datasets/madlad/charset-detect3/test \
  --lengths 20,50,100,200,full \
  --confusion
```

The tool reports six columns per charset:

| Column | Description |
|---|---|
| `Stat` | ML model only, no post-processing rules |
| `+ISO` | + C1-byte → Windows-12XX upgrade |
| `+CJK` | + CJK grammar walkers |
| `All` | All ML rules enabled (no Wide pre-filter) |
| `Pipeline` | `WideUnicodeDetector` + all ML rules (production configuration) |
| `ICU4J` | `Icu4jEncodingDetector` baseline |
| `juniv` | `UniversalEncodingDetector` baseline |

Each column shows **R%** (strict — exact charset match) and **S%** (soft —
exact or confusable-group match).  A timing row (`µs/sample`) is printed below
each probe-length section.

### Bucket size sweep

Evaluation across bucket sizes showed negligible accuracy differences,
confirming the feature space is not the bottleneck:

| Buckets | Model size | Strict (full) | Soft (full) |
|---|---|---|---|
| 65 536 | 2.0 MB | 70.6% | 85.6% |
| 32 768 | 1.0 MB | 70.9% | 85.6% |
| 16 384 | 512 KB | 71.2% | 85.6% |
| **8 192** | **257 KB** | **70.9%** | **85.6%** |
| 4 096 | 129 KB | 70.3% | 85.5% |

(ML-All column, held-out test set at full probe length.)

The production model uses 8 192 buckets as the best accuracy-to-size tradeoff.

---

## Evaluation Results

Held-out test set: **5 000 samples/charset**, 36 charsets, generated from
MADLAD-400 + Cantonese Wikipedia (zh_yuewiki).  Big5/Big5-HKSCS samples come
exclusively from zh_yuewiki; all other charsets from MADLAD.  All splits are
genuine held-out — no training sentences appear in devtest or test.

**R%** = strict accuracy (exact charset name match).
**S%** = soft accuracy (exact or confusable-group match).

```
=== Probe length: 20B ===
Charset                    N  | Stat R%  S%  | +ISO R%  S%  | +CJK R%  S%  |  All R%  S%  | Pipe R%  S%  | ICU4J R%  S% | juniv R%  S% |
-------------------------------------------------------------------------------------------------------------------------------------------
Big5-HKSCS              5000  |  77.0  99.7  |  74.7  96.4  |  74.7  96.4  |  74.7  96.4  |  74.7  96.4  |   0.0  97.6  |   0.0  71.2  |
Big5                    5000  |  67.9  99.3  |  64.9  95.5  |  64.9  95.5  |  64.9  95.5  |  64.9  95.5  |  97.3  97.3  |  98.0  98.0  |
EUC-JP                  5000  |  94.5  94.5  |  94.5  94.5  |  94.5  94.5  |  94.5  94.5  |  94.5  94.5  |  83.8  83.8  |  91.2  91.2  |
EUC-KR                  5000  |  91.6  91.6  |  91.6  91.6  |  91.6  91.6  |  91.6  91.6  |  91.6  91.6  |  92.0  92.0  |  91.7  91.7  |
GB18030                 5000  |  11.6  99.8  |  11.6  99.8  |  11.6  99.8  |  17.0  99.8  |  17.0  99.8  |  99.4  99.4  |  99.9  99.9  |
GBK                     5000  |  76.7  99.9  |  76.7  99.9  |  76.7  99.9  |  76.7  99.9  |  76.7  99.9  |   0.0  99.4  |   0.0  99.8  |
...
UTF-16-BE               5000  |   0.0   0.0  |   0.0   0.0  |   0.0   0.0  |   0.0   0.0  |  97.8  97.8  |  69.9  69.9  |   0.4   0.4  |
UTF-16-LE               5000  |   0.0   0.0  |   0.0   0.0  |   0.0   0.0  |   0.0   0.0  |  98.1  98.1  |  69.5  69.5  |   0.5   0.5  |
UTF-32-BE               5000  |   0.0   0.0  |   0.0   0.0  |   0.0   0.0  |   0.0   0.0  |  99.8  99.8  | 100.0 100.0  |   0.6   0.6  |
UTF-32-LE               5000  |   0.0   0.0  |   0.0   0.0  |   0.0   0.0  |   0.0   0.0  |  99.8  99.8  | 100.0 100.0  |   0.6   0.6  |
-------------------------------------------------------------------------------------------------------------------------------------------
OVERALL               179470  |  41.2  48.5  |  48.1  61.8  |  48.1  61.8  |  48.1  61.8  |  50.0  61.2  |  25.8  38.6  |  32.2  43.8  |
  µs/sample                   |        11.1  |         9.7  |         9.7  |         9.6  |         7.2  |        11.4  |         4.0  |

=== Probe length: 100B ===
OVERALL               179470  |  62.0  73.6  |  68.0  82.8  |  68.0  82.8  |  68.0  82.8  |  72.4  85.1  |  46.8  69.9  |  40.1  55.7  |
  µs/sample                   |         8.3  |         7.8  |         7.5  |         7.4  |         5.5  |        36.1  |         6.1  |

=== Probe length: full ===
OVERALL               179470  |  65.3  77.4  |  70.7  85.6  |  70.7  85.6  |  70.9  85.6  |  78.0  91.4  |  51.7  74.0  |  40.7  56.5  |
  µs/sample                   |        12.5  |        12.6  |        10.8  |        10.8  |         8.5  |       167.1  |        18.3  |
```

### Notes on specific charsets

**UTF-16/32 — 0% strict in `All`, 98–100% in `Pipeline`**: the ML model has
no structural mechanism to distinguish wide encodings; `WideUnicodeDetector`
handles them before ML is invoked.  The `Pipeline` column is the correct metric
for production use.

**Pipeline is fastest**: `WideUnicodeDetector` short-circuits ~11% of samples
(the UTF-16/32 ones) before the ML model runs, reducing average latency below
any single-detector configuration.

**ICU4J at 167 µs/full vs Pipeline at 8.5 µs/full**: ICU4J's statistical
tables require a full-probe pass with complex byte-frequency analysis; the ML
model uses sparse hash lookups over high bytes only.

**GB18030 low strict / high soft**: the GB family is a strict subset chain
(GB2312 ⊂ GBK ⊂ GB18030).  When ML predicts GBK for a GB18030 file, the text
decodes correctly unless the file uses GB18030-specific 4-byte sequences (rare
minority-language characters).  The `GB_FOUR_BYTE_UPGRADE` rule catches those
cases.  ICU4J's 99% strict score for GB18030 reflects grammar-based rules; the
practical decoding difference is small for typical Han Chinese content.

**Big5 vs Big5-HKSCS**: both charsets encode Cantonese Wikipedia identically
at the byte level (the HKSCS extension characters are rare in typical text).
The model learns to distinguish them from subtle frequency differences.  When
it predicts Big5 for a Big5-HKSCS file, the only risk is HKSCS extension
characters being mis-decoded; predicting Big5-HKSCS for a Big5 file is always
safe (superset decodes the subset correctly).

**IBM424 0% strict / ~100% soft**: the structural rule detects EBCDIC Hebrew
correctly but does not determine directionality (ltr vs rtl), so the result is
always the confusable-group partner — a soft hit.

---

## Source Language → Charset Mapping

The pipeline maps ISO 639-3 language codes to applicable legacy charsets.
Key decisions:

- **Big5 / Big5-HKSCS**: sourced from Cantonese Wikipedia (`yue` virtual
  language).  MADLAD Simplified Chinese is excluded because simplified
  characters are generally not encodable in plain Big5.
- **IBM424**: Hebrew (`heb`) only, both LTR (logical) and RTL (visual/reversed)
  variants use the same IBM424 code page.
- **IBM500**: all languages that can encode into Latin-1 (English, French,
  German, Spanish, Dutch, etc.) — IBM500 is a Latin EBCDIC code page.
- **UTF-8 / UTF-16 / UTF-32**: all languages contribute.
- **US-ASCII**: English (`eng`) only.
- **ISO-8859-3**: Maltese (`mlt`) — the only charset for which ħ, ġ, ċ, ż are
  representable without a Windows equivalent.
