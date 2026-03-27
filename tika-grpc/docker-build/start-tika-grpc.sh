#!/bin/bash

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

TIKA_GRPC_PORT="${TIKA_GRPC_PORT:-9090}"

echo "Tika Version: ${TIKA_VERSION}"
echo "Tika Plugins:"
ls "/tika/plugins"
echo "Tika gRPC Port: ${TIKA_GRPC_PORT}"

CONFIG_ARGS=()
if [ -n "${TIKA_CONFIG:-}" ]; then
  echo "Tika Config: ${TIKA_CONFIG}"
  CONFIG_ARGS+=("-c" "${TIKA_CONFIG}")
fi

exec java \
  --add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED \
  --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED \
  --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
  --add-opens=java.management/com.sun.jmx.mbeanserver=ALL-UNNAMED \
  --add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED \
  --add-opens=java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED \
  --add-opens=java.base/java.io=ALL-UNNAMED \
  --add-opens=java.base/java.nio=ALL-UNNAMED \
  --add-opens=java.base/java.util=ALL-UNNAMED \
  --add-opens=java.base/java.lang=ALL-UNNAMED \
  -Djava.net.preferIPv4Stack=true \
  -jar "/tika/libs/tika-grpc-${TIKA_VERSION}.jar" \
  "${CONFIG_ARGS[@]}" \
  -p "${TIKA_GRPC_PORT}" \
  --plugin-roots "/tika/plugins" \
  "$@"
