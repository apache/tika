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
"""Generate Bazel BUILD files and MODULE.bazel artifacts for Tika from graph.json.

Usage: gen_bazel.py <tika-root> <graph.json> <overrides.json>
overrides.json: {"<artifactId>": {"deps": [...], "test_deps": [...], ...}}
"""
import json
import os
import re
import sys

root, graph_path, ovr_path = sys.argv[1:]
g = json.load(open(graph_path))
ovr = json.load(open(ovr_path)) if os.path.exists(ovr_path) else {}

LICENSE = """# Licensed to the Apache Software Foundation (ASF) under one or more
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

# Artifacts every test runtime needs but no POM names directly.
EXTRA_ARTIFACTS = {
    "org.junit.platform:junit-platform-launcher",
    "org.junit.platform:junit-platform-reporting",
}


def mlabel(group, artifact):
    return "@maven//:" + re.sub(r"[^A-Za-z0-9]", "_", f"{group}_{artifact}")


def ilabel(artifact_id, test_jar=False):
    d = g[artifact_id]["dir"]
    if test_jar:
        return f"//{d}:tests"
    return f"//{d}" if d.split("/")[-1] == artifact_id else f"//{d}:{artifact_id}"


artifacts = {}  # "g:a" -> version or None
for a, m in g.items():
    for d in m["deps"]:
        if d["internal"] or d["type"] == "pom":
            continue
        key = f"{d['groupId']}:{d['artifactId']}"
        if artifacts.get(key) is None:
            artifacts[key] = d["version"]
for k in EXTRA_ARTIFACTS:
    artifacts.setdefault(k, None)

ABSTRACT = re.compile(r"^\s*(public\s+)?abstract\s+class\s", re.M)
SUREFIRE = re.compile(r"^(Test.*|.*Test|.*Tests|.*TestCase)\.java$")


def fmt_list(name, items, indent="    "):
    items = sorted(set(items), key=lambda x: (not x.startswith(":"), not x.startswith("//"), x))
    if not items:
        return ""
    if len(items) == 1:
        return f'{indent}{name} = ["{items[0]}"],\n'
    body = "".join(f'{indent}    "{i}",\n' for i in items)
    return f"{indent}{name} = [\n{body}{indent}],\n"


for a, m in sorted(g.items()):
    d = os.path.join(root, m["dir"])
    o = ovr.get(a, {})
    if m["packaging"] == "pom":
        # Aggregator: re-export the modules a Maven user gets by depending on it.
        exports = [ilabel(x["artifactId"]) if x["internal"] else mlabel(x["groupId"], x["artifactId"])
                   for x in m["deps"] if x["scope"] in ("compile", "runtime")]
        out = LICENSE + '\nload("@rules_java//java:defs.bzl", "java_library")\n\n'
        out += f'java_library(\n    name = "{a}",\n'
        out += fmt_list("exports", exports + o.get("exports", []))
        out += '    visibility = ["//visibility:public"],\n)\n'
        open(os.path.join(d, "BUILD.bazel"), "w").write(out)
        continue
    if a == "tika-annotation-processor":
        continue  # hand-written

    deps, test_deps = [], []
    for x in m["deps"]:
        if x["type"] == "pom":
            continue
        tj = x["type"] == "test-jar" or x["classifier"] == "tests"
        lab = ilabel(x["artifactId"], tj) if x["internal"] else mlabel(x["groupId"], x["artifactId"])
        if lab == ilabel(a):
            continue
        (test_deps if x["scope"] == "test" else deps).append(lab)
    test_deps = [t for t in test_deps if t not in deps]
    deps += o.get("deps", [])
    test_deps += o.get("test_deps", [])

    test_dir = os.path.join(d, "src/test/java")
    excludes = []
    for dp, _, fn in os.walk(test_dir):
        for f in fn:
            if SUREFIRE.match(f) and ABSTRACT.search(open(os.path.join(dp, f), errors="replace").read()):
                excludes.append(os.path.relpath(os.path.join(dp, f), d))
    test_jar_used = any(
        x["artifactId"] == a and (x["type"] == "test-jar" or x["classifier"] == "tests")
        for mm in g.values() for x in mm["deps"])

    head = '\nload("@rules_java//java:defs.bzl", "java_plugin")' if "java_plugin(" in o.get("append", "") else ""
    out = LICENSE + head + '\nload("//bazel:tika.bzl", "tika_java_module")\n\n'
    out += f'tika_java_module(\n    name = "{a}",\n'
    out += fmt_list("deps", deps)
    out += fmt_list("test_deps", test_deps)
    out += fmt_list("test_excludes", excludes + o.get("test_excludes", []))
    for k in ("resource_excludes", "javacopts", "test_jvm_flags", "runtime_deps", "test_runtime_deps"):
        out += fmt_list(k, o.get(k, []))
    if test_jar_used:
        out += "    test_jar_resources = True,\n"
    for k, v in o.get("extra", {}).items():
        out += f"    {k} = {json.dumps(v).replace('true', 'True').replace('false', 'False')},\n"
    out += ")\n"
    if o.get("append"):
        out += "\n" + o["append"]
    open(os.path.join(d, "BUILD.bazel"), "w").write(out)

# Emit artifact list for MODULE.bazel.
lines = []
for k in sorted(artifacts):
    v = artifacts[k]
    lines.append(f'        "{k}:{v}",' if v else f'        "{k}",')
open(os.path.join(os.path.dirname(graph_path), "artifacts.txt"), "w").write("\n".join(lines) + "\n")
print(f"{len(artifacts)} artifacts; BUILD files written for {len(g)} modules")
