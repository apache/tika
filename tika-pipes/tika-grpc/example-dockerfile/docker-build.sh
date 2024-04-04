TAG_NAME=$1

if [ -z "${TAG_NAME}" ]; then
    echo "Tag name is required"
    exit 1
fi

SCRIPT_DIR=$( cd -- $( dirname -- ${BASH_SOURCE[0]} ) &> /dev/null && pwd )
TIKA_SRC_PATH=${SCRIPT_DIR}/../../..
DEST_DIR=${TIKA_SRC_PATH}/tika-pipes/tika-grpc/target/tika-docker

mvn clean install -DskipTests=true -f ${TIKA_SRC_PATH}
mvn dependency:copy-dependencies -f ${TIKA_SRC_PATH}/tika-pipes/tika-grpc
rm -rf ${TIKA_SRC_PATH}/tika-pipes/tika-grpc/target/docker
mkdir -p ${DEST_DIR}

cp -r ${TIKA_SRC_PATH}/tika-pipes/tika-grpc/target/dependency ${DEST_DIR}/docker
cp ${TIKA_SRC_PATH}/tika-pipes/tika-grpc/target/tika-grpc-*.jar ${DEST_DIR}
cp ${TIKA_SRC_PATH}/tika-pipes/tika-grpc/src/test/resources/log4j2.xml ${DEST_DIR}
cp ${TIKA_SRC_PATH}/tika-pipes/tika-grpc/src/test/resources/tika-pipes-test-config.xml ${DEST_DIR}/tika-config.xml
cp ${TIKA_SRC_PATH}/tika-pipes/tika-grpc/example-dockerfile/Dockerfile ${DEST_DIR}/Dockerfile

cd ${TIKA_SRC_PATH}/tika-pipes/tika-grpc/target
docker build ${DEST_DIR} -t ${TAG_NAME}
