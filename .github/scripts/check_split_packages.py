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
Fail if any Java package is declared in more than one Maven module ("split
package"). JPMS forbids split packages on the module path, and Tika keeps
one-package-per-module as an invariant (see TIKA-4xxx). This is a structural,
reactor-wide check that per-module tools (checkstyle, forbidden-apis, spotless,
banDuplicateClasses) cannot express.

Scope: main sources only (src/main/java) -- that is what JPMS cares about;
test sources live in the unnamed module and are intentionally excluded.

Generated code counts too: protoc output ships in the module's jar, so a .proto
whose `option java_package` names another module's package is a split package
even though no such directory exists in src/main/java. Those classes land under
target/, which is pruned here and would need a build to see, so the proto option
is read straight from src/main/proto instead (TIKA-4808).

Usage:  python3 .github/scripts/check_split_packages.py [repo_root]
Exit:   0 = no split packages, 1 = split package(s) found.
"""
import os
import re
import sys
import collections

PRUNE = {"target", ".git", ".local_m2_repo", "node_modules", ".mvn"}

JAVA_PACKAGE_OPTION = re.compile(
    r'^\s*option\s+java_package\s*=\s*"([^"]+)"\s*;', re.M)


def main() -> int:
    root = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else ".")
    pkg_to_modules = collections.defaultdict(set)
    for dirpath, dirnames, files in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in PRUNE]
        norm = dirpath.replace(os.sep, "/")

        if norm.endswith("/src/main/proto") or "/src/main/proto/" in norm + "/":
            marker = os.sep + os.path.join("src", "main", "proto")
            idx = dirpath.find(marker)
            if idx > 0:
                module = os.path.relpath(dirpath[:idx], root).replace(os.sep, "/")
                for f in files:
                    if not f.endswith(".proto"):
                        continue
                    with open(os.path.join(dirpath, f), encoding="utf-8") as fh:
                        m = JAVA_PACKAGE_OPTION.search(fh.read())
                    if m:
                        pkg_to_modules[m.group(1)].add(module)
            continue

        if not norm.endswith("/src/main/java"):
            continue
        src_root = dirpath
        module = os.path.relpath(dirpath[: -len(os.sep + "src/main/java")], root).replace(os.sep, "/")
        for sub, _, files in os.walk(src_root):
            if any(f.endswith(".java") and f != "package-info.java" for f in files):
                pkg = os.path.relpath(sub, src_root).replace(os.sep, ".")
                if pkg and pkg != ".":
                    pkg_to_modules[pkg].add(module)

    splits = {p: sorted(m) for p, m in pkg_to_modules.items() if len(m) > 1}
    if not splits:
        print(f"OK: no split packages ({len(pkg_to_modules)} packages across the reactor).")
        return 0

    print("ERROR: split package(s) found -- each package must live in exactly one module.\n")
    for pkg, mods in sorted(splits.items()):
        print(f"  {pkg}")
        for m in mods:
            print(f"      {m}")
    print("\nJPMS forbids a package spanning modules. Move the minority side into a")
    print("module-specific package (e.g. org.apache.tika.detect.icu4j) so the package")
    print("lives in one module only. See the maintainer docs on split packages.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
