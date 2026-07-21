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
Turn a CHANGES-X.Y.Z.txt release section into the apt "notable changes" list.

Python 3 port of Chris Mattmann's extract-tika-issues.py, originally from:
  https://github.com/chrismattmann/apachestuff/blob/master/extract-tika-issues.py
Updates over the original: py2 -> py3; dropped the numeric-list heuristic that
mangled version numbers ("4.x", "12.0.36"); recognise "  BREAKING CHANGES" /
"  NEW FEATURES" section headers; join wrapped continuation lines; guard the
common arg-order swap.

Usage:
  extract-tika-issues.py CHANGES_FILE OUTPUT_FILE RELEASE_VERSION

  CHANGES_FILE     path to CHANGES-X.Y.Z.txt (input)
  OUTPUT_FILE      path to write the apt fragment (output; will be overwritten)
  RELEASE_VERSION  e.g. 3.3.2  -- must match a "Release X.Y.Z - MM/dd/yyyy" header

Example:
  ./extract-tika-issues.py CHANGES-3.3.2.txt out-3.3.2.apt 3.3.2

NOTE: the output is a STARTING POINT, not final copy. The numeric heuristic
mis-splits sentences that contain version numbers (e.g. "Jetty 11 -> 12.0.36"
becomes a bogus bullet). Hand-edit the result before pasting it into index.apt,
and keep only the genuinely notable items -- this is not meant to be the whole
changelog.
"""
import re
import sys

USAGE = ("Usage: extract-tika-issues.py CHANGES_FILE OUTPUT_FILE RELEASE_VERSION\n"
         "  e.g. extract-tika-issues.py CHANGES-3.3.2.txt out-3.3.2.apt 3.3.2")


def looks_like_version(s):
    return re.fullmatch(r"\d+\.\d+\.\d+(?:[-.].+)?", s.strip()) is not None


def main():
    if len(sys.argv) != 4:
        print(USAGE, file=sys.stderr)
        return 2

    changesFile, outputFile, relVersion = sys.argv[1], sys.argv[2], sys.argv[3]

    # Guard the classic arg-order slip (OUTPUT and VERSION swapped), which
    # silently writes a file literally named after the output path.
    if looks_like_version(outputFile) and not looks_like_version(relVersion):
        print(f"ERROR: argument 2 ('{outputFile}') looks like a version and argument 3 "
              f"('{relVersion}') does not.\n"
              f"Arguments are: CHANGES_FILE OUTPUT_FILE RELEASE_VERSION -- did you swap "
              f"the last two?\n{USAGE}", file=sys.stderr)
        return 2

    versionChangeLog = {}
    version = None

    # Tika CHANGES.txt grammar:
    #   "Release X.Y.Z - M/D/YYYY"  version header
    #   "  * text ..."              a changelog entry (may wrap over indented lines)
    #   "  BREAKING CHANGES"        an all-caps section header (optional; some releases)
    #   blank line                  separates entries
    release_re = re.compile(r".*\d{1,2}/\d{1,2}/\d{4}.*")
    bullet_re = re.compile(r"^\s*\*\s+(.*)")
    # All-caps line = section header. Restricting to A-Z and spaces (no digits,
    # slashes, or lowercase) keeps wrapped prose from being mistaken for a header.
    # NOTE: the old numeric-list heuristic was removed -- it mangled entries
    # containing version numbers ("Port the 4.x SAX-based parsers..." -> "x SAX...").
    section_re = re.compile(r"^[A-Z][A-Z ]{2,38}$")
    SECTION_MARKER = "__SECTION__:"

    with open(changesFile, mode="r", encoding="utf-8") as cf:
        lines = cf.readlines()

    versionIssues = []
    issueTxt = ""
    for line in lines:
        stripped = line.strip()

        if release_re.match(line.rstrip()):
            if issueTxt:
                versionIssues.append(issueTxt)
                issueTxt = ""
            if version is not None:
                versionChangeLog[version] = versionIssues
                versionIssues = []
            version = line.rsplit(" - ", 1)[0].strip()
            continue
        if version is None or not stripped:            # preamble or entry separator
            continue
        if "notable changes" in line or set(stripped) <= {"-"}:
            continue
        m = bullet_re.match(line)
        if m:                                          # new changelog entry
            if issueTxt:
                versionIssues.append(issueTxt)
            issueTxt = m.group(1).strip()
        elif section_re.match(stripped):               # section header
            if issueTxt:
                versionIssues.append(issueTxt)
                issueTxt = ""
            versionIssues.append(SECTION_MARKER + stripped.title())
        else:                                          # continuation of current entry
            issueTxt = (issueTxt + " " + stripped) if issueTxt else stripped

    if issueTxt:
        versionIssues.append(issueTxt)
    if version is not None:
        versionChangeLog[version] = versionIssues

    key = "Release " + relVersion
    if key not in versionChangeLog:
        print(f"ERROR: no '{key}' section found in {changesFile}.\n"
              f"Sections present: {sorted(versionChangeLog)}", file=sys.stderr)
        return 1

    with open(outputFile, "w", encoding="utf-8") as of:
        of.write("Apache Tika " + relVersion + "\n\n")
        of.write("\t The most notable changes in Tika " + relVersion +
                 " over the previous release are:\n\n")
        for issue in versionChangeLog[key]:
            if issue.startswith(SECTION_MARKER):
                of.write("* " + issue[len(SECTION_MARKER):] + "\n\n")
                continue
            out = re.sub(r"TIKA-(\d+)",
                         r"{{{http://issues.apache.org/jira/browse/TIKA-\1}TIKA-\1}}", issue)
            out = re.sub(r"Github-(\d+)",
                         r"{{{http://github.com/apache/tika/pull/\1}Github-\1}}", out, flags=re.I)
            of.write("\t * " + out + "\n\n")

    print(f"Wrote {outputFile} for release {relVersion} "
          f"({len(versionChangeLog[key])} entries). Review & hand-edit before pasting.",
          file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
