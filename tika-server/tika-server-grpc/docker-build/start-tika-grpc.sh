#!/bin/sh
echo "Tika Pipes Version:"
echo "${TIKA_PIPES_VERSION}"
echo "Tika Pipes Plugins:"
ls "/tika/plugins"
echo "Tika Pipes Log4j config:"
cat /tika/config/log4j2.xml
echo "Tika Pipes Max Inbound Message Size:"
echo "${TIKA_PIPES_MAX_INBOUND_MESSAGE_SIZE}"
echo "Tika Pipes Max Outbound Message Size:"
echo "${TIKA_PIPES_MAX_OUTBOUND_MESSAGE_SIZE}"
echo "Tika Pipes Num Threads:"
echo "${TIKA_PIPES_GRPC_NUM_THREADS}"
exec java \
  -Dgrpc.server.port=9090 \
  "-Dgrpc.server.max-inbound-message-size=${TIKA_PIPES_MAX_INBOUND_MESSAGE_SIZE}" \
  "-Dgrpc.server.max-outbound-message-size=${TIKA_PIPES_MAX_INBOUND_MESSAGE_SIZE}" \
  "-Dgrpc.server.numThreads=${TIKA_PIPES_MAX_INBOUND_MESSAGE_SIZE}" \
  "-Dlog4j.configurationFile=/tika/config/log4j2.xml" \
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
  -jar "/tika/libs/tika-pipes-grpc-${TIKA_PIPES_VERSION}.jar" \
  "--spring.config.location=file:/tika/config/application.yaml"
