#!/usr/bin/env bash
# Train the CharSoup language detection model against the local .m2repo.
#
# Three-step pipeline:
#   Step 1 (PrepareCorpus)     Read MADLAD corpus, sample, dedup, split into
#                              pool/ + dev.txt + test_raw.txt.
#                              Skipped if output already exists in PREP_RAW.
#   Step 2 (Python)            fastText-filter contamination from pool + dev + test.
#                              Skipped if PREP_CLEAN already exists.
#   Step 3 (Java train)        Train on clean pool, self-clean (2-pass), eval.
#
# Prerequisites (run once before this script):
#   python3 tika-langdetect/tika-langdetect-charsoup/src/test/python/filter_pashto.py \
#       ~/datasets/madlad/data
#
# Quality evaluation (run after Step 3):
#   ./run-compare.sh <model>                                  # flores dev (intermediate)
#   ./run-compare.sh flores200_devtest.tsv <model>            # flores devtest (final only)
#
# Usage:
#   ./run-lang-train.sh [extra TrainLanguageModel args...]
#
# Extra args (e.g. --max-train, --buckets) are forwarded to both Step 1 and Step 3,
# so a single flag controls the full run. Flags in $@ override the hardcoded defaults.
#
# Environment overrides:
#   MADLAD_CORPUS      raw corpus dir  (default: ~/datasets/madlad/data)
#   MADLAD_OUTPUT      output dir      (default: ~/datasets/madlad/lang-detect)
#   MODEL_NAME         model filename  (default: model-16k.bin)
#   NO_FASTTEXT=1      skip Step 2 (fastText filter); train from PREP_RAW.
#                      Combined with NO_SCRIPT_FILTER=0 (default): script-only run.
#   NO_SCRIPT_FILTER=1 disable script-purity filter in PrepareCorpus Step 1.
#                      Combined with NO_FASTTEXT=0 (default): fastText-only run.
#                      Uses a separate prep dir (preprocessed_noscript/) so the
#                      standard preprocessed/ dir is not overwritten.
#   UNK_PER_LANG=N     sample N sentences per excluded/dropped language into an
#                      "unk" class (default: 0 = disabled).
#
# Comparison recipes:
#   Both filters (default):
#     ./run-lang-train.sh
#   Script purity only:
#     NO_FASTTEXT=1 MODEL_NAME=model-16k-script.bin ./run-lang-train.sh
#   FastText only:
#     NO_SCRIPT_FILTER=1 MODEL_NAME=model-16k-fasttext.bin ./run-lang-train.sh
#   No external cleaning (two-pass self-cleaning only):
#     NO_FASTTEXT=1 NO_SCRIPT_FILTER=1 MODEL_NAME=model-16k-raw.bin ./run-lang-train.sh
#   FastText + unk class experiment:
#     NO_SCRIPT_FILTER=1 UNK_PER_LANG=500 MODEL_NAME=model-16k-unk.bin ./run-lang-train.sh
#
# Examples:
#   ./run-lang-train.sh
#   ./run-lang-train.sh --buckets 32768
#   MADLAD_OUTPUT=~/datasets/madlad/lang-detect ./run-lang-train.sh

set -euo pipefail

TIKA_ROOT="$(cd "$(dirname "$0")" && pwd)"
LOCAL_REPO="$TIKA_ROOT/.m2repo"
MODULE="$TIKA_ROOT/tika-langdetect/tika-langdetect-charsoup"
CORE="$TIKA_ROOT/tika-langdetect/tika-langdetect-charsoup-core"
EVAL="$TIKA_ROOT/tika-eval/tika-eval-core"
PYTHON_DIR="$MODULE/src/test/python"
# Python with fasttext installed (venv with --system-site-packages)
PYTHON="${VENV_PYTHON:-$HOME/venvs/ml/bin/python3}"

# ── Classpath ──
CP="$CORE/target/classes:$MODULE/target/test-classes:$MODULE/target/classes"
CP="$CP:$EVAL/target/test-classes:$EVAL/target/classes"
for jar in \
    "$LOCAL_REPO/org/apache/tika/tika-core/4.0.0-SNAPSHOT/"*SNAPSHOT.jar; do
    [[ -f "$jar" ]] && CP="$CP:$jar"
done
for jar in \
    "$LOCAL_REPO/org/apache/tika/tika-ml-core/4.0.0-SNAPSHOT/"*SNAPSHOT.jar; do
    [[ -f "$jar" ]] && CP="$CP:$jar"
done

# ── Paths ──
CORPUS="${MADLAD_CORPUS:-$HOME/datasets/madlad/data}"
OUTPUT_DIR="${MADLAD_OUTPUT:-$HOME/datasets/madlad/lang-detect}"
MODEL_NAME="${MODEL_NAME:-model-16k.bin}"
MODEL_FILE="$OUTPUT_DIR/$MODEL_NAME"
# NO_SCRIPT_FILTER=1 uses a separate prep dir so the standard one is not clobbered
if [[ "${NO_SCRIPT_FILTER:-0}" == "1" ]]; then
    PREP_RAW="$OUTPUT_DIR/preprocessed_noscript"
else
    PREP_RAW="$OUTPUT_DIR/preprocessed"
fi
PREP_CLEAN="$OUTPUT_DIR/preprocessed_clean"

echo "=== CharSoup Training Pipeline ==="
echo "  Corpus        : $CORPUS"
echo "  Output dir    : $OUTPUT_DIR"
echo "  Model         : $MODEL_FILE"
echo "  Prep dir      : $PREP_RAW"
echo "  Script filter : ${NO_SCRIPT_FILTER:-0} (0=enabled, 1=disabled)"
echo "  FastText      : ${NO_FASTTEXT:-0} (0=enabled, 1=disabled)"
echo ""

# ── Step 1: Data preparation ──
SCRIPT_FILTER_FLAG=""
[[ "${NO_SCRIPT_FILTER:-0}" == "1" ]] && SCRIPT_FILTER_FLAG="--no-script-filter"

if [[ -d "$PREP_RAW/pool" && -f "$PREP_RAW/dev.txt" \
      && -f "$PREP_RAW/test_raw.txt" ]]; then
    echo "--- Step 1: Skipping data prep (already exists at $PREP_RAW) ---"
else
    echo "--- Step 1: Data preparation ---"
    UNK_FLAG=""
    [[ "${UNK_PER_LANG:-0}" != "0" ]] && UNK_FLAG="--unk-per-lang ${UNK_PER_LANG}"
    java -Xmx8g -cp "$CP" \
        org.apache.tika.langdetect.charsoup.tools.PrepareCorpus \
        --corpus "$CORPUS" \
        --output-dir "$PREP_RAW" \
        --max-train 100000 \
        $SCRIPT_FILTER_FLAG \
        $UNK_FLAG
fi

echo ""

# ── Step 2: fastText contamination filter ──
# When NO_SCRIPT_FILTER=1 the fastText-clean dir is named accordingly
# so both comparison artefacts can coexist on disk.
if [[ "${NO_SCRIPT_FILTER:-0}" == "1" ]]; then
    PREP_CLEAN="$OUTPUT_DIR/preprocessed_noscript_clean"
fi

if [[ "${NO_FASTTEXT:-0}" == "1" ]]; then
    echo "--- Step 2: Skipping fastText filter (NO_FASTTEXT=1) ---"
    TRAIN_DIR="$PREP_RAW"
elif [[ -d "$PREP_CLEAN/pool" && -f "$PREP_CLEAN/dev.txt" \
      && -f "$PREP_CLEAN/test_raw.txt" ]]; then
    echo "--- Step 2: Skipping fastText filter (already exists at $PREP_CLEAN) ---"
    TRAIN_DIR="$PREP_CLEAN"
else
    echo "--- Step 2: fastText contamination filter ---"
    "$PYTHON" "$PYTHON_DIR/filter_contamination.py" "$PREP_RAW" "$PREP_CLEAN"
    TRAIN_DIR="$PREP_CLEAN"
fi

echo ""

# ── Step 3: Train ──
echo "--- Step 3: Training ---"
java -Xmx8g -cp "$CP" \
    org.apache.tika.langdetect.charsoup.tools.TrainLanguageModel \
    --corpus "$CORPUS" \
    --output "$MODEL_FILE" \
    --prep-dir "$TRAIN_DIR" \
    --buckets 16384 \
    --max-train 100000 \
    "$@"

echo ""

# ── Step 4: Common tokens ──
# Generate tika-eval common-token files for exactly the languages in the
# trained model. Using --model ensures the token files and the model are
# always in sync — no guessing, no drift.
COMMON_TOKENS_DIR="$TIKA_ROOT/tika-eval/tika-eval-core/src/main/resources/common_tokens"
# Use the fastText-cleaned pool (before mislabel filtering) as the source.
# This gives representative, contamination-filtered text without the mislabel
# filter's aggressive sentence removal, which is specific to model training.
FASTTEXT_POOL="$TRAIN_DIR/pool"
echo "--- Step 4: Common token generation ---"
echo "  Model  : $MODEL_FILE"
echo "  Pool   : $FASTTEXT_POOL"
echo "  Output : $COMMON_TOKENS_DIR"
java -Xmx4g -cp "$CP" \
    org.apache.tika.eval.core.tokens.tools.CommonTokenGenerator \
    "$FASTTEXT_POOL" \
    "$COMMON_TOKENS_DIR" \
    30000 10 \
    --model "$MODEL_FILE"

echo ""
echo "=== Pipeline complete ==="
echo "  Model        : $MODEL_FILE"
echo "  Common tokens: $COMMON_TOKENS_DIR"
