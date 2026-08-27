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
set -euo pipefail

usage() {
  cat >&2 <<'EOF'
Runs a tika-app batch extraction with provenance and a crash ledger that
tika-eval can join back in (Profile --pipesReport/--runInfo,
Compare --pipesReportA/B --runInfoA/B). Needs python3.

  run-batch.sh --app <tika-app dir or jar> --input <dir> --extracts <dir> \
               [--config <tika-config.json>] [--note "text"] [-- <extra tika-app args>]

Writes to <extracts>/.run-info/ (tika-eval skips that dir when crawling and picks these up by default):
  run-info-<run.id>.json    what ran (jar/lib/plugin sha256s, config sha256, jvm, host, time, exit code)
  crashes-<run.id>.jsonl    one line per failed result, from the file-system-jsonl-reporter
                            (only when the tika-app's file-system plugin ships it, TIKA-4846)

Secrets: JAVA_OPTS values that look like passwords/tokens are masked in run-info; the
tika-config you pass is recorded by path and sha256 only, never copied into the extracts dir.
EOF
  exit 2
}

APP=""; INPUT=""; EXTRACTS=""; CONFIG=""; NOTE=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --app) APP="$2"; shift 2;;
    --input) INPUT="$2"; shift 2;;
    --extracts) EXTRACTS="$2"; shift 2;;
    --config) CONFIG="$2"; shift 2;;
    --note) NOTE="$2"; shift 2;;
    -h|--help) usage;;
    --) shift; break;;
    *) echo "unknown arg: $1" >&2; usage;;
  esac
done
[[ -n "$APP" && -n "$INPUT" && -n "$EXTRACTS" ]] || usage
command -v python3 >/dev/null || { echo "python3 is required" >&2; exit 2; }

if [[ -d "$APP" ]]; then
  jars=("$APP"/tika-app-*.jar)
  [[ ${#jars[@]} -eq 1 && -f "${jars[0]}" ]] || { echo "expected exactly one tika-app-*.jar under $APP, found: ${jars[*]}" >&2; exit 2; }
  JAR="${jars[0]}"; APP_DIR="$APP"
else
  JAR="$APP"; APP_DIR=$(dirname "$JAR")
fi
[[ -f "$JAR" ]] || { echo "no tika-app jar at $JAR" >&2; exit 2; }

abs() { python3 -c 'import os,sys; print(os.path.realpath(sys.argv[1]))' "$1"; }
sha() { python3 -c 'import hashlib,sys; print(hashlib.sha256(open(sys.argv[1],"rb").read()).hexdigest())' "$1"; }
# prints the manifest value or nothing; exit 1 when absent so || chains work
manifest() {
  local v
  v=$(unzip -p "$JAR" META-INF/MANIFEST.MF 2>/dev/null | tr -d '\r' | awk -F': ' -v k="$1" '$1==k{print $2}')
  [[ -n "$v" ]] && echo "$v"
}

RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)-$(head -c4 /dev/urandom | od -An -tx1 | tr -d ' \n')"
mkdir -p "$EXTRACTS"
OUT_DIR="$(abs "$EXTRACTS")/.run-info"
mkdir -p "$OUT_DIR"
RUN_INFO="$OUT_DIR/run-info-$RUN_ID.json"
LEDGER="$OUT_DIR/crashes-$RUN_ID.jsonl"

TIKA_VERSION=$(manifest Implementation-Version || basename "$JAR" | sed -E 's/^tika-app-(.*)\.jar$/\1/')
GIT_COMMIT=$(manifest Git-Commit || manifest Implementation-Build || true)

LIB_SHA=""
if [[ -d "$APP_DIR/lib" ]]; then
  LIB_SHA=$(cd "$APP_DIR/lib" && for f in *.jar; do [[ -f "$f" ]] && echo "$f $(sha "$f")"; done | python3 -c 'import hashlib,sys; print(hashlib.sha256(sys.stdin.buffer.read()).hexdigest())')
fi

# the ledger needs the jsonl reporter inside the file-system plugin (TIKA-4846); look for the class, not a version
USE_LEDGER=false
if [[ -d "$APP_DIR/plugins" ]] && python3 - "$APP_DIR/plugins" <<'PY'
import io, sys, zipfile, glob, os
for z in glob.glob(os.path.join(sys.argv[1], "tika-pipes-file-system*.zip")):
    with zipfile.ZipFile(z) as outer:
        for n in outer.namelist():
            if n.endswith(".jar") and "tika-pipes-file-system" in n:
                with zipfile.ZipFile(io.BytesIO(outer.read(n))) as inner:
                    if any(m.endswith("FileSystemJsonlReporter.class") for m in inner.namelist()):
                        sys.exit(0)
sys.exit(1)
PY
then USE_LEDGER=true; else echo "no file-system-jsonl-reporter in $APP_DIR/plugins; recording run-info without a crash ledger" >&2; fi

# merged config lives outside the extracts dir: the user's config may carry fetcher/emitter secrets
TMP_DIR=$(mktemp -d); trap 'rm -rf "$TMP_DIR"' EXIT
EFFECTIVE_CONFIG="$CONFIG"
if $USE_LEDGER; then
  EFFECTIVE_CONFIG="$TMP_DIR/tika-config-$RUN_ID.json"
  python3 - "$CONFIG" "$EFFECTIVE_CONFIG" "$LEDGER" <<'PY'
import json, sys
src, dst, ledger = sys.argv[1:]
cfg = json.load(open(src)) if src else {}
reporters = cfg.setdefault("pipes-reporters", {})
if not isinstance(reporters, dict):
    sys.exit("pipes-reporters in %s is not an object" % src)
if "file-system-jsonl-reporter" in reporters:
    sys.exit("%s already configures file-system-jsonl-reporter; remove it or run without --config" % src)
# every non-success status: anything here leaves no extract behind
reporters["file-system-jsonl-reporter"] = {
    "path": ledger,
    "includes": ["OOM", "TIMEOUT", "UNSPECIFIED_CRASH", "FAILED_TO_INITIALIZE", "FETCHER_INITIALIZATION_EXCEPTION",
                 "EMITTER_INITIALIZATION_EXCEPTION", "CLIENT_UNAVAILABLE_WITHIN_MS", "FETCH_EXCEPTION", "EMIT_EXCEPTION",
                 "FETCHER_NOT_FOUND", "EMITTER_NOT_FOUND", "PAYLOAD_LIMIT_EXCEEDED"],
    "onExists": "EXCEPTION",
    "maxMessageLength": 4096,
}
json.dump(cfg, open(dst, "w"), indent=2)
PY
fi

CONFIG_SHA=""; CONFIG_ABS=""
if [[ -n "$CONFIG" ]]; then CONFIG_SHA=$(sha "$CONFIG"); CONFIG_ABS=$(abs "$CONFIG"); fi
JVM_ARGS="${JAVA_OPTS:-}"

# values go to python via the environment; no shell-into-json quoting
RB_RUN_ID="$RUN_ID" RB_NOTE="$NOTE" RB_START="$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
RB_JAR="$(abs "$JAR")" RB_JAR_SHA="$(sha "$JAR")" RB_VERSION="$TIKA_VERSION" RB_COMMIT="$GIT_COMMIT" \
RB_LIB_SHA="$LIB_SHA" RB_PLUGINS_DIR="$APP_DIR/plugins" RB_CONFIG="$CONFIG_ABS" RB_CONFIG_SHA="$CONFIG_SHA" \
RB_INPUT="$(abs "$INPUT")" RB_EXTRACTS="$(abs "$EXTRACTS")" \
RB_JAVA="$(java -version 2>&1 | head -1)" RB_JVM_ARGS="$JVM_ARGS" RB_LEDGER="$( $USE_LEDGER && echo "$LEDGER" || true )" \
python3 - "$RUN_INFO" <<'PY'
import glob, hashlib, json, os, socket, sys
e = os.environ.get
plugins = {}
for z in sorted(glob.glob(os.path.join(e("RB_PLUGINS_DIR", ""), "*.zip"))):
    plugins[os.path.basename(z)] = hashlib.sha256(open(z, "rb").read()).hexdigest()
tika = {"app_path": e("RB_JAR"), "app_sha256": e("RB_JAR_SHA"), "version": e("RB_VERSION"), "lib_sha256": e("RB_LIB_SHA"), "plugins": plugins}
if e("RB_COMMIT"):
    tika["git_commit"] = e("RB_COMMIT")
json.dump({
  "run": {"id": e("RB_RUN_ID"), "note": e("RB_NOTE"), "start": e("RB_START"), "host": socket.gethostname(), "user": e("USER", "")},
  "tika": tika,
  "config": {"path": e("RB_CONFIG"), "sha256": e("RB_CONFIG_SHA")},
  "input": {"path": e("RB_INPUT")},
  "extracts": {"path": e("RB_EXTRACTS")},
  "jvm": {"version": e("RB_JAVA"), "args": e("RB_JVM_ARGS", "")},
  "ledger": {"path": e("RB_LEDGER")},
}, open(sys.argv[1], "w"), indent=2)
PY
echo "run.id=$RUN_ID  run-info=$RUN_INFO" >&2

set +e
if [[ -n "$EFFECTIVE_CONFIG" ]]; then
  # shellcheck disable=SC2086  # JAVA_OPTS is word-split on purpose
  java $JVM_ARGS -jar "$JAR" --config="$EFFECTIVE_CONFIG" "$@" "$INPUT" "$EXTRACTS"
else
  # shellcheck disable=SC2086
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
