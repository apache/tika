#!/usr/bin/env bash
#
# Stage, commit, and push typed ParseResponse work for the Tika gRPC modules.
#
# Usage:
#   ./tika-grpc/scripts/commit-typed-parse-response.sh
#
# Creates or reuses branch typed-parse-response-grpc (never commits on main).
#
# Optional environment variables:
#   GIT_REMOTE   remote to push (default: fork -> ai-pipestream/tika)
#   GIT_BRANCH   branch name     (default: typed-parse-response-grpc)
#   DRY_RUN=1    print actions only, do not commit or push
#
# Upstream Apache remote stays as origin (read-only for most contributors).
# Add the fork once:
#   git remote add fork https://github.com/ai-pipestream/tika.git
#
# No Co-authored-by or tool attribution trailers are added.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

GIT_REMOTE="${GIT_REMOTE:-fork}"
GIT_BRANCH="${GIT_BRANCH:-typed-parse-response-grpc}"
DRY_RUN="${DRY_RUN:-0}"

paths=(
  pom.xml
  tika-bom/pom.xml
  tika-grpc-api
  tika-grpc-mapper
  tika-grpc/pom.xml
  tika-grpc/README.md
  tika-grpc/scripts/commit-typed-parse-response.sh
  tika-grpc/src/main/java/org/apache/tika/pipes/grpc/TikaGrpcServerImpl.java
  tika-grpc/src/main/proto/tika.proto
  tika-grpc/src/test/java/org/apache/tika/pipes/grpc/PipesBiDirectionalStreamingIntegrationTest.java
  tika-grpc/src/test/java/org/apache/tika/pipes/grpc/TikaGrpcServerTest.java
  tika-grpc/scripts/pr-typed-parse-response.md
)

echo "Repository: $ROOT"
echo "Branch:     $GIT_BRANCH"
echo "Remote:     $GIT_REMOTE"
echo

if [[ "$GIT_BRANCH" == "main" || "$GIT_BRANCH" == "master" ]]; then
  echo "Refusing to commit on $GIT_BRANCH. Set GIT_BRANCH to a feature branch."
  exit 1
fi

ensure_feature_branch() {
  if git show-ref --verify --quiet "refs/heads/$GIT_BRANCH"; then
    git checkout "$GIT_BRANCH"
  else
    git checkout -b "$GIT_BRANCH"
  fi
}

if [[ "$DRY_RUN" == "1" ]]; then
  echo "[dry-run] Would checkout or create branch: $GIT_BRANCH"
  echo "[dry-run] Would stage:"
  printf '  %s\n' "${paths[@]}"
  echo
  echo "[dry-run] Would commit with message:"
  cat <<'EOF'
Replace FetchAndParseReply string map with typed ParseResponse

Add tika-grpc-api (protobuf schema and descriptors) and tika-grpc-mapper
(Tika Metadata to ParseResponse). Wire tika-grpc to return parse_response on
fetch-and-parse RPCs. Include mapper tests and README updates.
EOF
  echo
  echo "[dry-run] Would push: git push -u $GIT_REMOTE $GIT_BRANCH"
  exit 0
fi

ensure_feature_branch

git add "${paths[@]}"

if git diff --cached --quiet; then
  echo "Nothing staged. Check paths or working tree."
  exit 1
fi

git commit -m "$(cat <<'EOF'
Replace FetchAndParseReply string map with typed ParseResponse

Add tika-grpc-api (protobuf schema and descriptors) and tika-grpc-mapper
(Tika Metadata to ParseResponse). Wire tika-grpc to return parse_response on
fetch-and-parse RPCs. Include mapper tests and README updates.
EOF
)"

git push -u "$GIT_REMOTE" "$GIT_BRANCH"

echo
echo "Pushed to $GIT_REMOTE/$GIT_BRANCH"
echo "Open a PR from that branch (not main). Body:"
echo "  tika-grpc/scripts/pr-typed-parse-response.md"
