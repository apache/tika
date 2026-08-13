#!/usr/bin/env bash

# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing,
#   software distributed under the License is distributed on an
#   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
#   KIND, either express or implied.  See the License for the
#   specific language governing permissions and limitations
#   under the License.

#
# Regenerates and validates the metadata key registry (tika-metadata-schema).
#
# Run this after adding, renaming, or removing a Property or KeyPrefix
# constant anywhere in tika-core or the standard parser bundle. It replaces the
# multi-step manual sequence in .skills/metadata-schema/SKILL.md with one command:
# install the dependency modules, regenerate the three registry files, sanity
# check the diff, then run the gate tests.
#
# Usage:
#   tika-metadata-schema/regen.sh [--skip-install] [--skip-tests]
#
#   --skip-install  skip the -am install step (only safe if no Property/
#                   KeyPrefix classes outside tika-metadata-schema
#                   changed since the last install)
#   --skip-tests    skip the final gate-test run, for a faster inner loop
#
# See tika-metadata-schema/README.md and .skills/metadata-schema/SKILL.md for the
# design and the traps this script exists to route around.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

MVN_REPO_OPT="-Dmaven.repo.local=$REPO_ROOT/.local_m2_repo"

SKIP_INSTALL=0
SKIP_TESTS=0
for arg in "$@"; do
    case "$arg" in
        --skip-install) SKIP_INSTALL=1 ;;
        --skip-tests) SKIP_TESTS=1 ;;
        -h|--help)
            sed -n '20,38p' "$0" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            echo "Unknown argument: $arg" >&2
            exit 1
            ;;
    esac
done

REGISTRY_DIR="tika-metadata-schema/src/main/resources/org/apache/tika/metadata"
REGISTRY_FILES=(
    "$REGISTRY_DIR/metadata-keys.json"
    "$REGISTRY_DIR/metadata-open-namespaces.json"
    "$REGISTRY_DIR/metadata-key-fields.json"
)

if [ "$SKIP_INSTALL" -eq 0 ]; then
    echo "==> Installing tika-metadata-schema + its dependency modules (tika-core, standard parsers)"
    echo "    so newly added Property/KeyPrefix classes are on the scan classpath."
    echo "    (skip with --skip-install if you already did this)"
    ./mvnw -Pfast -DskipTests -pl tika-metadata-schema -am install "$MVN_REPO_OPT"
fi

echo "==> Recording committed key counts, to catch an incomplete classpath scan later"
BEFORE_COUNTS=()
for f in "${REGISTRY_FILES[@]}"; do
    if git cat-file -e "HEAD:$f" 2>/dev/null; then
        BEFORE_COUNTS+=("$(git show "HEAD:$f" | wc -l)")
    else
        BEFORE_COUNTS+=("0")
    fi
done

echo "==> Regenerating the registry (forked exec — see .skills/metadata-schema/SKILL.md for why exec:java is unsafe)"
./mvnw -pl tika-metadata-schema -Pregen-metadata-schema process-classes "$MVN_REPO_OPT"

echo "==> Comparing key counts before/after (a large drop usually means classes failed to load):"
for i in "${!REGISTRY_FILES[@]}"; do
    f="${REGISTRY_FILES[$i]}"
    before="${BEFORE_COUNTS[$i]}"
    after=$(wc -l < "$f")
    line="    $f: $before -> $after lines"
    if [ "$before" -gt 0 ] && [ "$after" -lt $((before * 90 / 100)) ]; then
        echo "$line  *** WARNING: >10% drop, check --skip-install and module installs ***"
    else
        echo "$line"
    fi
done

echo "==> git diff of the registries (review before committing):"
git --no-pager diff --stat -- "${REGISTRY_FILES[@]}"

if [ "$SKIP_TESTS" -eq 0 ]; then
    echo "==> Running gate tests (MetadataSchemaTest, MetadataFieldTableTest, MetadataNoUnderscoreTest, MetadataCoverageTest, ...)"
    ./mvnw -pl tika-metadata-schema test "$MVN_REPO_OPT"
fi

echo "==> Done. Review the diff above, then commit the Property/KeyPrefix change and the regenerated JSON together."
