TAG_NAME=$1

if [ -z "${TAG_NAME}" ]; then
    echo "Single command line argument is required which will be used as the -t parameter of the docker build command"
    exit 1
fi

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
TIKA_SRC_PATH=${SCRIPT_DIR}/../../..
OUT_DIR=${TIKA_SRC_PATH}/tika-pipes/tika-grpc/target/tika-docker

mvn clean install -Dossindex.skip -DskipTests=true -f "${TIKA_SRC_PATH}" || exit
mvn dependency:copy-dependencies -f "${TIKA_SRC_PATH}/tika-pipes/tika-grpc" || exit
rm -rf "${OUT_DIR}"
mkdir -p "${OUT_DIR}"

cp -r "${TIKA_SRC_PATH}/tika-pipes/tika-grpc/target/dependency" "${OUT_DIR}/libs"
cp -r "${TIKA_SRC_PATH}/tika-pipes/tika-fetchers/tika-fetcher-gcs/target/tika-fetcher-gcs-"*".jar" "${OUT_DIR}/libs"
cp -r "${TIKA_SRC_PATH}/tika-pipes/tika-fetchers/tika-fetcher-az-blob/target/tika-fetcher-az-blob-"*".jar" "${OUT_DIR}/libs"
cp -r "${TIKA_SRC_PATH}/tika-pipes/tika-fetchers/tika-fetcher-http/target/tika-fetcher-http-"*".jar" "${OUT_DIR}/libs"
cp -r "${TIKA_SRC_PATH}/tika-pipes/tika-fetchers/tika-fetcher-microsoft-graph/target/tika-fetcher-microsoft-graph-"*".jar" "${OUT_DIR}/libs"
cp -r "${TIKA_SRC_PATH}/tika-pipes/tika-fetchers/tika-fetcher-s3/target/tika-fetcher-s3-"*".jar" "${OUT_DIR}/libs"

cp "${TIKA_SRC_PATH}/tika-pipes/tika-grpc/target/tika-grpc-"*".jar" "${OUT_DIR}/libs"
cp "${TIKA_SRC_PATH}/tika-pipes/tika-grpc/src/test/resources/log4j2.xml" "${OUT_DIR}"
cp "${TIKA_SRC_PATH}/tika-pipes/tika-grpc/src/test/resources/tika-pipes-test-config.xml" "${OUT_DIR}/tika-config.xml"
cp "${TIKA_SRC_PATH}/tika-pipes/tika-grpc/example-dockerfile/Dockerfile" "${OUT_DIR}/Dockerfile"

cd "${OUT_DIR}" || exit

# build single arch
#docker build "${OUT_DIR}" -t "${TAG_NAME}"

# Or we can build multi-arch - https://www.docker.com/blog/multi-arch-images/
docker buildx create --name tikabuilder
# see https://askubuntu.com/questions/1339558/cant-build-dockerfile-for-arm64-due-to-libc-bin-segmentation-fault/1398147#1398147
docker run --rm --privileged tonistiigi/binfmt --install amd64
docker run --rm --privileged tonistiigi/binfmt --install arm64
docker buildx build --builder=tikabuilder "${OUT_DIR}" -t "${TAG_NAME}" --platform linux/amd64,linux/arm64 --push
docker buildx stop tikabuilder
