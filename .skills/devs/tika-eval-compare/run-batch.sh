#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Runs a tika-app batch extraction with provenance and a crash ledger that
# tika-eval can join back in (Profile --pipesReport/--runInfo,
# Compare --pipesReportA/B --runInfoA/B).
#
#   run-batch.sh --app <tika-app dir or jar> --input <dir> --extracts <dir> \
#                [--config <tika-config.json>] [--note "text"] [-- <extra tika-app args>]
#
# Writes to <extracts>/.run-info/ (tika-eval skips that dir when crawling and picks these up by default):
#   run-info-<run.id>.json    what ran (jar/lib/plugin sha256s, config sha256, jvm, git commit, host, time)
#   crashes-<run.id>.jsonl    one line per non-success result (file-system-jsonl-reporter; 4.1+ only)
#   tika-config-<run.id>.json the config actually passed to tika-app
set -euo pipefail

APP=""; INPUT=""; EXTRACTS=""; CONFIG=""; NOTE=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --app) APP="$2"; shift 2;;
    --input) INPUT="$2"; shift 2;;
    --extracts) EXTRACTS="$2"; shift 2;;
    --config) CONFIG="$2"; shift 2;;
    --note) NOTE="$2"; shift 2;;
    --) shift; break;;
    *) echo "unknown arg: $1" >&2; exit 2;;
  esac
done
[[ -n "$APP" && -n "$INPUT" && -n "$EXTRACTS" ]] || { sed -n '17,26p' "$0" >&2; exit 2; }

if [[ -d "$APP" ]]; then
  JAR=$(ls "$APP"/tika-app-*.jar | head -1); APP_DIR="$APP"
else
  JAR="$APP"; APP_DIR=$(dirname "$JAR")
fi
[[ -f "$JAR" ]] || { echo "no tika-app jar under $APP" >&2; exit 2; }

sha() { sha256sum "$1" | cut -d' ' -f1; }
manifest() { unzip -p "$JAR" META-INF/MANIFEST.MF 2>/dev/null | tr -d '\r' | awk -F': ' -v k="$1" '$1==k{print $2}'; }

RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)-$(head -c4 /dev/urandom | od -An -tx1 | tr -d ' \n')"
mkdir -p "$EXTRACTS"
OUT_DIR="$(readlink -f "$EXTRACTS")/.run-info"
mkdir -p "$OUT_DIR"
RUN_INFO="$OUT_DIR/run-info-$RUN_ID.json"
LEDGER="$OUT_DIR/crashes-$RUN_ID.jsonl"

TIKA_VERSION=$(manifest Implementation-Version || true)
[[ -n "$TIKA_VERSION" ]] || TIKA_VERSION=$(basename "$JAR" | sed -E 's/^tika-app-(.*)\.jar$/\1/')
GIT_COMMIT=$(manifest Git-Commit || manifest Implementation-Build || true)

LIB_SHA=""
if [[ -d "$APP_DIR/lib" ]]; then
  LIB_SHA=$(cd "$APP_DIR/lib" && for f in $(ls *.jar | sort); do echo "$f $(sha "$f")"; done | sha256sum | cut -d' ' -f1)
fi
PLUGINS_JSON="{}"
if [[ -d "$APP_DIR/plugins" ]]; then
  PLUGINS_JSON=$(cd "$APP_DIR/plugins" && for f in $(ls *.zip 2>/dev/null | sort); do printf '"%s":"%s",' "$f" "$(sha "$f")"; done | sed 's/,$//' | sed 's/^/{/;s/$/}/')
fi
CONFIG_SHA=""; CONFIG_ABS=""
if [[ -n "$CONFIG" ]]; then CONFIG_SHA=$(sha "$CONFIG"); CONFIG_ABS=$(readlink -f "$CONFIG"); fi

# 4.1+ tika-app ships the file-system plugin with the jsonl reporter; older versions get run-info only.
USE_LEDGER=false
case "$TIKA_VERSION" in 4.[1-9]*|[5-9].*) USE_LEDGER=true;; esac

EFFECTIVE_CONFIG="$CONFIG"
if $USE_LEDGER; then
  EFFECTIVE_CONFIG="$OUT_DIR/tika-config-$RUN_ID.json"
  python3 - "$CONFIG" "$EFFECTIVE_CONFIG" "$LEDGER" <<'PY'
import json, sys
src, dst, ledger = sys.argv[1:]
cfg = json.load(open(src)) if src else {}
cfg.setdefault("pipes-reporters", {})["file-system-jsonl-reporter"] = {
    "path": ledger,
    "includes": ["OOM", "TIMEOUT", "UNSPECIFIED_CRASH", "FAILED_TO_INITIALIZE", "PAYLOAD_LIMIT_EXCEEDED", "EMIT_EXCEPTION", "FETCH_EXCEPTION"],
    "onExists": "EXCEPTION",
    "maxMessageLength": 10000,
}
json.dump(cfg, open(dst, "w"), indent=2)
PY
fi

JVM_ARGS="${JAVA_OPTS:-}"
# values go to python via the environment; no shell-into-json quoting
RB_RUN_ID="$RUN_ID" RB_NOTE="$NOTE" RB_START="$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
RB_JAR="$(readlink -f "$JAR")" RB_JAR_SHA="$(sha "$JAR")" RB_VERSION="$TIKA_VERSION" RB_COMMIT="$GIT_COMMIT" \
RB_LIB_SHA="$LIB_SHA" RB_PLUGINS="$PLUGINS_JSON" RB_CONFIG="$CONFIG_ABS" RB_CONFIG_SHA="$CONFIG_SHA" \
RB_EFFECTIVE_CONFIG="$( [[ -n "$EFFECTIVE_CONFIG" ]] && readlink -f "$EFFECTIVE_CONFIG" || true )" \
RB_INPUT="$(readlink -f "$INPUT")" RB_EXTRACTS="$(readlink -f "$EXTRACTS")" \
RB_JAVA="$(java -version 2>&1 | head -1)" RB_JVM_ARGS="$JVM_ARGS" RB_LEDGER="$( $USE_LEDGER && echo "$LEDGER" || true )" \
python3 - "$RUN_INFO" <<'PY'
import json, os, socket, sys
e = os.environ.get
json.dump({
  "run": {"id": e("RB_RUN_ID"), "note": e("RB_NOTE"), "start": e("RB_START"), "host": socket.gethostname(), "user": e("USER", "")},
  "tika": {"app_path": e("RB_JAR"), "app_sha256": e("RB_JAR_SHA"), "version": e("RB_VERSION"), "git_commit": e("RB_COMMIT"),
           "lib_sha256": e("RB_LIB_SHA"), "plugins": json.loads(e("RB_PLUGINS") or "{}")},
  "config": {"path": e("RB_CONFIG"), "sha256": e("RB_CONFIG_SHA"), "effective_path": e("RB_EFFECTIVE_CONFIG")},
  "input": {"path": e("RB_INPUT")},
  "extracts": {"path": e("RB_EXTRACTS")},
  "jvm": {"version": e("RB_JAVA"), "args": e("RB_JVM_ARGS")},
  "ledger": {"path": e("RB_LEDGER")},
}, open(sys.argv[1], "w"), indent=2)
PY
echo "run.id=$RUN_ID  run-info=$RUN_INFO" >&2

set +e
if [[ -n "$EFFECTIVE_CONFIG" ]]; then
  java $JVM_ARGS -jar "$JAR" --config="$EFFECTIVE_CONFIG" "$@" "$INPUT" "$EXTRACTS"
else
  java $JVM_ARGS -jar "$JAR" "$@" "$INPUT" "$EXTRACTS"
fi
RC=$?
set -e

python3 - "$RUN_INFO" "$RC" <<'PY'
import json, sys, datetime
p, rc = sys.argv[1], int(sys.argv[2])
d = json.load(open(p))
d["run"]["end"] = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
d["run"]["exit_code"] = rc
json.dump(d, open(p, "w"), indent=2)
PY
exit $RC
