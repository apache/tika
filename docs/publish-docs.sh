#!/bin/bash
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
# Copies the already-built Antora site into a tika-site SVN checkout,
# flattening URLs from /docs/tika/X.Y.Z/... to /docs/X.Y.Z/... so they
# match the website layout.
#
# Usage:
#   ./publish-docs.sh /path/to/tika-site/publish
#
# Prerequisite: build target/site/ first, from the repo root:
#   ./mvnw package -Papache-release -pl :tika-docs -DskipTests
# (The 'docs' module is only in the reactor under the apache-release profile.)

set -euo pipefail
cd "$(dirname "$0")"

PUBLISH_DIR="${1:?usage: publish-docs.sh <tika-site-publish-dir>}"

# Guard the 'rm -rf' below: the publish dir must already exist (it's a
# tika-site checkout, not something we create) and not be a dangerously
# short/root path that a typo could expand to.
if [[ ! -d "${PUBLISH_DIR}" ]]; then
    echo "PUBLISH_DIR '${PUBLISH_DIR}' is not an existing directory." >&2
    echo "Point it at a tika-site 'publish/' checkout." >&2
    exit 1
fi
PUBLISH_DIR="$(cd "${PUBLISH_DIR}" && pwd -P)"
if [[ "${#PUBLISH_DIR}" -lt 4 || "${PUBLISH_DIR}" != *"/"* ]]; then
    echo "Refusing to operate on suspiciously short PUBLISH_DIR '${PUBLISH_DIR}'." >&2
    exit 1
fi
# Confirm this looks like a tika-site 'publish/' dir: the documented argument
# is always <tika-site-checkout>/publish, and the downstream 'svn add' step
# hardcodes that name for the things written here (publish/docs, publish/_,
# publish/search-index.js). Refusing a non-'publish' basename catches a
# wrong-but-valid checkout before we 'rm -rf' inside it.
if [[ "$(basename "${PUBLISH_DIR}")" != "publish" ]]; then
    echo "PUBLISH_DIR '${PUBLISH_DIR}' does not look like a tika-site publish dir" >&2
    echo "(expected its name to be 'publish'). Refusing to modify it." >&2
    exit 1
fi

DOCS_DIR="${PUBLISH_DIR}/docs"

if [[ ! -d target/site ]]; then
    echo "target/site/ not found." >&2
    echo "Build the docs first: cd .. && ./mvnw package -Papache-release -pl :tika-docs -DskipTests" >&2
    exit 1
fi

# Run sed and replace $output atomically. The plain 'sed IN > OUT' form
# truncates OUT before sed runs, so a missing input or sed failure leaves an
# empty file behind; this writes to OUT.tmp first and only renames on success.
# Important for PUBLISH_DIR/search-index.js, which persists across runs (a
# corrupted one would stay corrupted until the next successful publish).
sed_atomic() {
    local script="$1" input="$2" output="$3"
    if [[ ! -f "${input}" ]]; then
        echo "${input} not found." >&2
        echo "Re-run the docs build: cd .. && ./mvnw package -Papache-release -pl :tika-docs -DskipTests" >&2
        exit 1
    fi
    sed "${script}" "${input}" > "${output}.tmp"
    mv "${output}.tmp" "${output}"
}

mkdir -p "${DOCS_DIR}"

# Strip the 'tika/' component dir prefix so URLs are /docs/X.Y.Z/...
cp -r target/site/tika/* "${DOCS_DIR}/"
# UI assets one level above docs/, since HTML uses ../../_/ relative paths.
# Replace wholesale: cp -r into an existing directory nests source as a
# subdirectory (publish/_/_/), so remove first to keep the layout flat.
# Refuse if '_' is a symlink: 'rm -rf _/' would follow it and wipe the
# target's contents, and the cp below needs a real directory here anyway.
if [[ -L "${PUBLISH_DIR}/_" ]]; then
    echo "Refusing to remove '${PUBLISH_DIR}/_': it is a symlink, not a directory." >&2
    exit 1
fi
rm -rf "${PUBLISH_DIR}/_"
cp -r target/site/_ "${PUBLISH_DIR}/_"
# Fix the root redirect and sitemap to match the flattened layout
sed_atomic 's|tika/||g' target/site/index.html "${DOCS_DIR}/index.html"
sed_atomic 's|/docs/tika/|/docs/|g' target/site/sitemap.xml "${DOCS_DIR}/sitemap.xml"
cp target/site/404.html "${DOCS_DIR}/"
# Lunr index lives next to _/ (one level above docs/), since HTML uses ../../search-index.js.
# Remove the stale copy from its old publish/docs/ location left by earlier runs.
rm -f "${DOCS_DIR}/search-index.js"
# Rewrite URLs in the search index from /tika/X.Y.Z/... (Antora's component-
# prefixed publish path) to /docs/X.Y.Z/... (the deployed layout). The HTML
# pages and sitemap.xml above are similarly flattened; without this rewrite,
# clicking a search result lands on https://tika.apache.org/tika/... which
# 404s. See TIKA-4743.
sed_atomic 's|"url":"/tika/|"url":"/docs/|g' target/site/search-index.js "${PUBLISH_DIR}/search-index.js"

echo "Published to: ${DOCS_DIR}/"
