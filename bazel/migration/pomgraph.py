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
"""Static reader for Tika's POMs: effective direct dependencies per module.

Reads pom.xml files as data (no Maven run). Resolves properties and
dependencyManagement through the internal parent chain. BOM-imported
versions are left unresolved (version None) and reported as BOM-managed.

Usage: pomgraph.py <tika-root> <out.json> <module-dir>...
Writes the closure of the named modules over internal dependencies.
"""
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def t(el, path):
    x = el.find(path, NS)
    return x.text.strip() if x is not None and x.text else None


def parse(path):
    root = ET.parse(path).getroot()
    p = root.find("m:parent", NS)
    props = {}
    pe = root.find("m:properties", NS)
    if pe is not None:
        for c in pe:
            props[c.tag.split("}")[1]] = (c.text or "").strip()

    def deps(parent):
        out = []
        if parent is None:
            return out
        for d in parent.findall("m:dependency", NS):
            excl = []
            ee = d.find("m:exclusions", NS)
            if ee is not None:
                for e in ee.findall("m:exclusion", NS):
                    excl.append(f"{t(e, 'm:groupId')}:{t(e, 'm:artifactId')}")
            out.append({
                "groupId": t(d, "m:groupId"),
                "artifactId": t(d, "m:artifactId"),
                "version": t(d, "m:version"),
                "scope": t(d, "m:scope"),
                "type": t(d, "m:type"),
                "classifier": t(d, "m:classifier"),
                "optional": t(d, "m:optional") == "true",
                "exclusions": excl,
            })
        return out

    dm = root.find("m:dependencyManagement/m:dependencies", NS)
    return {
        "path": os.path.dirname(path),
        "groupId": t(root, "m:groupId") or (t(p, "m:groupId") if p is not None else None),
        "artifactId": t(root, "m:artifactId"),
        "packaging": t(root, "m:packaging") or "jar",
        "parent": t(p, "m:artifactId") if p is not None else None,
        "modules": [m.text.strip() for m in root.findall("m:modules/m:module", NS)],
        "props": props,
        "depMgmt": deps(dm),
        "deps": deps(root.find("m:dependencies", NS)),
        "proc_none": "-proc:none" in ET.tostring(root, encoding="unicode"),
        "uses_processor": "tika-annotation-processor</artifactId>" in
        re.sub(r"\s", "", ET.tostring(root, encoding="unicode")).replace("ns0:", ""),
    }


def main():
    root, out, *wanted = sys.argv[1:]
    poms = {}
    for dp, dn, fn in os.walk(root):
        dn[:] = [d for d in dn if d not in ("target", ".git", "node_modules", "bazel-out")
                 and not d.startswith("bazel-")]
        if "pom.xml" in fn:
            m = parse(os.path.join(dp, "pom.xml"))
            m["dir"] = os.path.relpath(dp, root)
            poms[m["artifactId"]] = m

    def chain(m):
        c = [m]
        while c[-1]["parent"] in poms:
            c.append(poms[c[-1]["parent"]])
        return list(reversed(c))  # root first

    def effective(m):
        props, dm, deps = {}, {}, []
        boms = []
        for a in chain(m):
            props.update(a["props"])
            for d in a["depMgmt"]:
                if d["scope"] == "import":
                    boms.append(d)
                else:
                    dm[(d["groupId"], d["artifactId"], d["classifier"], d["type"])] = d
            deps.extend(a["deps"])
        props.setdefault("project.version", props.get("revision"))
        props["project.groupId"] = "org.apache.tika"

        def interp(s):
            for _ in range(5):
                if s is None or "${" not in s:
                    return s
                s = re.sub(r"\$\{([^}]+)\}", lambda x: props.get(x.group(1), x.group(0)), s)
            return s

        res = {}
        for d in deps:
            g, a = interp(d["groupId"]), interp(d["artifactId"])
            key = (g, a, d["classifier"], d["type"])
            man = dm.get(key) or dm.get((g, a, None, None)) or {}
            r = dict(d)
            r["groupId"], r["artifactId"] = g, a
            r["version"] = interp(d["version"] or man.get("version"))
            r["scope"] = d["scope"] or man.get("scope") or "compile"
            r["exclusions"] = sorted(set(d["exclusions"]) | set(man.get("exclusions", [])))
            r["internal"] = g == "org.apache.tika" and a in poms
            res[(g, a, d["classifier"], d["type"])] = r  # child wins
        return {
            "dir": m["dir"], "artifactId": m["artifactId"], "packaging": m["packaging"],
            "proc_none": m["proc_none"], "uses_processor": m["uses_processor"],
            "deps": list(res.values()),
            "boms": [{"groupId": interp(b["groupId"]), "artifactId": interp(b["artifactId"]),
                      "version": interp(b["version"])} for b in boms],
        }

    bydir = {m["dir"]: m for m in poms.values()}
    todo = [bydir[w.rstrip("/")]["artifactId"] for w in wanted]
    seen = {}
    while todo:
        a = todo.pop()
        if a in seen:
            continue
        e = effective(poms[a])
        seen[a] = e
        for d in e["deps"]:
            if d["internal"] and d["artifactId"] not in seen:
                todo.append(d["artifactId"])
    json.dump(seen, open(out, "w"), indent=1, sort_keys=True)
    print(f"{len(seen)} modules")
    for a in sorted(seen, key=lambda x: seen[x]["dir"]):
        print(" ", seen[a]["dir"], seen[a]["packaging"])


if __name__ == "__main__":
    main()
