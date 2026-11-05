# tika-ml-chardetect — Charset Detection tools

Evaluation and data tools for the charset detector in
`tika-encoding-detector-mojibuster`.  The runtime is
`MojibusterEncodingDetector`: structural checks for UTF-32 (codepoint
validity), UTF-8 (grammar) and ISO-2022 (escapes), then a hashed byte
n-gram model (`HashedNgramEncodingDetector`) for everything else,
UTF-16 and UTF-32 included.

## Documentation

Architecture and the detection chain are in the main Tika docs:

**`docs/modules/ROOT/pages/advanced/charset-detection-design.adoc`**

## The bundled model

`tika-encoding-detectors/tika-encoding-detector-mojibuster/src/main/resources/org/apache/tika/ml/chardetect/`

| File | What |
|---|---|
| `hashed-ngram.hng` | the model (format HNG2, below) |
| `hashed-ngram.hng.provenance` | `model_sha256`, md5, size, training data, gold version |

There is exactly one model; it is not configurable.  The model is trained
in PyTorch outside this repository and exported to HNG2 by the training
project's `export.py` (int8 embedding rows with a per-row float scale,
float32 dense layers).  No Python lives in this repository.

### Replacing the model

1. Copy the new `.hng` over `hashed-ngram.hng`.
2. Update `hashed-ngram.hng.provenance` (`model_sha256`, `model_md5`,
   `model_bytes`, training data and gold version).  `HashedNgramModelParityTest`
   fails until `model_sha256` matches.
3. Regenerate `src/test/resources/org/apache/tika/ml/chardetect/hashed-ngram-parity.tsv`
   with the training project's reference Java scorer (`HashNgramModel2`, which
   is checked against the PyTorch logits): same probe bytes, one row per probe,
   43 (or the new class count) logits, and the `model-md5` line.  The test fails
   on an md5 mismatch so a swap cannot pass against stale logits.  Keep every
   probe under 4,096 non-text bytes: past that the runtime may stop early
   (`HashedNgramModel.CHECKPOINT_NON_TEXT`), which the reference scorer does not.
4. If the label list changed, check `HashedNgramEncodingDetector.toJavaCharsetName`
   (every label but `binary` must name a Java charset) and run
   `./mvnw clean install` on the mojibuster, junkdetect and text-parser modules.

### HNG2 format

Big-endian, as written by `java.io.DataOutputStream`:

```
int     magic 0x484E4732 ("HNG2")
int     nClasses, then nClasses x writeUTF(label)
int     nOrders, then per order (ascending n):
          int n, int bits, int dim
          float[2^bits] rowScale
          byte[2^bits * dim] rows (int8, row-major)
int     nCols, then per column channel:
          int kind (0 = byte at even offset, 1 = byte at odd offset)
          int bits (8), int dim
          float[2^bits] rowScale
          byte[2^bits * dim] rows
int     colwin (column channels read the first colwin bytes)
int     asciiOrders (0: only n-grams holding a non-text byte count)
int     countFeature (1: append log1p(nonTextBytes) / 4)
int     pool (1 mean, 2 max, 3 mean then max)
int     hidden
float[hidden * inputDim]  w1 (row per hidden unit), float[hidden] b1
float[nClasses * hidden]  w2 (row per class),       float[nClasses] b2
```

Non-text bytes are everything except 0x20-0x7E, TAB, LF and CR.  An n-gram
value packs its bytes little-endian (`b0 | b1 << 8 | ...`).  Orders 1 and 2
with `bits >= 16` index rows directly; wider orders hash:
`((v + n * 0x100000000000L) * 0x9E3779B97F4A7C15L) >>> (64 - bits)`.
A row contributes `rowScale[row] * rows[row][j]`.  `inputDim` is
`poolWidth * sum(dim) + countFeature`, laid out channel by channel (orders,
then columns), each channel's mean block before its max block.  The hidden
layer is ReLU; the output is raw logits.  A probe with no non-text byte is not
scored (the detector treats it as ASCII).

## Data tools

```bash
./mvnw clean package -pl tika-ml/tika-ml-chardetect -am -Ptrain -DskipTests

JAR=tika-ml/tika-ml-chardetect/target/tika-ml-chardetect-*-tools.jar

java -cp $JAR org.apache.tika.ml.chardetect.tools.BuildCharsetTrainingData \
    --madlad-dir  <madlad>/data \
    --zh-yue-file <zh_yuewiki>/sentences_zh_yue.txt \
    --output-dir  <charset-detect>

# Bundled pipeline vs ICU4J and juniversalchardet at each probe length
java -jar $JAR --data <charset-detect>/devtest --lengths 20,100,256,full
```
