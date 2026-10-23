#!/bin/bash
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
# Scaffold a new STABLE version's apt docs by copying the previous stable
# version's directory and bumping the version string. Safe because the
# per-version apt docs (configuring/detection/examples/parser/parser_guide/
# gettingstarted) are identical across 3.x releases apart from the version
# string. formats.apt and index.apt are intentionally NOT copied: they must be
# regenerated (formats from `tika-app --list-parser-details-apt`, index from
# the extract-tika-* scripts + hand-editing).
#
# PREVIEW/beta releases do NOT use this -- they get only a hand-written
# index.apt. See SKILL.md.
#
# Usage:
#   scaffold-stable-version.sh <tika-site-root> <prev-version> <new-version>
# Example:
#   scaffold-stable-version.sh /path/to/tika-site 3.3.1 3.3.2
set -euo pipefail

SITE_ROOT="${1:?usage: scaffold-stable-version.sh <tika-site-root> <prev-version> <new-version>}"
PREV="${2:?missing <prev-version>}"
NEW="${3:?missing <new-version>}"

APT="${SITE_ROOT}/src/site/apt"
SRC="${APT}/${PREV}"
DST="${APT}/${NEW}"

[[ -d "${SRC}" ]] || { echo "Previous version dir not found: ${SRC}" >&2; exit 1; }
[[ -e "${DST}" ]] && { echo "Target already exists: ${DST} (refusing to overwrite)" >&2; exit 1; }

# Files that are pure version-string bumps of the previous release.
COPY_BUMP=(configuring.apt detection.apt examples.apt parser.apt parser_guide.apt gettingstarted.apt)

mkdir -p "${DST}"
for f in "${COPY_BUMP[@]}"; do
    if [[ -f "${SRC}/${f}" ]]; then
        sed "s/${PREV//./\\.}/${NEW}/g" "${SRC}/${f}" > "${DST}/${f}"
        echo "  bumped  ${f}"
    else
        echo "  WARN: ${SRC}/${f} missing, skipped" >&2
    fi
done

echo ""
echo "Scaffolded ${DST} from ${PREV}."
echo "STILL TO DO (not done by this script):"
echo "  * formats.apt  -- regenerate: java -jar tika-app-${NEW}.jar --list-parser-details-apt"
echo "                   (prepend the same header block as ${SRC}/formats.apt, then the list)"
echo "  * index.apt    -- build from extract-tika-issues.py + extract-tika-contribs.py, hand-edit"
echo "  * git/svn add  -- 'svn add ${DST}' once the two files above exist"
echo "  * Verify each bumped file's version references look right (diff against ${SRC})."
