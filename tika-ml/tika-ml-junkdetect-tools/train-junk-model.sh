#!/usr/bin/env bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Rebuilds the bundled junk-detector model from a clean-text corpus.
#
# CORPUS is the committed record of which data trained the bundled model:
# change it here and commit, never pass it on the command line. The run writes
# junkdetect.bin plus junkdetect.bin.provenance (model digest, corpus manifest
# digest, git state, every training constant); commit both together.
#
# Usage: TIKA_JUNK_DATA=<dir containing $CORPUS> tika-ml/tika-ml-junkdetect-tools/train-junk-model.sh
# Optional: JAVA_OPTS (default -Xmx12g), MAVEN_REPO_LOCAL (default <repo>/.local_m2_repo).

set -euo pipefail

CORPUS=junk-clean-20261103

: "${TIKA_JUNK_DATA:?set TIKA_JUNK_DATA to the directory that holds ${CORPUS}}"
SRC="${TIKA_JUNK_DATA}/${CORPUS}"
[ -f "${SRC}/manifest.tsv" ] || { echo "no manifest.tsv in ${SRC}" >&2; exit 1; }

ROOT=$(git rev-parse --show-toplevel)
cd "${ROOT}"

# An uncommitted tree is allowed (experiments), but the exact diff is kept beside the log,
# untracked (not ignored) files included: they are built into the jar too.
UNCOMMITTED=$(mktemp)
git diff HEAD --binary > "${UNCOMMITTED}"
git ls-files --others --exclude-standard -z | while IFS= read -r -d '' f; do
  git diff --no-index --binary /dev/null "$f" >> "${UNCOMMITTED}" || true
done
GIT=$(git rev-parse HEAD)
if [ -s "${UNCOMMITTED}" ]; then
  DIFF_SHA=$(sha256sum < "${UNCOMMITTED}" | cut -c1-12)
  GIT="${GIT}-dirty-${DIFF_SHA}"
fi
RUN="${TIKA_JUNK_DATA}/${CORPUS}-runs/$(date +%Y%m%d-%H%M%S)-${GIT:0:12}"
BUILT="${RUN}/built"
mkdir -p "${RUN}"
mv "${UNCOMMITTED}" "${RUN}/uncommitted.diff"
LOG="${RUN}/train.log"
echo "corpus=${CORPUS} git=${GIT} run=${RUN}" | tee "${LOG}"

REPO="${MAVEN_REPO_LOCAL:-${ROOT}/.local_m2_repo}"
./mvnw clean install -pl tika-ml/tika-ml-junkdetect-tools -am -Pfast,train \
  -Dmaven.repo.local="${REPO}" >> "${LOG}" 2>&1
JAR=$(ls tika-ml/tika-ml-junkdetect-tools/target/tika-ml-junkdetect-tools-*-tools.jar)
OUT=tika-ml/tika-ml-junkdetect/src/main/resources/org/apache/tika/ml/junkdetect/junkdetect.bin
JOPTS=${JAVA_OPTS:--Xmx12g}

# shellcheck disable=SC2086
java ${JOPTS} -cp "${JAR}" org.apache.tika.ml.junkdetect.tools.BuildJunkTrainingData \
  --data-dir "${SRC}" --output-dir "${BUILT}" 2>&1 | tee -a "${LOG}"
# shellcheck disable=SC2086
java ${JOPTS} -cp "${JAR}" org.apache.tika.ml.junkdetect.tools.TrainJunkModel \
  --data-dir "${BUILT}" --output "${OUT}" --git-commit "${GIT}" 2>&1 | tee -a "${LOG}"
cp "${OUT}.provenance" "${RUN}/"
echo "model: ${OUT}  provenance: ${OUT}.provenance  log: ${LOG}" | tee -a "${LOG}"
