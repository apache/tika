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

echo "Tika Version:"
echo "${TIKA_VERSION}"
echo "Tika Plugins:"
ls "/tika/plugins"
echo "Tika gRPC Max Inbound Message Size:"
echo "${TIKA_GRPC_MAX_INBOUND_MESSAGE_SIZE}"
echo "Tika gRPC Max Outbound Message Size:"
echo "${TIKA_GRPC_MAX_OUTBOUND_MESSAGE_SIZE}"
echo "Tika gRPC Num Threads:"
echo "${TIKA_GRPC_NUM_THREADS}"
exec java \
  -Dgrpc.server.port=9090 \
  "-Dgrpc.server.max-inbound-message-size=${TIKA_GRPC_MAX_INBOUND_MESSAGE_SIZE}" \
  "-Dgrpc.server.max-outbound-message-size=${TIKA_GRPC_MAX_OUTBOUND_MESSAGE_SIZE}" \
  "-Dgrpc.server.numThreads=${TIKA_GRPC_NUM_THREADS}" \
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
  "-Dplugins.pluginDirs=/tika/plugins" \
  -jar "/tika/libs/tika-grpc-${TIKA_VERSION}.jar"
