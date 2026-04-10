#!/bin/bash
# Builds the Antora docs site with the current git commit stamped on the home page.
# Usage: ./build-docs.sh
# Output: target/site/
#
# To publish to the tika-site SVN repo:
#   ./build-docs.sh --publish /path/to/tika-site/publish

set -euo pipefail
cd "$(dirname "$0")"

COMMIT=$(git rev-parse --short HEAD)
DATE=$(date -u +%Y-%m-%d)

# Inject commit into playbook, build, restore
sed -i "/tika-stable-version/a\\    git-commit: '${COMMIT} (${DATE})'" antora-playbook.yml
trap 'git checkout antora-playbook.yml' EXIT

# Pass remaining args to Maven (filter out our --publish flag)
PUBLISH_DIR=""
MVN_ARGS=()
while [[ $# -gt 0 ]]; do
    case $1 in
        --publish)
            PUBLISH_DIR="$2"
            shift 2
            ;;
        *)
            MVN_ARGS+=("$1")
            shift
            ;;
    esac
done

../mvnw antora:antora "${MVN_ARGS[@]}"

echo "Site built at: target/site/"
echo "Commit: ${COMMIT} (${DATE})"

if [[ -n "${PUBLISH_DIR}" ]]; then
    # Flatten: skip the 'tika/' component directory so URLs are /docs/4.0.0-SNAPSHOT/
    # Copy UI assets one level above docs/ since HTML uses ../../_/ relative paths
    DOCS_DIR="${PUBLISH_DIR}/docs"
    mkdir -p "${DOCS_DIR}"
    cp -r target/site/tika/* "${DOCS_DIR}/"
    cp -r target/site/_/ "${PUBLISH_DIR}/_/"
    # Fix the root redirect to match flattened layout
    sed 's|tika/||g' target/site/index.html > "${DOCS_DIR}/index.html"
    sed 's|/docs/tika/|/docs/|g' target/site/sitemap.xml > "${DOCS_DIR}/sitemap.xml"
    cp target/site/404.html "${DOCS_DIR}/"
    cp target/site/search-index.js "${DOCS_DIR}/"
    echo "Published to: ${DOCS_DIR}/"
fi
