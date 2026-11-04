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
# Trains a hashed junk model (TIKA-5027) from train data only.
#
# The data names below are the committed record of what trained the model: change them here
# and commit, never pass them on the command line. The run directory gets the model, its
# .provenance (digests of every input, git state, every HashedTrainingConfig constant, the
# deferral sweep) and the log. Training choices live in HashedTrainingConfig.
#
# Usage: TIKA_JUNK_DATA=<dir holding the data below> tika-ml/tika-ml-junkdetect-tools/train-hashed-junk-model.sh
# Optional: JAVA_OPTS (default -Xmx22g), MAVEN_REPO_LOCAL (default <repo>/.local_m2_repo).

set -euo pipefail

CORPUS=junk-clean-20261103                                   # Wikipedia sentences per language
GOLD=charsets/gold-v181-train-halves.tsv                     # gold v1.8.1 cc-html train, even/odd halves, damage spans
SAMPLE=charsets/cchtml-train-unanimous/sample-gen-20261103.tsv  # cc-html train pages for real-decision pairs
SAMPLE_DOCS=charsets/cchtml-train-unanimous/docs

: "${TIKA_JUNK_DATA:?set TIKA_JUNK_DATA to the directory that holds ${CORPUS} and charsets/}"
D="${TIKA_JUNK_DATA}"
[ -f "${D}/${CORPUS}/manifest.tsv" ] || { echo "no manifest.tsv in ${D}/${CORPUS}" >&2; exit 1; }
for f in "${D}/${GOLD}" "${D}/${SAMPLE}"; do [ -f "$f" ] || { echo "missing $f" >&2; exit 1; }; done
[ -d "${D}/${SAMPLE_DOCS}" ] || { echo "missing ${D}/${SAMPLE_DOCS}" >&2; exit 1; }

ROOT=$(git rev-parse --show-toplevel)
cd "${ROOT}"

# An uncommitted tree is allowed (experiments), but the exact diff is kept beside the log.
GIT=$(git rev-parse HEAD)
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  DIFF_SHA=$(git diff HEAD | sha256sum | cut -c1-12)
  GIT="${GIT}-dirty-${DIFF_SHA}"
fi
RUN="${D}/${CORPUS}-hashed-runs/$(date +%Y%m%d-%H%M%S)-${GIT:0:12}"
BUILT="${RUN}/built"
mkdir -p "${RUN}"
git diff HEAD > "${RUN}/uncommitted.diff"
LOG="${RUN}/train.log"
echo "corpus=${CORPUS} gold=${GOLD} sample=${SAMPLE} git=${GIT} run=${RUN}" | tee "${LOG}"

REPO="${MAVEN_REPO_LOCAL:-${ROOT}/.local_m2_repo}"
./mvnw clean install -pl tika-ml/tika-ml-junkdetect-tools -am -Pfast,train \
  -Dmaven.repo.local="${REPO}" >> "${LOG}" 2>&1
JAR=$(ls tika-ml/tika-ml-junkdetect-tools/target/tika-ml-junkdetect-tools-*-tools.jar)
OUT="${RUN}/junkdetect-hashed.bin"
JOPTS=${JAVA_OPTS:--Xmx22g}

# shellcheck disable=SC2086
java ${JOPTS} -cp "${JAR}" org.apache.tika.ml.junkdetect.tools.BuildJunkTrainingData \
  --data-dir "${D}/${CORPUS}" --output-dir "${BUILT}" 2>&1 | tee -a "${LOG}"
# shellcheck disable=SC2086
java ${JOPTS} -cp "${JAR}" org.apache.tika.ml.junkdetect.tools.TrainHashedJunkModel \
  --corpus "${D}/${CORPUS}" --built "${BUILT}" --gold "${D}/${GOLD}" \
  --sample "${D}/${SAMPLE}" --sample-docs "${D}/${SAMPLE_DOCS}" \
  --output "${OUT}" --git-commit "${GIT}" 2>&1 | tee -a "${LOG}"
echo "model: ${OUT}  provenance: ${OUT}.provenance  log: ${LOG}" | tee -a "${LOG}"
