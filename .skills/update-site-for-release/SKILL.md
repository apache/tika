---
name: update-site-for-release
description: >
  Update/publish the Apache Tika website (tika-site SVN repo) for a release —
  step 17 of the Release Process. Handles the 4.x track (Changes page +
  aggregate javadoc + Antora docs branch) vs the 3.x maintenance track (full
  per-version apt docs + javadoc). Use for "update the site", "publish the
  site for X.Y.Z", "the website part of the release".
---

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

# Update the Tika website for a release

Step 17 ("Update Tika site") of the Release Process
(<https://cwiki.apache.org/confluence/spaces/TIKA/pages/109454070/Release+Process>).
Assumes the release (tag, artifacts, VOTE, dist promotion) is done; covers the
**website** only.

Set these first:
- **`$SITE`** — `tika-site` **SVN** checkout (not git): `src/site/` (sources) +
  `publish/` (generated, SVN-tracked, served; `mvn install` regenerates it and
  auto-runs `svn add --force publish` via antrun).
- **`$SCRATCH`** — release working dir: unzipped src release, `CHANGES-<NEW>.txt`,
  built javadoc.

Scripts: `./scripts/`. Local paths + toolchain (Maven binary, JDKs) live in a
private companion skill under `~/.claude/skills/`.

> **[HUMAN] gates — never do these yourself:** final `svn commit` (outward-facing,
> irreversible), JIRA "release", s.apache.org shortlink, announce emails. Prepare
> everything, show `svn status`, hand off.

---

## 0. Inputs + release track

Two supported tracks since 4.0.0 (2026-08-21): **4.x** is the current line,
**3.x** is the maintenance line. Which track a release is on decides the
process; both tracks are "stable" — there is no preview slot unless a future
5.x preview reintroduces one.

| Input | Example | Notes |
|---|---|---|
| `NEW_VERSION` | `4.0.1` | the release |
| `RELEASE_TRACK` | `4.x` | `4.x` or `3.x` (maintenance) |
| `PREV_TAG` / `NEW_TAG` | `4.0.0-rc1` / `4.0.1` | git tags, for the GitHub contributor query |
| `JIRA fixVersion` | `4.0.1` | **may differ** from the label (pre-releases often use the base version) |
| `CHANGES` file | `$SCRATCH/CHANGES-4.0.1.txt` | notable-changes source |
| src release zip | `tika-<NEW>-src.zip` | javadoc source (both tracks) |
| release date | `2026-08-21` | doap.rdf + news blurb |

Confirm current values in `pom.xml` (repo root — `tika.stable.version` = 4.x
line, `tika.maintenance.version` = 3.x line).

| Step | 4.x track | 3.x maintenance track |
|---|---|---|
| `pom.xml` `<parent><version>` | leave at newest 3.x | → `<NEW>` (must stay a 3.x parent — Java 11 build) |
| `pom.xml` `tika.stable.version` | → `<NEW>` | leave |
| `pom.xml` `tika.maintenance.version` | leave | → `<NEW>` |
| `src/site/apt/<NEW>/` | **only `index.apt`** (Changes) | full 8-file set (scaffold from prev 3.x) |
| `site.xml` entry | sub-menu linking `docs/<X.Y>.x/` pages + Changes + api | full legacy sub-menu, expanded |
| formats.apt | n/a (Antora docs) | regenerate from `tika-app` jar |
| javadoc | `clean install -Pfast` + `javadoc:aggregate` → `publish/<NEW>/api` (step 7) | same |
| Antora docs | new minor → new `docs/<X.Y>.x` branch; patch → republish same branch (step 7) | n/a |
| Download page | automatic | automatic |

doap.rdf, index.apt.vm news, verify, publish are common to both.

URL scheme (decided 2026-08-19): Antora docs are per-minor (`/docs/4.0.x/`);
the apt Changes page and javadoc are per-release (`/4.0.0/`, `/4.0.0/api/`) —
javadocs are exact-version artifacts.

---

## 1. `src/site/pom.xml` versions  [AGENT]

- **4.x:** bump `<tika.stable.version>` to `<NEW>`.
- **3.x:** bump `<tika.maintenance.version>` **and** `<parent><version>` to `<NEW>`.
- `<parent>` supplies build config only (no site content references it since
  4.0.0). It must stay on the newest **3.x** parent: the 4.x parent enforces
  Java 17, but maven-site-plugin 3.4 needs the Java 11 build (step 8).

Download page auto-reads these — no manual edit.

---

## 2. `src/site/site.xml` menu  [AGENT]

Current 4.x + current 3.x expanded; older = `collapse="true"`.

- **4.x, new minor:** new expanded block at the top; links go into the Antora
  tree plus the per-release Changes/api (repoint `docs/<old>.x` → `docs/<new>.x`
  when a new minor supersedes; a patch release only updates the Changes/api
  version numbers in the hrefs):
  ```xml
  <item name="Apache Tika 4.0.0" href="docs/4.0.x/index.html">
    <item name="Documentation Home" href="docs/4.0.x/index.html"/>
    <item name="Using Tika" href="docs/4.0.x/using-tika/index.html"/>
    <item name="Getting Started (Java API)" href="docs/4.0.x/using-tika/java-api/getting-started.html"/>
    <item name="Pipes" href="docs/4.0.x/pipes/index.html"/>
    <item name="Configuration" href="docs/4.0.x/configuration/index.html"/>
    <item name="Migrating to Tika 4.x" href="docs/4.0.x/migration-to-4x/index.html"/>
    <item name="Changes" href="4.0.0/index.html"/>
    <item name="API Documentation" href="4.0.0/api/"/>
  </item>
  ```
- **3.x:** new expanded block above the previous 3.x; add `collapse="true"`
  to the old block:
  ```xml
  <item name="Apache Tika 3.3.2" href="3.3.2/index.html">
    <item name="Getting Started"                href="3.3.2/gettingstarted.html"/>
    <item name="Supported Formats"              href="3.3.2/formats.html"/>
    <item name="Parser API"                     href="3.3.2/parser.html"/>
    <item name="Parser 5min Quick Start Guide"  href="3.3.2/parser_guide.html"/>
    <item name="Content and Language Detection" href="3.3.2/detection.html"/>
    <item name="Configuring Tika"               href="3.3.2/configuring.html"/>
    <item name="Usage Examples"                 href="3.3.2/examples.html"/>
    <item name="API Documentation"              href="3.3.2/api/"/>
  </item>
  ```

---

## 3. Per-version apt docs `src/site/apt/<NEW>/`  [AGENT]

**4.x** — create only `index.apt` (the Changes page; title `Apache Tika <NEW>`;
fill step 4). No other apt files — everything else lives in the Antora docs.

**3.x** — scaffold (these docs are version-string-identical across 3.x):
```bash
./scripts/scaffold-stable-version.sh $SITE 3.3.1 3.3.2
```
Copies+bumps `configuring/detection/examples/parser/parser_guide/gettingstarted.apt`. Then:
- **formats.apt** — two parts: a hand-written top (license, intro, `%{toc}`, ~25
  prose format-family sections) down to the header line
  `Full list of Supported Formats in "standard" artifacts`, then a generated flat
  list below it. `--list-parser-details-apt` regenerates only the flat part. Copy
  prev `formats.apt`, bump versions, replace everything **below** that header:
  ```bash
  java -jar <path>/tika-app-<NEW>.jar --list-parser-details-apt
  ```
  **Do NOT truncate at the `%{toc}` line** — keep the prose sections. (Scaffold
  skips `formats.apt` for this reason.)
- **index.apt** — step 4.



---

## 4. Per-version `index.apt`: notable changes + contributors  [AGENT + HUMAN]

Shape (see `src/site/apt/3.3.1/index.apt`): license+title; "most notable changes…"
bullets; "The following people have contributed…" bullets; "See
{{https://s.apache.org/XXXX}} …".

**Notable changes** — review output, keep only notable items:
```bash
./scripts/extract-tika-issues.py CHANGES-3.3.2.txt out-3.3.2.apt 3.3.2
```
Mirrors CHANGES verbatim; TIKA-####/Github-#### auto-linked; ALL-CAPS headers →
apt sections.

**Contributors** — candidate list, RM curates:
```bash
./scripts/extract-tika-contribs.py 3.3.2 --prev-tag 3.3.1 --tag 3.3.2 > contribs.txt
# beta (fixVersion differs from label):
# ./scripts/extract-tika-contribs.py 4.0.0 --prev-tag 3.3.1 --tag 4.0.0-beta-1
```
Merges JIRA (reporters/assignees/comment authors) + GitHub commit/PR authors
(uses `gh` auth; resolves logins→names; case-insensitive sort; filters bots/AI).
Over-reports drive-by commenters, misses GitHub-issue-only commenters. **[HUMAN]**
prune / normalise / add.

**Shortlink [HUMAN]** — `s.apache.org/XXXX` → the JIRA "issues fixed in <NEW>"
query; needs s.apache.org login. Ask the RM.

---

## 5. `src/site/resources/doap.rdf`  [AGENT]

New `<release>` at the top. Ordering is by **date, not version** (a stable point
release can sit above an older-dated preview — 3.3.2/Jul-16 above 4.0.0-beta-1/Jul-3):
```xml
      <release>
        <Version>
          <name>Apache Tika 3.3.2</name>
          <created>2026-07-21</created>
          <revision>3.3.2</revision>
        </Version>
      </release>
```

---

## 6. Home page `src/site/apt/index.apt.vm`  [AGENT + HUMAN]

1. New **Latest News** block at the top; its CHANGES link uses
   `dist.apache.org/repos/dist/release/...` (live mirror):
   ```
   [21 July 2026: Apache Tika Release]
    Apache Tika 3.3.2 has been released! <one or two sentence summary>.
    Please see the {{{https://dist.apache.org/repos/dist/release/tika/3.3.2/CHANGES-3.3.2.txt}CHANGES.txt}}
    file for the full list of changes in the release and have a look at the download page for more information
    on how to obtain Apache Tika 3.3.2.
   ```
2. **Repoint the superseded release's CHANGES link** (its artifacts get `svn rm`'d
   from the live mirror at release): `dist.apache.org/repos/dist/release/tika/<PREV>/…`
   → `archive.apache.org/dist/tika/<PREV>/…`. **[HUMAN]** confirm which version was
   removed.

---

## 7. Docs / Javadoc

**Javadoc — both tracks [AGENT].** NOT the wiki's `javadoc:aggregate-no-fork` (runs
against `tika-parent`, its relative `<sourcepath>` fails → `No source files for
package org.apache.tika`; wrong goal, not a JDK issue). From the unzipped src
release (its `./mvnw` is broken — use system `mvn`):
```bash
unzip tika-3.3.2-src.zip && cd tika-3.3.2
mvn clean install -Pfast        # ~4 min; module artifacts + full dep classpath
mvn javadoc:aggregate           # FORKING goal (NOT -no-fork)
mkdir -p $SITE/publish/3.3.2
mv target/reports/apidocs $SITE/publish/3.3.2/api
```
Both steps matter: without `install` javadoc dies on `package org.slf4j does not
exist`; the forking `aggregate` (@aggregator) runs once on the root, `-no-fork`
breaks per-`pom`-module. Any modern JDK (11 and 25 verified). (`tika-server`
miredot docs discontinued — skip.)

**4.x — Antora docs [AGENT].** Built from the tika git repo (main checkout),
not the src zip — the playbook pulls every `docs/{0..9}*` branch as a content
source. New minor: create `docs/<X.Y>.x` from the tag (or main), set
`version: '<X.Y>.x'` + `tika-version` attribute in that branch's
`docs/antora.yml`, and make sure main's antora.yml has `prerelease: true`
[HUMAN commits]. Patch: commit doc changes + `tika-version` bump to the
existing branch. Then:
```bash
cd tika-main
./mvnw package -Papache-release -pl :tika-docs -DskipTests
./docs/publish-docs.sh $SITE/publish
```
`publish-docs.sh` copies target/site into `publish/docs/`, flattens URLs, rewrites
the search index (has its own guards). First 4.x publish after the SNAPSHOT era:
`svn rm publish/docs/<old>-SNAPSHOT` (nothing prunes it) and verify
`publish/docs/index.html` redirects to the released line, not a SNAPSHOT.
Full procedure: docs/modules/ROOT/pages/maintainers/site.adoc.

---

## 8. Build + verify  [AGENT]

`tika-site` has no `./mvnw` → system `mvn`. **Build with Java 11** — it pins
maven-site-plugin 3.4 (2014), unreliable on newer JDKs; a Doxia error here means
wrong JDK, not a content problem (separate from step 7's JDK-agnostic javadoc).
```bash
cd "$SITE"
mvn clean install
```
> **ALWAYS `clean install`, never bare `install`** — an incremental build leaves
> `publish/css/` stale → pages render with no CSS/sidebar. Fix is a `clean`
> rebuild, not a CSS edit.

Build auto-copies target/site → `publish/`, strips timestamps, `svn add --force
publish`. Check: new version in the menu; news + download versions right; **pages
styled (CSS + sidebar)**; per-version pages + javadoc/Antora resolve. Preview:
`mvn site:run` → <http://localhost:8080>.

---

## 9. Stage + hand off the commit  [HUMAN]

```bash
cd "$SITE"
svn status
svn add src/site/apt/<NEW>            # + any other new files
# hand to the RM — do NOT run yourself:
# svn commit -m "Update website for <NEW> release."
```

**Big-commit caveat (javadoc):** `publish/<NEW>/api` is ~3,000 files / ~55 MB; a
single commit often **times out / `E000104 Connection reset by peer`** — this is
size, NOT auth (bad password = `Authentication failed`/403, and cached creds won't
re-prompt). Fixes:
- `http-timeout = 1800` in `~/.subversion/servers` `[global]`.
- Else commit the api in chunks, then the rest:
  ```bash
  svn commit --depth=empty publish/<NEW> publish/<NEW>/api \
      publish/<NEW>/api/org publish/<NEW>/api/org/apache \
      publish/<NEW>/api/org/apache/tika -m "<NEW> site: api dir skeleton"
  for d in publish/<NEW>/api/org/apache/tika/*/; do
    svn commit "$d" -m "<NEW> javadoc: $(basename "$d")" || break   # parser/ ~1,300 files
  done
  svn commit publish/<NEW>/api -m "<NEW> javadoc: remaining api files"
  svn commit -m "Update website for <NEW> release."
  ```
- Atomic per invocation → a failed commit rolls back; retry. Locked (`E155004`) →
  `svn cleanup`.

---

## 10. Confirm published + re-kick  [HUMAN]

svnwcsub maps `/www/tika.apache.org ← %(ASF)s/tika/site/publish`: **only a commit
touching `publish/` triggers a republish**, and it publishes the whole tree at
HEAD. Verify (cache-buster hits the origin, not Varnish):
```bash
curl -s -o /dev/null -w "%{http_code}\n" "https://tika.apache.org/<NEW>/index.html?cb=$(date +%s)"
```
Want `200`. Still `404`/old minutes later → the web-node `svn up` choked on the
big commit. **Re-kick** with a trivial whitespace commit to a file **under
`publish/`** (e.g. a blank line in `publish/index.html`):
```bash
svn commit publish/index.html -m "Nudge svnwcsub to republish."
```
Re-fires svnwcsub → `svn up` to HEAD (harmless; next build regenerates it). A
commit outside `publish/` won't trigger. Still stuck ~30 min → ping `#asfinfra`.

---

## 11. Post-site  [HUMAN] (context)

- JIRA: "release" `<NEW>`; move stragglers to the next version.
- Announce to `user@`, `dev@`, `announce@apache.org`.
- Log at <https://reporter.apache.org/addrelease.html?tika>.
- CVEs fixed → update `security.apt` / per-version security notes, republish.

---

## Checklist

- [ ] 4.x vs 3.x track decided
- [ ] `pom.xml` versions (stable for 4.x; maintenance+parent for 3.x)
- [ ] `site.xml`: new entry added, previous same-track entry collapsed
- [ ] per-version apt docs (`index.apt` only for 4.x / full set for 3.x)
- [ ] `formats.apt` regenerated (3.x only)
- [ ] `index.apt`: notable changes + curated contributors + shortlink
- [ ] `doap.rdf` entry
- [ ] `index.apt.vm`: news block + superseded CHANGES link → archive
- [ ] javadoc → `publish/<NEW>/api` (both tracks); Antora branch published (4.x)
- [ ] `mvn clean install` (never bare `install`); pages styled (CSS + sidebar)
- [ ] `svn status`/`svn add` done, commit handed to RM (chunk `api/` if it resets)
- [ ] live site 200: `https://tika.apache.org/<NEW>/index.html?cb=…` — else re-kick

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `aggregate-no-fork` → `No source files for package org.apache.tika` | runs against `tika-parent`; relative `<sourcepath>` can't resolve | use forking `javadoc:aggregate` after `clean install -Pfast` (step 7) |
| javadoc → `package org.slf4j does not exist` etc. | aggregate without a prior build → empty classpath | `mvn clean install -Pfast` first |
| pages unstyled (no CSS/sidebar) | incremental `install` left `publish/css/` stale | `mvn clean install` (never bare `install`) |
| site-plugin / Doxia error on `mvn install` | maven-site-plugin 3.4 on too-new a JDK | build with **Java 11** |
| notable-changes bullet split on a version number | old numeric heuristic (removed) | use the bundled script; re-run |
| contributors have bots/AI, or surname order | old behavior (fixed): filter + `str.casefold` sort | use bundled `extract-tika-contribs.py`; RM curates |
| commit `E175012 timed out` / `E000104 Connection reset` | ~55 MB api tree too big for one transaction | `http-timeout=1800`; chunk the `api/` (step 9). Size, not auth. |
| commit `Authentication failed` / 403 | genuinely bad/expired credential | `svn commit --username <you>` to re-cache |
| `svn: E155004 working copy locked` | prior commit died mid-transaction | `svn cleanup`, retry |
| committed, site still old even with `?cb=` (origin 404s/old) | web-node `svn up` choked on the big commit | re-kick: whitespace commit under `publish/` (step 10); stuck ~30 min → `#asfinfra` |
| home-page CHANGES link 404s for the previous release | it was `svn rm`'d from the live dist mirror | repoint to `archive.apache.org/dist/tika/<prev>/CHANGES-<prev>.txt` (step 6) |
