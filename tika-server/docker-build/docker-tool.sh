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
      echo "    docker-tool.sh test-uat <TIKA_DOCKER_VERSION> Runs the tika-server REST UAT against images for <TIKA_DOCKER_VERSION>."
      echo "    docker-tool.sh test-uat-snapshot Builds the minimal and full images from the local tika-server-standard zip"
      echo "                                    with the Dockerfile.snapshot files CI publishes from, and runs the REST UAT"
      echo "                                    (both passes) against them. No download, no signature: the artifact under test"
      echo "                                    is what this checkout built."
      echo "                                                  Requires TIKA_MAIN env var or sibling tika-main checkout (../tika-main)."
      echo "    docker-tool.sh publish <TIKA_VERSION> <BUILD_NUMBER>      Builds multi-arch images and pushes three tags per image:"
      echo "                                                  <TIKA_VERSION> (mutable), <TIKA_VERSION>-<BUILD_NUMBER> (immutable),"
      echo "                                                  and latest (for non-prerelease tags only)."
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

wait_for_version() {
  # Wait up to 30s for /version to respond.
  for i in $(seq 1 30); do
    if curl -fsS --max-time 2 http://localhost:9998/version >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

report_uat() {
  # report_uat <image> <container> <exit code of the UAT>
  if [[ "$3" == "0" ]]; then
    echo "$(tput setaf 2)Image: $1 - UAT passed$(tput sgr0)"
    stop_test_container "$2"
  else
    echo "$(tput setaf 1)Image: $1 - UAT failed$(tput sgr0)"
    echo "--- last 40 lines of container log ---"
    docker logs --tail 40 "$2" || true
    echo "--- end log ---"
    stop_test_container "$2"
    exit 1
  fi
}

test_docker_image_uat() {
  container_name=$1
  image=$image_name:$1
  uat_script=$2
  require_ocr=${3:-}   # non-empty => OCR must work (the -full image ships tesseract)

  docker run -d --name "$container_name" -p 127.0.0.1:9998:9998 "$image" \
    || die "couldn't start $image"
  wait_for_version
  TIKA_UAT_REQUIRE_OCR="$require_ocr" "$uat_script" http://localhost:9998
  report_uat "$image" "$container_name" $?
}

# Whether the image's HTTP client retries a 429 (TIKA-4912): the images carry no unzip, so the
# jar is copied out of a stopped container and inspected on the host.
image_client_retries() {
  local probe tmp rc=1
  probe="$(printf "%s" "$1" | tr "/:" "__")-probe"   # a container name cannot hold / or :
  tmp="$(mktemp -d /tmp/tika-uat-probe.XXXXXX)"
  docker create --name "$probe" "$1" >/dev/null 2>&1 || { rm -rf "$tmp"; return 1; }
  if docker cp "$probe:/opt/tika-server/lib/." "$tmp/" >/dev/null 2>&1; then
    local jar
    jar=$(ls "$tmp"/tika-http-jdk-*.jar 2>/dev/null | head -1)
    if [[ -n "$jar" ]] && unzip -p "$jar" org/apache/tika/http/TikaHttpClient.class 2>/dev/null \
         | grep -q DEFAULT_MAX_RETRIES; then
      rc=0
    fi
  fi
  docker rm -f "$probe" >/dev/null 2>&1
  rm -rf "$tmp"
  return $rc
}

# Second pass with an inference config: a mock engine (release-tools/uat/MockInferenceServer.java)
# on the host stands in for a hosted OCR model and an embedding model, started --flaky so every
# other request is refused first; the container reaches it as host.docker.internal. Proves the
# forked worker's engine wiring, ${env:...} interpolation into the forks, the client's retry, and
# ffmpeg where the image ships it (a hard failure on -full, a skip on minimal).
test_docker_image_uat_inference() {
  container_name="$1-inference"
  image=$image_name:$1
  uat_script=$2
  require_media=${3:-}
  uat_dir="$(dirname "$uat_script")"
  mock_port=18080

  # The mock refuses every other request only when the image's HTTP client retries a 429
  # (TIKA-4912): detected from the class in the image's tika-http-jdk jar, so an older image
  # is tested without refusals rather than failing for a reason this pass is not about.
  # TIKA_UAT_MOCK_FLAKY=1 or =0 overrides the detection.
  flaky=""
  if [[ -n "${TIKA_UAT_MOCK_FLAKY:-}" ]]; then
    [[ "$TIKA_UAT_MOCK_FLAKY" == "1" ]] && flaky="--flaky"
  elif image_client_retries "$image"; then
    flaky="--flaky"
  fi
  java "$uat_dir/MockInferenceServer.java" $mock_port $flaky > /tmp/tika-uat-mock.log 2>&1 &
  mock_pid=$!
  for i in $(seq 1 20); do
    curl -fsS --max-time 1 "http://localhost:$mock_port/v1/models" >/dev/null 2>&1 && break
    sleep 0.5
  done
  curl -fsS --max-time 2 "http://localhost:$mock_port/v1/models" >/dev/null 2>&1 \
    || { kill $mock_pid 2>/dev/null; die "mock inference server did not start (see /tmp/tika-uat-mock.log)"; }

  config="$(mktemp /tmp/tika-uat-inference-config.XXXXXX.json)"
  sed "s|MOCK_BASE_URL|http://host.docker.internal:$mock_port|g" "$uat_dir/uat-inference-config.json" > "$config"
  # the images run as UID 35002; a mktemp file is 0600, which that user cannot read
  chmod 644 "$config"

  docker run -d --name "$container_name" -p 127.0.0.1:9998:9998 \
    --add-host=host.docker.internal:host-gateway \
    -e TIKA_UAT_MOCK_KEY=uat-secret \
    -v "$config:/tika-config.json:ro" \
    "$image" -c /tika-config.json \
    || { kill $mock_pid 2>/dev/null; die "couldn't start $image with the inference config"; }
  wait_for_version
  TIKA_UAT_INFERENCE=1 TIKA_UAT_REQUIRE_MEDIA="$require_media" \
    TIKA_UAT_MOCK_URL="http://localhost:$mock_port" "$uat_script" http://localhost:9998
  rc=$?
  kill $mock_pid 2>/dev/null
  rm -f "$config"
  report_uat "$image (inference config)" "$container_name" $rc
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

  test-uat)
    # Run the tika-server REST UAT (release-tools/uat/run-uat.sh, two levels
    # up from this script in the tika repo) against both images.
    repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
    uat_script="${repo_root}/release-tools/uat/run-uat.sh"
    if [[ ! -x "$uat_script" ]]; then
      die "UAT script not found or not executable: $uat_script"
    fi
    test_docker_image_uat ${tika_docker_version} "$uat_script"
    test_docker_image_uat "${tika_docker_version}-full" "$uat_script" 1
    test_docker_image_uat_inference ${tika_docker_version} "$uat_script"
    test_docker_image_uat_inference "${tika_docker_version}-full" "$uat_script" 1
    ;;

  test-uat-snapshot)
    # The same images the docker-snapshot workflow publishes: Dockerfile.snapshot over the
    # unzipped local distribution. Run after `./mvnw install -pl tika-server/tika-server-standard -am`.
    repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
    uat_script="${repo_root}/release-tools/uat/run-uat.sh"
    [[ -x "$uat_script" ]] || die "UAT script not found or not executable: $uat_script"
    zip=$(ls "${repo_root}"/tika-server/tika-server-standard/target/tika-server-standard-*.zip 2>/dev/null | head -1)
    [[ -f "$zip" ]] || die "no tika-server-standard zip under tika-server/tika-server-standard/target; build it first"
    snapshot_version=$(basename "$zip" .zip | sed 's/^tika-server-standard-//')
    tika_docker_version="${snapshot_version}-snapshot"
    build_dir="$(mktemp -d /tmp/tika-uat-snapshot.XXXXXX)"
    for flavor in minimal full; do
      ctx="$build_dir/$flavor"
      mkdir -p "$ctx/tika-server"
      unzip -q "$zip" -d "$ctx/tika-server"
      cp "${repo_root}/tika-server/docker-build/$flavor/Dockerfile.snapshot" "$ctx/Dockerfile"
      tag="${tika_docker_version}"
      [[ "$flavor" == "full" ]] && tag="${tika_docker_version}-full"
      docker build -q -t "${image_name}:${tag}" --build-arg "TIKA_VERSION=${snapshot_version}" "$ctx" \
        || die "couldn't build the $flavor snapshot image"
    done
    rm -rf "$build_dir"
    test_docker_image_uat "${tika_docker_version}" "$uat_script"
    test_docker_image_uat "${tika_docker_version}-full" "$uat_script" 1
    test_docker_image_uat_inference "${tika_docker_version}" "$uat_script"
    test_docker_image_uat_inference "${tika_docker_version}-full" "$uat_script" 1
    ;;

  publish)
    # publish <tika_version> <build_number>
    # Tag scheme:
    #   apache/tika:<tika_version>            (mutable; moves on each rebuild)
    #   apache/tika:<tika_version>-<N>        (immutable; one per rebuild)
    #   apache/tika:latest                    (only for non-prerelease tags; tracks newest stable)
    # (plus the matching -full variants for the full image).
    publish_tika_version=$tika_docker_version  # first positional arg
    publish_build_number=$tika_version          # second positional arg
    if [[ -z "$publish_tika_version" || -z "$publish_build_number" ]]; then
      die "Usage: $0 publish <tika_version> <build_number>"
    fi
    # Only move :latest for non-prerelease tags. Preview releases never displace
    # the latest-stable pointer.
    push_latest=true
    case "$publish_tika_version" in
      *-alpha*|*-BETA*|*-RC*|*-SNAPSHOT*) push_latest=false ;;
    esac

    minimal_tags=( --tag "${image_name}:${publish_tika_version}" \
                   --tag "${image_name}:${publish_tika_version}-${publish_build_number}" )
    full_tags=(    --tag "${image_name}:${publish_tika_version}-full" \
                   --tag "${image_name}:${publish_tika_version}-${publish_build_number}-full" )
    if $push_latest; then
      minimal_tags+=( --tag "${image_name}:latest" )
      full_tags+=(    --tag "${image_name}:latest-full" )
    else
      echo "Skipping :latest for prerelease tag: $publish_tika_version"
    fi

    docker buildx create --use --name tika-builder || die "couldn't create builder"
    docker buildx build --platform linux/arm64/v8,linux/amd64,linux/s390x --output "type=image,push=true" \
      "${minimal_tags[@]}" --build-arg TIKA_VERSION=${publish_tika_version} --no-cache --builder tika-builder minimal \
      || stop_and_die "couldn't build multi-arch minimal"
    docker buildx build --platform linux/arm64/v8,linux/amd64,linux/s390x --output "type=image,push=true" \
      "${full_tags[@]}" --build-arg TIKA_VERSION=${publish_tika_version} --no-cache --builder tika-builder full \
      || stop_and_die "couldn't build multi-arch full"
    docker buildx rm tika-builder || die "couldn't stop builder -- make sure to stop the builder manually! "
    ;;

esac
