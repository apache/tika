#!/usr/bin/env bash

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

image_name=apache/tika

stop_and_die() {
  docker buildx rm tika-builder || die "couldn't stop builder -- make sure to stop the builder manually! "
  die "$*"
}

die() {
  echo "$*" >&2
  exit 1
}

while getopts ":h" opt; do
  case ${opt} in
    h )
      echo "Usage:"
      echo "    docker-tool.sh -h                      Display this help message."
      echo "    docker-tool.sh build <TIKA_DOCKER_VERSION> <TIKA_VERSION>   Builds <TIKA_DOCKER_VERSION> images for <TIKA_VERSION>."
      echo "    docker-tool.sh test <TIKA_DOCKER_VERSION>     Tests images for <TIKA_DOCKER_VERSION>."
      echo "    docker-tool.sh publish <TIKA_DOCKER_VERSION> <TIKA_VERSION> Builds multi-arch images for <TIKA_DOCKER_VERSION> and pushes to Docker Hub."
      exit 0
      ;;
   \? )
     echo "Invalid Option: -$OPTARG" 1>&2
     exit 1
     ;;
  esac
done

stop_test_container() {
  container_name=$1
  docker kill "$container_name"
  docker rm "$container_name"
}

test_docker_image() {
  container_name=$1
  image=$image_name:$1
  full=$2

  docker run -d --name "$container_name" -p 127.0.0.1:9998:9998 "$image"
  sleep 10
  url=http://localhost:9998/
  status=$(curl --head --location --connect-timeout 5 --write-out %{http_code} --silent --output /dev/null ${url})
  user=$(docker inspect "$container_name" --format '{{.Config.User}}')

  if [[ $status == '200' ]]
  then
    echo "$(tput setaf 2)Image: $image - Basic test passed$(tput sgr0)"
  else
    echo "$(tput setaf 1)Image: $image - Basic test failed$(tput sgr0)"
    stop_test_container "$container_name"
    exit 1
  fi

  #now test that the user is correctly set
  if [[ $user == '35002:35002' ]]
  then
    echo "$(tput setaf 2)Image: $image - User passed$(tput sgr0)"
  else
    echo "$(tput setaf 1)Image: $image - User failed$(tput sgr0)"
    stop_test_container "$container_name"
    exit 1
  fi

  if [ $full == true ]
  then
    # Test ImageMagick is installed and runnable
    if docker exec "$1" /usr/bin/convert -version >/dev/null
    then
      echo "$(tput setaf 2)Image: $image - ImageMagick passed$(tput sgr0)"
    else
      echo "$(tput setaf 1)Image: $image - ImageMagick failed$(tput sgr0)"
      stop_test_container "$container_name"
      exit 1
    fi
  fi

  stop_test_container "$container_name"
}

shift $((OPTIND -1))
subcommand=$1; shift
tika_docker_version=$1; shift
tika_version=$1; shift


case "$subcommand" in
  build)
    # Build slim tika- with minimal dependencies
    docker build -t ${image_name}:${tika_docker_version} --build-arg TIKA_VERSION=${tika_version} - < minimal/Dockerfile --no-cache || die "couldn't build minimal"
    # Build full tika- with OCR, Fonts and GDAL
    docker build -t ${image_name}:${tika_docker_version}-full --build-arg TIKA_VERSION=${tika_version} - < full/Dockerfile --no-cache || die "couldn't build full"
    ;;

  test)
    # Test the images
    test_docker_image ${tika_docker_version} false
    test_docker_image "${tika_docker_version}-full" true
    ;;

  publish)
    docker buildx create --use --name tika-builder || die "couldn't create builder"
    # Build multi-arch with buildx and push
    docker buildx build --platform linux/arm/v7,linux/arm64/v8,linux/amd64,linux/s390x --output "type=image,push=true" \
      --tag ${image_name}:latest --tag ${image_name}:${tika_docker_version} --build-arg TIKA_VERSION=${tika_version} --no-cache --builder tika-builder minimal || stop_and_die "couldn't build multi-arch minimal"
    docker buildx build --platform linux/arm/v7,linux/arm64/v8,linux/amd64,linux/s390x --output "type=image,push=true" \
      --tag ${image_name}:latest-full --tag ${image_name}:${tika_docker_version}-full --build-arg TIKA_VERSION=${tika_version} --no-cache --builder tika-builder full || stop_and_die "couldn't build multi-arch full"
    docker buildx rm tika-builder || die "couldn't stop builder -- make sure to stop the builder manually! "
    ;;

esac
