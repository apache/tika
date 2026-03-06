#!/bin/bash
# Run tika-grpc in development mode with plugin hot-reloading
#
# Usage:
#   ./run-dev.sh [config-file]
#
# If no config file is specified, defaults to dev-tika-config.json

CONFIG_FILE="${1:-dev-tika-config.json}"

echo "🚀 Starting Tika gRPC Server in Development Mode"
echo "================================================"
echo "Config file: $CONFIG_FILE"
echo "Plugin dev mode: ENABLED"
echo ""
echo "Plugins will be loaded from target/classes directories"
echo "Press Ctrl+C to stop the server"
echo ""

export MAVEN_OPTS="--add-opens=java.base/java.io=ALL-UNNAMED \
--add-opens=java.base/java.nio=ALL-UNNAMED \
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
--add-opens=java.base/java.lang=ALL-UNNAMED \
--add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
--add-opens=java.base/java.lang.invoke=ALL-UNNAMED \
--add-opens=java.base/java.util=ALL-UNNAMED \
--add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED \
--add-opens=java.base/java.math=ALL-UNNAMED \
--add-opens=java.base/java.time=ALL-UNNAMED \
--add-opens=java.base/java.text=ALL-UNNAMED \
--add-opens=java.base/sun.util.calendar=ALL-UNNAMED \
--add-opens=java.management/com.sun.jmx.mbeanserver=ALL-UNNAMED \
--add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED \
--add-opens=java.management/sun.management=ALL-UNNAMED"

mvn exec:java -Pdev -Dconfig.file="$CONFIG_FILE"
