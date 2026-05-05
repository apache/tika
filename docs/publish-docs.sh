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
# Prerequisite: run 'mvn package -pl docs' first to populate target/site/.

set -euo pipefail
cd "$(dirname "$0")"

PUBLISH_DIR="${1:?usage: publish-docs.sh <tika-site-publish-dir>}"
DOCS_DIR="${PUBLISH_DIR}/docs"

if [[ ! -d target/site ]]; then
    echo "target/site/ not found." >&2
    echo "Build the docs first: cd .. && ./mvnw package -pl docs" >&2
    exit 1
fi

mkdir -p "${DOCS_DIR}"

# Strip the 'tika/' component dir prefix so URLs are /docs/X.Y.Z/...
cp -r target/site/tika/* "${DOCS_DIR}/"
# UI assets one level above docs/, since HTML uses ../../_/ relative paths
cp -r target/site/_/ "${PUBLISH_DIR}/_/"
# Fix the root redirect and sitemap to match the flattened layout
sed 's|tika/||g' target/site/index.html > "${DOCS_DIR}/index.html"
sed 's|/docs/tika/|/docs/|g' target/site/sitemap.xml > "${DOCS_DIR}/sitemap.xml"
cp target/site/404.html "${DOCS_DIR}/"
cp target/site/search-index.js "${DOCS_DIR}/"

echo "Published to: ${DOCS_DIR}/"
