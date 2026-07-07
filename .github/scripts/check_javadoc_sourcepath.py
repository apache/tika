#!/usr/bin/env python3
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
"""
Guard the hand-maintained <sourcepath> in tika-parent/pom.xml used by the
TIKA-4318 javadoc:aggregate workaround. That list must name every module's
src/main/java (except tika-grpc); a module missing from it is silently dropped
from the aggregated API docs. Run this right before building the javadocs.

  check:  python3 .github/scripts/check_javadoc_sourcepath.py [repo_root]
  fix:    python3 .github/scripts/check_javadoc_sourcepath.py --fix [repo_root]

Exit 0 = list matches the reactor; 1 = drift (missing/stale roots) or --fix rewrote it.
"""
import os
import re
import sys

PRUNE = {"target", ".git", ".local_m2_repo", "node_modules", ".mvn"}
EXCLUDE_MODULE_PREFIX = "tika-grpc/"          # protobuf gen-sources not on the aggregate classpath
POM = "tika-parent/pom.xml"


def actual_roots(root: str):
    roots = set()
    for dirpath, dirnames, _ in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in PRUNE]
        if not dirpath.replace(os.sep, "/").endswith("/src/main/java"):
            continue
        rel = os.path.relpath(dirpath, root).replace(os.sep, "/")
        if rel.startswith(EXCLUDE_MODULE_PREFIX):
            continue
        for _, _, files in os.walk(dirpath):
            if any(f.endswith(".java") and f != "package-info.java" for f in files):
                roots.add(rel)
                break
    return roots


def listed_roots(pom_text: str):
    m = re.search(r"<sourcepath>([^<]*)</sourcepath>", pom_text)
    if not m:
        sys.exit(f"ERROR: no <sourcepath> found in {POM}")
    return set(p.strip() for p in m.group(1).split(";") if p.strip()), m


def main() -> int:
    args = [a for a in sys.argv[1:] if a != "--fix"]
    fix = "--fix" in sys.argv
    root = os.path.abspath(args[0] if args else ".")
    pom_path = os.path.join(root, POM)
    text = open(pom_path, encoding="utf-8").read()

    actual = actual_roots(root)
    listed, m = listed_roots(text)
    missing = sorted(actual - listed)   # modules present but NOT in the list -> dropped from docs
    stale = sorted(listed - actual)     # entries in the list that no longer exist

    if not missing and not stale:
        print(f"OK: javadoc <sourcepath> covers all {len(actual)} module source roots.")
        return 0

    if fix:
        new_list = ";".join(sorted(actual))
        open(pom_path, "w", encoding="utf-8").write(
            text[: m.start(1)] + new_list + text[m.end(1):])
        print(f"FIXED: rewrote <sourcepath> in {POM} ({len(actual)} roots).")
        return 0

    print(f"ERROR: javadoc <sourcepath> in {POM} is out of sync with the reactor.\n")
    if missing:
        print("  MISSING (module exists but is not in <sourcepath> -> its API docs are dropped):")
        for r in missing:
            print(f"      {r}")
    if stale:
        print("  STALE (in <sourcepath> but no longer a module source root):")
        for r in stale:
            print(f"      {r}")
    print("\nFix: re-run with --fix, or edit tika-parent/pom.xml's javadoc <sourcepath>.")
    print("See TIKA-4318.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
