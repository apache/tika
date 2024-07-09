# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.
# TAG_NAME=$1

if [ -z "${TAG_NAME}" ]; then
    echo "Single command line argument is required which will be used as the -t parameter of the docker build command"
    exit 1
fi

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
TIKA_SRC_PATH=${SCRIPT_DIR}/../../..
OUT_DIR=${TIKA_SRC_PATH}/tika-pipes/tika-grpc/target/tika-docker

mvn clean install -DskipTests=true -f "${TIKA_SRC_PATH}"
mvn dependency:copy-dependencies -f "${TIKA_SRC_PATH}/tika-pipes/tika-grpc"
rm -rf "${OUT_DIR}"
mkdir -p "${OUT_DIR}"

cp -r "${TIKA_SRC_PATH}/tika-pipes/tika-grpc/target/dependency" "${OUT_DIR}/libs"
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
