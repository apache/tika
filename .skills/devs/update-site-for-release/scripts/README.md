<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Helper scripts for `update-site-for-release`

Python 3. No third-party deps (stdlib only). Run from anywhere.

| Script | Purpose | Key gotcha |
|---|---|---|
| `extract-tika-issues.py` | `CHANGES-X.Y.Z.txt` → apt "notable changes" bullet list | Mirrors CHANGES verbatim (bullets + all-caps section headers); review to keep only notable items. Arg order is `CHANGES OUTPUT VERSION` (guarded against the common swap). |
| `extract-tika-contribs.py` | Candidate contributor list from **JIRA + GitHub** | **Candidate only** — RM must curate. `FIX_VERSION` may differ from the release label (betas). Pass `--prev-tag`/`--tag` to include GitHub PR authors. Uses `gh auth token` automatically. |
| `scaffold-stable-version.sh` | Copy previous stable's apt docs → new version, bump version string | STABLE only. Skips `formats.apt`/`index.apt` on purpose (regenerate those). |

## Origin

`extract-tika-issues.py` and the idea behind `extract-tika-contribs.py` come from
Chris Mattmann's [`chrismattmann/apachestuff`](https://github.com/chrismattmann/apachestuff)
repo. The original `extract-tika-contribs` was a bash one-liner that scraped the
JIRA "printable" search HTML (`grep ViewProfile | cut | sort | uniq`) behind a
`tika` shell alias — fragile, capped at 100 rows, and blind to GitHub-only
contributors. This version queries the JIRA REST API and the GitHub compare API
instead. (`apachestuff` also has `name_to_committer_id.py`; the Tika site process
lists contributors by display name and never uses committer ids, so it is
intentionally not bundled here.)

## Examples

```bash
# Notable changes for a stable release
./extract-tika-issues.py /path/to/CHANGES-3.3.2.txt out-3.3.2.apt 3.3.2

# Contributors, stable (JIRA fixVersion == label)
./extract-tika-contribs.py 3.3.2 --prev-tag 3.3.1 --tag 3.3.2

# Contributors, beta (JIRA fixVersion is the base version, tag is the label)
./extract-tika-contribs.py 4.0.0 --prev-tag 3.3.1 --tag 4.0.0-beta-1

# Scaffold the new stable version's apt docs
./scaffold-stable-version.sh /path/to/tika-site 3.3.1 3.3.2
```
