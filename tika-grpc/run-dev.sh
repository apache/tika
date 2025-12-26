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

mvn exec:java -Pdev -Dconfig.file="$CONFIG_FILE"
