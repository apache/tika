#!/usr/bin/env bash
set -euo pipefail

PROTOC="$1"
PROTO_ROOT="$2"
DEPS_DIR="$3"
OUT="$4"

mkdir -p "$(dirname "$OUT")"

args=("-I" "$PROTO_ROOT")
if [[ -d "$DEPS_DIR" ]]; then
  for d in "$DEPS_DIR"/*/; do
    [[ -d "$d" ]] && args+=("-I" "$d")
  done
fi

"$PROTOC" "${args[@]}" \
  --descriptor_set_out="$OUT" \
  --include_imports \
  org/apache/tika/grpc/v1/parse_response.proto
