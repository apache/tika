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
"""Fold Bazel's strict-deps 'buildozer add deps' suggestions into overrides.json.

Usage: apply_buildozer.py <graph.json> <overrides.json> <build.log>...
Prints how many new deps were added.
"""
import json
import os
import re
import sys

graph_path, ovr_path, *logs = sys.argv[1:]
g = json.load(open(graph_path))
ovr = json.load(open(ovr_path)) if os.path.exists(ovr_path) else {}
bydir = {m["dir"]: a for a, m in g.items()}

pat = re.compile(r"buildozer 'add deps ([^']+)' (//[^\s:]+)(?::(\S+))?")
# Strict-deps errors that name only a jar path; the owning target follows in the
# "(from java_library rule target //pkg:name)" line before them.
jarpat = re.compile(r"TOOL_INFO: \"bazel-out/[^\"]*maven\+maven/(.+)/([^/]+)/[^/]+/(?:header_|processed_)?[^/]+\.jar\"")
tgtpat = re.compile(r"from java_library rule target (//[^\s:]+)(?::(\S+))?\)")
added = 0
for log in logs:
    current = None
    for line in open(log, errors="replace"):
        t = tgtpat.search(line)
        if t:
            current = (t.group(1), t.group(2))
        j = jarpat.search(line)
        if j and current:
            group = j.group(1).replace("/", ".")
            lab = "@maven//:" + re.sub(r"[^A-Za-z0-9]", "_", group + "_" + j.group(2))
            line = "buildozer 'add deps %s' %s%s" % (lab, current[0], ":" + current[1] if current[1] else "")
        m = pat.search(line)
        if not m:
            continue
        labels, pkg, target = m.group(1).split(), m.group(2)[2:], m.group(3)
        art = bydir.get(pkg)
        if art is None:
            print("unknown package", pkg, file=sys.stderr)
            continue
        attr = "test_deps" if target == "tests" else "deps"
        if target not in (None, art, "tests"):
            print("unexpected target", pkg, target, file=sys.stderr)
            continue
        cur = ovr.setdefault(art, {}).setdefault(attr, [])
        for lab in labels:
            if lab not in cur:
                cur.append(lab)
                added += 1
json.dump(ovr, open(ovr_path, "w"), indent=1, sort_keys=True)
print(f"added {added}")
