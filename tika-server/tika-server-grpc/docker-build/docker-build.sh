#!/bin/bash
# This script is intended to be run from Maven exec plugin during the package phase of maven build

if [ -z "${TIKA_PIPES_VERSION}" ]; then
    echo "Environment variable TIKA_PIPES_VERSION is required, and should match the maven project version of Tika Pipes"
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
PROJECT_NAME=${PROJECT_NAME:-tika-pipes}

# If RELEASE_IMAGE_TAG not specified, use TIKA_PIPES_VERSION
if [[ -z "${RELEASE_IMAGE_TAG}" ]]; then
    RELEASE_IMAGE_TAG="${TIKA_PIPES_VERSION}"
    ## Remove '-SNAPSHOT' from the version string
    RELEASE_IMAGE_TAG="${RELEASE_IMAGE_TAG//-SNAPSHOT/}"
fi

mkdir -p "${OUT_DIR}/libs"
mkdir -p "${OUT_DIR}/plugins"
mkdir -p "${OUT_DIR}/config"
mkdir -p "${OUT_DIR}/bin"
cp -v -r "tika-pipes-grpc/target/tika-pipes-grpc-${TIKA_PIPES_VERSION}.jar" "${OUT_DIR}/libs"

# Loop through tika-pipes-fetchers child directories and copy the plugin zip files
for dir in tika-pipes-fetchers/*/; do
    fetcher_name=$(basename "$dir")
    zip_file="${dir}target/${fetcher_name}-${TIKA_PIPES_VERSION}.zip"
    if [ -f "$zip_file" ]; then
        cp -v -r "$zip_file" "${OUT_DIR}/plugins"
    else
        echo "Fetcher file $zip_file does not exist, skipping."
    fi
done

# Loop through tika-pipes-fetchers child directories and copy the plugin zip files
for dir in tika-pipes-emitters/*/; do
    emitter_name=$(basename "$dir")
    zip_file="${dir}target/${emitter_name}-${TIKA_PIPES_VERSION}.zip"
    if [ -f "$zip_file" ]; then
        cp -v -r "$zip_file" "${OUT_DIR}/plugins"
    else
        echo "Emitter file $zip_file does not exist, skipping."
    fi
done

# Loop through tika-pipes-pipe-iterators child directories and copy the plugin zip files
for dir in tika-pipes-pipe-iterators/*/; do
    pipe_iterator_name=$(basename "$dir")
    zip_file="${dir}target/${pipe_iterator_name}-${TIKA_PIPES_VERSION}.zip"
    if [ -f "$zip_file" ]; then
        cp -v -r "$zip_file" "${OUT_DIR}/plugins"
    else
        echo "Pipe iterator file $zip_file does not exist, skipping."
    fi
done

cp -v -r "tika-pipes-cli/src/main/resources/log4j2.xml" "${OUT_DIR}/config"
cp -v -r "tika-pipes-grpc/src/main/resources/application.yaml" "${OUT_DIR}/config"
cp -v -r "tika-pipes-grpc/docker-build/start-tika-grpc.sh" "${OUT_DIR}/bin"

cp -v "tika-pipes-grpc/docker-build/Dockerfile" "${OUT_DIR}/Dockerfile"

cd "${OUT_DIR}" || exit

echo "Running docker build from directory: $(pwd)"

IMAGE_TAGS=()
if [[ -n "${AWS_ACCOUNT_ID}" ]]; then
    aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com
    IMAGE_TAGS+=("-t ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${PROJECT_NAME}:${RELEASE_IMAGE_TAG}")
fi

if [[ -n "${AZURE_REGISTRY_NAME}" ]]; then
    az acr login --name ${AZURE_REGISTRY_NAME}
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
if [ "${MULTI_ARCH}" == "true" ]; then
  echo "Building multi arch image"
  docker buildx create --name tikapipesbuilder
  # see https://askubuntu.com/questions/1339558/cant-build-dockerfile-for-arm64-due-to-libc-bin-segmentation-fault/1398147#1398147
  docker run --rm --privileged tonistiigi/binfmt --install amd64
  docker run --rm --privileged tonistiigi/binfmt --install arm64
  docker buildx build \
      --builder=tikapipesbuilder . \
      ${tag} \
      --platform linux/amd64,linux/arm64 \
      --push
  docker buildx stop tikapipesbuilder
else
  echo "Building single arch image"
  # build single arch
  docker build . ${tag}
fi

echo " ==================================================================================================="
echo " Done running docker build with tag ${tag}"
echo " ==================================================================================================="
