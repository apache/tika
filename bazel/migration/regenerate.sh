#!/bin/bash
# Licensed to the Apache Software Foundation (ASF) under the Apache License, Version 2.0.
#
# Regenerate the module BUILD.bazel files from the POMs.
#   bazel/migration/regenerate.sh          regenerate only
#   bazel/migration/regenerate.sh --fix    also loop on strict-deps errors, recording each
#                                          'buildozer add deps' suggestion in overrides.json
set -euo pipefail
cd "$(dirname "$0")/../.."
M=bazel/migration
OUT=$(mktemp -d)
S=tika-parsers/tika-parsers-standard/tika-parsers-standard-modules
python3 -I $M/pomgraph.py . "$OUT/graph.json" \
  tika-annotation-processor tika-test-support tika-core tika-serialization tika-plugins-core \
  tika-parsers/tika-http-jdk $(ls -d $S/*/pom.xml | xargs -n1 dirname) tika-parsers/tika-parsers-standard/tika-parsers-standard-package \
  tika-encoding-detectors/tika-encoding-detector-html \
  tika-encoding-detectors/tika-encoding-detector-mojibuster tika-ml/tika-ml-core tika-ml/tika-ml-junkdetect
python3 -I $M/gen_bazel.py . "$OUT/graph.json" $M/overrides.json
[ "${1:-}" = "--fix" ] || exit 0
for i in $(seq 1 10); do
  rc=0; bazel build --keep_going --nojava_header_compilation //... > "$OUT/build-$i.log" 2>&1 || rc=$?
  added=$(python3 -I $M/apply_buildozer.py "$OUT/graph.json" $M/overrides.json "$OUT/build-$i.log")
  echo "pass $i: build exit $rc, $added"
  python3 -I $M/gen_bazel.py . "$OUT/graph.json" $M/overrides.json > /dev/null
  [ "$added" = "added 0" ] && break
done
