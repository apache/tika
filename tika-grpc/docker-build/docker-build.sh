#!/bin/bash
# This script is intended to be run from Maven exec plugin during the package phase of maven build
set -e

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo "ERROR: Docker is not installed or not in PATH. Please install Docker first."
    exit 1
fi

if [ -z "${TIKA_VERSION}" ]; then
    echo "Environment variable TIKA_VERSION is required, and should match the maven project version of Tika"
    exit 1
fi

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

cd "${SCRIPT_DIR}/../../" || exit

OUT_DIR=target/tika-docker

MULTI_ARCH=${MULTI_ARCH:-false}
AWS_REGION=${AWS_REGION:-us-west-2}
AWS_ACCOUNT_ID=${AWS_ACCOUNT_ID:-}
AZURE_REGISTRY_NAME=${AZURE_REGISTRY_NAME:-}
DOCKER_ID=${DOCKER_ID:-}
PROJECT_NAME=${PROJECT_NAME:-tika-grpc}

# If RELEASE_IMAGE_TAG not specified, use TIKA_VERSION
if [[ -z "${RELEASE_IMAGE_TAG}" ]]; then
    RELEASE_IMAGE_TAG="${TIKA_VERSION}"
    ## Remove '-SNAPSHOT' from the version string
    RELEASE_IMAGE_TAG="${RELEASE_IMAGE_TAG//-SNAPSHOT/}"
fi

mkdir -p "${OUT_DIR}/libs"
mkdir -p "${OUT_DIR}/plugins"
mkdir -p "${OUT_DIR}/config"
mkdir -p "${OUT_DIR}/bin"
cp -v "tika-grpc/target/tika-grpc-${TIKA_VERSION}.jar" "${OUT_DIR}/libs"

# Copy all tika-pipes plugin zip files
for dir in tika-pipes/tika-pipes-plugins/*/; do
    plugin_name=$(basename "$dir")
    zip_file="${dir}target/${plugin_name}-${TIKA_VERSION}.zip"
    if [ -f "$zip_file" ]; then
        cp -v "$zip_file" "${OUT_DIR}/plugins"
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
        cp -v "$jar_file" "${OUT_DIR}/plugins"
    else
        echo "Parser package file $jar_file does not exist, skipping."
    fi
done

cp -v "tika-grpc/docker-build/start-tika-grpc.sh" "${OUT_DIR}/bin"

cp -v "tika-grpc/docker-build/Dockerfile" "${OUT_DIR}/Dockerfile"

cd "${OUT_DIR}" || exit

echo "Running docker build from directory: $(pwd)"

IMAGE_TAGS=()
if [[ -n "${AWS_ACCOUNT_ID}" ]]; then
    if ! aws ecr get-login-password --region "${AWS_REGION}" | docker login --username AWS --password-stdin "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"; then
        echo "ERROR: Failed to authenticate with AWS ECR"
        exit 1
    fi
    IMAGE_TAGS+=("-t ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${PROJECT_NAME}:${RELEASE_IMAGE_TAG}")
fi

if [[ -n "${AZURE_REGISTRY_NAME}" ]]; then
    if ! az acr login --name "${AZURE_REGISTRY_NAME}"; then
        echo "ERROR: Failed to authenticate with Azure Container Registry"
        exit 1
    fi
    IMAGE_TAGS+=("-t ${AZURE_REGISTRY_NAME}.azurecr.io/${PROJECT_NAME}:${RELEASE_IMAGE_TAG}")
fi

if [[ -n "${DOCKER_ID}" ]]; then
    IMAGE_TAGS+=("-t ${DOCKER_ID}/${PROJECT_NAME}:${RELEASE_IMAGE_TAG}")
fi

if [ ${#IMAGE_TAGS[@]} -eq 0 ]; then
    echo "No image tags specified, skipping Docker build step. To enable build, set AWS_ACCOUNT_ID, AZURE_REGISTRY_NAME, and/or DOCKER_ID environment variables."
    exit 0
fi

tag="${IMAGE_TAGS[*]}"
BUILDX_BUILDER_NAME="tikabuilder"
BUILDX_CREATED_BY_SCRIPT=false

if [ "${MULTI_ARCH}" == "true" ]; then
  echo "Building multi arch image"
  if docker buildx inspect "${BUILDX_BUILDER_NAME}" >/dev/null 2>&1; then
    echo "Using existing Docker buildx builder '${BUILDX_BUILDER_NAME}'"
  else
    echo "Creating Docker buildx builder '${BUILDX_BUILDER_NAME}'"
    docker buildx create --name "${BUILDX_BUILDER_NAME}"
    BUILDX_CREATED_BY_SCRIPT=true
  fi
  # Pin binfmt to a specific digest for security (digest verified 2025-12-17)
  # see https://askubuntu.com/questions/1339558/cant-build-dockerfile-for-arm64-due-to-libc-bin-segmentation-fault/1398147#1398147
  docker run --rm --privileged tonistiigi/binfmt:latest@sha256:8de6f2decb92e9001d094534bf8a92880c175bd5dfb4a9d8579f26f09821cfa2 --install amd64
  docker run --rm --privileged tonistiigi/binfmt:latest@sha256:8de6f2decb92e9001d094534bf8a92880c175bd5dfb4a9d8579f26f09821cfa2 --install arm64
  if ! docker buildx build \
      --builder="${BUILDX_BUILDER_NAME}" . \
      --build-arg VERSION="${TIKA_VERSION}" \
      ${tag} \
      --platform linux/amd64,linux/arm64 \
      --push; then
    echo "ERROR: Docker multi-arch build or push failed"
    if [ "${BUILDX_CREATED_BY_SCRIPT}" = "true" ]; then
      docker buildx stop "${BUILDX_BUILDER_NAME}" >/dev/null 2>&1 || true
      docker buildx rm "${BUILDX_BUILDER_NAME}" >/dev/null 2>&1 || true
    fi
    exit 1
  fi
  if [ "${BUILDX_CREATED_BY_SCRIPT}" = "true" ]; then
    docker buildx stop "${BUILDX_BUILDER_NAME}"
    docker buildx rm "${BUILDX_BUILDER_NAME}"
  fi
else
  echo "Building single arch image"
  if ! docker build . --build-arg VERSION="${TIKA_VERSION}" ${tag}; then
    echo "ERROR: Docker single-arch build failed"
    exit 1
  fi
fi

echo " ==================================================================================================="
echo " Done running docker build with tag ${tag}"
echo " ==================================================================================================="
