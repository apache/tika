#!/usr/bin/env bash
#
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
# Portable REST UAT for tika-server. Exercises the default-mode endpoint
# surface, the security gating (allowPerRequestConfig / allowPipes), header
# behavior, and error handling against an ALREADY-RUNNING server.
#
# This script does NOT start or stop the server. Point it at a running
# instance:
#
#     release-tools/uat/run-uat.sh [base-url]      # default http://localhost:9998
#
# Exit 0 if every check passes, 1 otherwise. Used by docker-tool.sh test-uat,
# the tika-e2e-tests/tika-server RunUatSmokeTest, and pre-vote release
# verification. See docs .../advanced/integration-testing/run-uat-script.adoc.

set -u

BASE="${1:-http://localhost:9998}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FILES="${TIKA_UAT_TEST_FILES:-$SCRIPT_DIR/test-files}"
PDF="$FILES/testPDF.pdf"
HTML="$FILES/testHTML.html"
DOCX="$FILES/test_recursive_embedded.docx"
OCRPNG="$FILES/testOCR_spacing.png"

# Set TIKA_UAT_REQUIRE_OCR=1 to make the OCR check a hard failure when no OCR text
# comes back (used for the -full Docker image, which ships tesseract). Left unset,
# the OCR check is skipped when OCR is unavailable (minimal image / no tesseract).
REQUIRE_OCR="${TIKA_UAT_REQUIRE_OCR:-}"

PASS=0
FAIL=0
SKIP=0
FAILED_NAMES=()

for f in "$PDF" "$HTML" "$DOCX" "$OCRPNG"; do
  if [[ ! -f "$f" ]]; then
    echo "FATAL: missing test file: $f" >&2
    exit 2
  fi
done
if ! command -v curl >/dev/null 2>&1; then
  echo "FATAL: curl not found" >&2
  exit 2
fi

# pass/fail bookkeeping
ok()   { PASS=$((PASS+1)); printf '  PASS  %s\n' "$1"; }
skip() { SKIP=$((SKIP+1)); printf '  SKIP  %s\n' "$1"; }
bad()  { FAIL=$((FAIL+1)); FAILED_NAMES+=("$1"); printf '  FAIL  %s\n' "$1";
         [[ -n "${2:-}" ]] && printf '        expected: %s\n' "$2";
         [[ -n "${3:-}" ]] && printf '        got: %s\n' "$(printf '%s' "$3" | head -c 200 | tr '\n' ' ')"; }

# assert_contains <name> <expected-substr> <actual>
assert_contains() {
  if printf '%s' "$3" | grep -qiF -- "$2"; then ok "$1"; else bad "$1" "contains '$2'" "$3"; fi
}
# assert_status <name> <expected-code> <actual-code> [body]
assert_status() {
  if [[ "$3" == "$2" ]]; then ok "$1"; else bad "$1" "HTTP $2" "HTTP $3 ${4:-}"; fi
}

echo "== tika-server REST UAT against $BASE =="

# --- introspection ---
assert_contains "T1 GET /version" "Apache Tika" "$(curl -s "$BASE/version")"
assert_contains "T13 GET /parsers" "org.apache.tika" "$(curl -s -H 'Accept: text/plain' "$BASE/parsers")"
assert_contains "T14 GET /detectors" "org.apache.tika" "$(curl -s -H 'Accept: text/plain' "$BASE/detectors")"
assert_contains "T15 GET /mime-types" "application/pdf" "$(curl -s -H 'Accept: application/json' "$BASE/mime-types")"

# --- detection ---
assert_contains "T2 PUT /detect" "application/pdf" "$(curl -s -X PUT -T "$PDF" "$BASE/detect")"

# --- parse variants (testPDF.pdf contains the literal 'Apache Tika') ---
assert_contains "T3 PUT /tika/text" "Apache Tika" "$(curl -s -X PUT -T "$PDF" "$BASE/tika/text")"
assert_contains "T4 PUT /tika/html" "<body"       "$(curl -s -X PUT -T "$PDF" "$BASE/tika/html")"
assert_contains "T5 PUT /tika/xml"  "<html"       "$(curl -s -X PUT -T "$PDF" "$BASE/tika/xml")"
assert_contains "T6 PUT /tika/json" "Content-Type" "$(curl -s -X PUT -T "$PDF" -H 'Accept: application/json' "$BASE/tika/json")"

# --- metadata ---
assert_contains "T7 PUT /meta" "Content-Type" "$(curl -s -X PUT -H 'Accept: application/json' -T "$PDF" "$BASE/meta")"
assert_contains "T8 PUT /meta/{field}" "application/pdf" "$(curl -s -X PUT -T "$PDF" "$BASE/meta/Content-Type")"

# --- recursive metadata ---
assert_contains "T9 PUT /rmeta" "Content-Type" "$(curl -s -X PUT -T "$DOCX" "$BASE/rmeta")"
assert_contains "T10 PUT /rmeta/text" "Content-Type" "$(curl -s -X PUT -T "$DOCX" "$BASE/rmeta/text")"

# --- language detection ---
# Lenient on WHICH language (short text is unreliable) but strict that we got a language
# code back: the previous version called ok() in both branches, so it could never fail.
LANG_CODE=$(curl -s -X PUT -T "$PDF" "$BASE/language")
if printf '%s' "$LANG_CODE" | grep -qE '^[a-z]{2,3}$'; then
  ok "T11 PUT /language ('$LANG_CODE')"
else
  bad "T11 PUT /language" "an ISO language code" "$LANG_CODE"
fi

# --- handler selection ---
# /tika/text is body-only (3.x BodyContentHandler). testHTML.html's <title> is
# "Title : Test Indexation Html"; the whole-document handler emits it as leading text,
# the body handler does not. This is the 4.x regression that /tika/json/text still serves.
HTML_TEXT=$(curl -s -X PUT -T "$HTML" "$BASE/tika/text")
if printf '%s' "$HTML_TEXT" | grep -qiF "Test Indexation Html" &&
   ! printf '%s' "$HTML_TEXT" | grep -qiF "Title : Test Indexation Html"; then
  ok "T31 PUT /tika/text is body-only (no title)"
else
  bad "T31 PUT /tika/text body-only" "body text without the <title>" "$HTML_TEXT"
fi

# An unnamed handler takes the default, which is markdown -- so /tika/json must agree
# with /tika/json/md, not with /tika/json/text.
J_DEFAULT=$(curl -s -X PUT -T "$HTML" -H 'Accept: application/json' "$BASE/tika/json")
J_MD=$(curl -s -X PUT -T "$HTML" -H 'Accept: application/json' "$BASE/tika/json/md")
if [[ -n "$J_DEFAULT" && "$J_DEFAULT" == "$J_MD" ]]; then
  ok "T32 /tika/json defaults to markdown"
else
  bad "T32 /tika/json defaults to markdown" "same body as /tika/json/md" "$J_DEFAULT"
fi

# An unrecognized handler name is a caller typo: 400, not the default handler's output.
BADH=$(curl -s -o /dev/null -w '%{http_code}' -X PUT -T "$PDF" "$BASE/tika/json/txet")
assert_status "T33 unknown handler name -> 400" "400" "$BADH"

# --- XMP: content negotiation on /meta, not a path. This shipped broken in 4.0.0
# alpha-1 and beta-1 (the resource failed to load), so it is worth a smoke check. ---
XMP=$(curl -s -X PUT -T "$PDF" -H 'Accept: application/rdf+xml' "$BASE/meta")
assert_contains "T34 PUT /meta as rdf+xml" "rdf:RDF" "$XMP"

# --- removed endpoints must be gone, not silently doing something ---
TR404=$(curl -s -o /dev/null -w '%{http_code}' -X PUT -T "$PDF" "$BASE/translate/all/foo/en/es")
assert_status "T35 /translate removed -> 404" "404" "$TR404"

# --- embedded extraction: response must be a valid zip (PK magic) ---
ZIP=$(mktemp); curl -s -X PUT -T "$DOCX" "$BASE/unpack/all" -o "$ZIP"
if [[ -s "$ZIP" ]] && unzip -l "$ZIP" >/dev/null 2>&1; then ok "T12 PUT /unpack/all (valid zip)"; else bad "T12 PUT /unpack/all" "valid zip" "$(head -c 80 "$ZIP")"; fi
rm -f "$ZIP"

# --- multipart variants ---
assert_contains "T16 POST /meta/form"  "Content-Type" "$(curl -s -X POST -F "upload=@$PDF" -H 'Accept: application/json' "$BASE/meta/form")"
assert_contains "T17 POST /rmeta/form" "Content-Type" "$(curl -s -X POST -F "upload=@$DOCX" "$BASE/rmeta/form")"

# --- SECURITY GATING (gate 1: path-based ConfigEndpointSecurityFilter) ---
# Per-request /config endpoints must be 403 in default mode (allowPerRequestConfig=false).
# Note: the unpack config-variant is /unpack/all/config; /unpack/config does not exist
# (the {id} template requires a leading slash, so it 404s) -- the old docs were wrong.
for ep in meta/config rmeta/config tika/config unpack/all/config; do
  RESP=$(curl -s -w '\n%{http_code}' -X POST -F "file=@$PDF" "$BASE/$ep")
  CODE=$(printf '%s' "$RESP" | tail -n1)
  BODY=$(printf '%s' "$RESP" | sed '$d')
  if [[ "$CODE" == "403" ]] && printf '%s' "$BODY" | grep -qiF "disabled"; then
    ok "T18 POST /$ep blocked (403 + 'disabled')"
  else
    bad "T18 POST /$ep" "403 + 'disabled'" "HTTP $CODE $BODY"
  fi
done

# --- SECURITY GATING (gate 2: content-based multipart 'config' part) ---
# A 'config' part on an endpoint that accepts one must be 403 even when the path
# has no /config (TikaResource.setupMultipartConfig). Exercises the second enforcement point.
RESP=$(curl -s -w '\n%{http_code}' -X POST -F "file=@$PDF" -F 'config={"parsers":[{"pdf-parser":{}}]}' "$BASE/unpack")
CODE=$(printf '%s' "$RESP" | tail -n1)
BODY=$(printf '%s' "$RESP" | sed '$d')
if [[ "$CODE" == "403" ]] && printf '%s' "$BODY" | grep -qiF "disabled"; then
  ok "T18b POST /unpack with config part blocked (403 + 'disabled')"
else
  bad "T18b POST /unpack with config part" "403 + 'disabled'" "HTTP $CODE $BODY"
fi

# --- SECURITY GATING: /status is NOT registered by default (must be 404, not 200) ---
SCODE=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/status")
assert_status "T18s GET /status not enabled by default" "404" "$SCODE"

# --- headers ---
HCODE=$(curl -s -o /dev/null -w '%{http_code}' -X PUT -H 'X-Tika-OCRskipOcr: true' -T "$PDF" "$BASE/tika/text")
assert_status "T26 X-Tika-OCRskipOcr header" "200" "$HCODE"

# --- error handling ---
N404=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/nonexistent")
assert_status "T28 unknown endpoint -> 404" "404" "$N404"
M405=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$BASE/tika/text")
assert_status "T29 invalid method -> 405" "405" "$M405"

# --- OCR (conditional: requires tesseract on the server) ---
# A PNG carries no text layer, so any extracted text proves OCR ran. Standalone images
# are OCR'd by default (no per-request config needed). Skipped when OCR is unavailable
# (minimal image / no tesseract); hard failure only when TIKA_UAT_REQUIRE_OCR is set
# (the -full Docker image, which ships tesseract).
OCR_OUT=$(curl -s -X PUT -T "$OCRPNG" "$BASE/tika/text")
if printf '%s' "$OCR_OUT" | grep -qiF "The quick"; then
  ok "T30 OCR PUT /tika/text (image -> 'The quick brown fox')"
elif [[ -n "$REQUIRE_OCR" ]]; then
  bad "T30 OCR PUT /tika/text" "OCR text 'The quick' (tesseract required)" "$OCR_OUT"
else
  skip "T30 OCR PUT /tika/text -- no OCR text (tesseract not available on server)"
fi

echo "== UAT done: $PASS passed, $FAIL failed, $SKIP skipped =="
if [[ $FAIL -gt 0 ]]; then
  printf 'FAILED: %s\n' "${FAILED_NAMES[*]}"
  exit 1
fi
exit 0
