# tika-ml-chardetect — Charset Detection

A byte-bigram Naive Bayes charset/encoding detector for Apache Tika,
shipped alongside dedicated structural detectors for UTF-32 (codepoint
validity) and UTF-16 (column-histogram maxent specialist).

## Documentation

Architecture, algorithm details, accuracy numbers, and comparison with
ICU4J / juniversalchardet are in the main Tika docs:

**`docs/modules/ROOT/pages/advanced/charset-detection-design.adoc`**

## Rebuilding the model

### Prerequisites

* Java 17+
* MADLAD-400 corpus (`sentences_madlad.txt` per language, one sentence per line)
* Cantonese Wikipedia sentences for Big5-HKSCS (see adoc above)

### Step 1 — Build training data

```bash
mvn package -pl tika-ml/tika-ml-chardetect -am -Ptrain -DskipTests \
    -Dcheckstyle.skip=true -q

JAR=tika-ml/tika-ml-chardetect/target/tika-ml-chardetect-*-tools.jar

java -cp $JAR \
    org.apache.tika.ml.chardetect.tools.BuildCharsetTrainingData \
    --madlad-dir  ~/datasets/madlad/data \
    --zh-yue-file ~/datasets/zh_yuewiki/sentences_zh_yue.txt \
    --output-dir  ~/datasets/charset-detect
```

### Step 2 — Train the NB model

```bash
java -cp $JAR \
    org.apache.tika.ml.chardetect.tools.TrainNaiveBayesBigram \
    --data      ~/datasets/charset-detect/train \
    --output    ~/datasets/nb-bigram.bin \
    --coverage  0.999 \
    --alpha-base 1.0 \
    --max-samples-per-class 50000

# Install as the bundled model
cp ~/datasets/nb-bigram.bin \
   tika-encoding-detectors/tika-encoding-detector-mojibuster/src/main/resources/org/apache/tika/ml/chardetect/nb-bigram.bin
```

### Step 3 — Evaluate

```bash
java -cp $JAR \
    org.apache.tika.ml.chardetect.tools.EvalCharsetDetectors \
    --nb-model ~/datasets/nb-bigram.bin \
    --data     ~/datasets/charset-detect/devtest \
    --lengths  20,100,256,full
```

Compares NB pipeline against ICU4J and juniversalchardet at each probe
length.

### Retraining the UTF-16 specialist (optional)

The UTF-16 specialist uses stride-2 column-histogram features and is
trained separately:

```bash
java -cp $JAR \
    org.apache.tika.ml.chardetect.tools.TrainUtf16Specialist \
    --data    ~/datasets/charset-detect/train \
    --output  tika-encoding-detectors/tika-encoding-detector-mojibuster/src/main/resources/org/apache/tika/ml/chardetect/utf16-specialist.bin
```

## Data sources

| Source | Usage |
|---|---|
| [MADLAD-400](https://arxiv.org/abs/2309.04662) | Primary training corpus (400+ languages, CC licensed) |
| Cantonese Wikipedia (`zh_yuewiki`) | Big5-HKSCS training data (Traditional Chinese) |

## Supported charsets

33 direct NB labels covering CJK multi-byte (Big5-HKSCS, EUC-JP,
GB18030, Shift_JIS, x-EUC-TW, x-windows-949), EBCDIC variants
(IBM420/424-ltr/rtl, IBM500, IBM1047), DOS OEM (IBM850/852/855/866),
Cyrillic (KOI8-R/U), Windows single-byte (1250-1258, 874),
ISO-8859-3/16, Mac (x-MacRoman, x-mac-cyrillic), and UTF-8.

UTF-32 is detected by `WideUnicodeDetector` (codepoint validity) and
UTF-16 by `Utf16SpecialistEncodingDetector`.  ISO-2022-JP / KR / CN /
HZ-GB-2312 are detected via structural rules.
