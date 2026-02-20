#!/usr/bin/env bash
# Run TrainLanguageModel against the local .m2repo.
# Usage: ./run-lang-train.sh [extra TrainLanguageModel args...]
#
# Examples:
#   ./run-lang-train.sh --buckets 32768 --output ~/datasets/madlad/lang-detect/exp2.bin
#   ./run-lang-train.sh --buckets 32768 --trigrams --output ~/datasets/madlad/lang-detect/exp4.bin

set -euo pipefail

TIKA_ROOT="$(cd "$(dirname "$0")" && pwd)"
LOCAL_REPO="$TIKA_ROOT/.m2repo"
MODULE="$TIKA_ROOT/tika-langdetect/tika-langdetect-charsoup"

CP="$MODULE/target/test-classes:$MODULE/target/classes"
for jar in "$MODULE/target/dependency/"*.jar; do CP="$CP:$jar"; done

# Prefer local-repo snapshot jars over potentially stale dependency-copy jars
for jar in \
    "$LOCAL_REPO/org/apache/tika/tika-ml-core/4.0.0-SNAPSHOT/"*SNAPSHOT.jar \
    "$LOCAL_REPO/org/apache/tika/tika-langdetect-charsoup/4.0.0-SNAPSHOT/"*SNAPSHOT.jar; do
    [[ -f "$jar" ]] && CP="$jar:$CP"
done

CORPUS="${MADLAD_CORPUS:-$HOME/datasets/madlad/data}"

exec java -cp "$CP" \
    org.apache.tika.langdetect.charsoup.tools.TrainLanguageModel \
    --corpus "$CORPUS" \
    "$@"
