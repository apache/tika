#!/bin/bash

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

# This script assembles the Docker build context for tika-grpc and builds the image.
# It is intended to be run from the root of the tika repository after a Maven build.

set -euo pipefail

if [ -z "${TIKA_VERSION:-}" ]; then
    echo "Environment variable TIKA_VERSION is required, and should match the maven project version of Tika"
    exit 1
fi

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
REPO_ROOT="${SCRIPT_DIR}/../../"

cd "${REPO_ROOT}" || exit

OUT_DIR=target/tika-grpc-docker

MULTI_ARCH=${MULTI_ARCH:-false}
DOCKER_ID=${DOCKER_ID:-}
PROJECT_NAME=${PROJECT_NAME:-tika-grpc}

# If RELEASE_IMAGE_TAG not specified, use TIKA_VERSION
if [[ -z "${RELEASE_IMAGE_TAG:-}" ]]; then
    RELEASE_IMAGE_TAG="${TIKA_VERSION}"
    # Remove '-SNAPSHOT' from the version string
    RELEASE_IMAGE_TAG="${RELEASE_IMAGE_TAG//-SNAPSHOT/}"
fi

mkdir -p "${OUT_DIR}/libs"
mkdir -p "${OUT_DIR}/plugins"
mkdir -p "${OUT_DIR}/config"
mkdir -p "${OUT_DIR}/bin"
cp -v -r "tika-grpc/target/tika-grpc-${TIKA_VERSION}.jar" "${OUT_DIR}/libs"

# Copy all tika-pipes plugin zip files
for dir in tika-pipes/tika-pipes-plugins/*/; do
    plugin_name=$(basename "$dir")
    zip_file="${dir}target/${plugin_name}-${TIKA_VERSION}.zip"
    if [ -f "$zip_file" ]; then
        cp -v -r "$zip_file" "${OUT_DIR}/plugins"
    else
        echo "WARNING: Plugin file $zip_file does not exist, skipping."
    fi
done

# Copy parser package jars as plugins
parser_packages=(
    "tika-parsers/tika-parsers-standard/tika-parsers-standard-package"
    "tika-parsers/tika-parsers-extended/tika-parser-scientific-package"
    "tika-parsers/tika-parsers-extended/tika-parser-sqlite3-package"
    "tika-parsers/tika-parsers-ml/tika-parser-nlp-package"
)

for parser_package in "${parser_packages[@]}"; do
    package_name=$(basename "$parser_package")
    jar_file="${parser_package}/target/${package_name}-${TIKA_VERSION}.jar"
    if [ -f "$jar_file" ]; then
        cp -v -r "$jar_file" "${OUT_DIR}/plugins"
    else
        echo "Parser package file $jar_file does not exist, skipping."
    fi
done

cp -v -r "tika-grpc/docker-build/start-tika-grpc.sh" "${OUT_DIR}/bin"
cp -v "tika-grpc/docker-build/default-tika-config.json" "${OUT_DIR}/config"
cp -v "tika-grpc/docker-build/Dockerfile" "${OUT_DIR}/Dockerfile"

cd "${OUT_DIR}" || exit

echo "Running docker build from directory: $(pwd)"

IMAGE_TAGS=()
if [[ -n "${DOCKER_ID}" ]]; then
    IMAGE_TAGS+=("-t" "${DOCKER_ID}/${PROJECT_NAME}:${RELEASE_IMAGE_TAG}")
fi

if [ ${#IMAGE_TAGS[@]} -eq 0 ]; then
    echo "No image tags specified. Set DOCKER_ID environment variable to enable Docker build."
    exit 0
fi

if [ "${MULTI_ARCH}" == "true" ]; then
    echo "Building multi-arch image"
    docker buildx create --name tikabuilder --use || true
    docker buildx build \
        --builder=tikabuilder . \
        "${IMAGE_TAGS[@]}" \
        --build-arg VERSION="${TIKA_VERSION}" \
        --platform linux/amd64,linux/arm64 \
        --push
    docker buildx stop tikabuilder
    docker buildx rm tikabuilder
else
    echo "Building single-arch image"
    docker build . "${IMAGE_TAGS[@]}" --build-arg VERSION="${TIKA_VERSION}"
fi

echo "==================================================================================================="
echo "Done running docker build with tags: ${IMAGE_TAGS[*]}"
echo "==================================================================================================="
