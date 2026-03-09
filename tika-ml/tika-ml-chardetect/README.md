# tika-ml-chardetect — Charset Detection

A lightweight, production-ready charset/encoding detector for Apache Tika built
on multinomial logistic regression over byte n-gram features.

## Documentation

Architecture, algorithm details, accuracy numbers, and comparison with ICU4J /
juniversalchardet are in the main Tika docs:

**`docs/modules/ROOT/pages/advanced/charset-detection-design.adoc`**

## Rebuilding the model

### Prerequisites

* Java 17+
* MADLAD-400 corpus (`sentences_madlad.txt` per language, one sentence per line)
* Cantonese Wikipedia sentences for Big5/Big5-HKSCS (see adoc above)

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

### Step 2 — Train

```bash
java -cp $JAR \
    org.apache.tika.ml.chardetect.tools.TrainCharsetModel \
    --data    ~/datasets/charset-detect/train \
    --output  ~/datasets/chardetect.bin \
    --buckets 16384 \
    --epochs  5

# Install as the bundled model
cp ~/datasets/chardetect.bin \
   tika-encoding-detectors/tika-encoding-detector-mojibuster/src/main/resources/org/apache/tika/ml/chardetect/chardetect.bin
```

### Step 3 — Evaluate

```bash
java -cp $JAR \
    org.apache.tika.ml.chardetect.tools.EvalCharsetDetectors \
    --model   ~/datasets/chardetect.bin \
    --data    ~/datasets/charset-detect/test \
    --lengths 20,50,100,200,full \
    --confusion
```

## Data sources

| Source | Usage |
|---|---|
| [MADLAD-400](https://arxiv.org/abs/2309.04662) | Primary training corpus (400+ languages, CC licensed) |
| Cantonese Wikipedia (`zh_yuewiki`) | Big5 / Big5-HKSCS training data (Traditional Chinese) |

## Supported charsets

37 direct model labels covering CJK multibyte, Unicode (UTF-8/16/32),
EBCDIC variants (IBM500/1047/424/420/850/852/855/866), Cyrillic (KOI8-R/U,
windows-1251, IBM855, x-mac-cyrillic), Arabic (windows-1256), Thai (TIS-620),
Vietnamese (windows-1258), and all major Windows-12XX / ISO-8859-X families.
ISO-2022-JP/KR/CN and HZ-GB-2312 are detected via structural rules before the
model runs.
