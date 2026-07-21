---
name: update-site-for-release
description: >
  Update/publish the Apache Tika website (tika-site SVN repo) for a release —
  step 17 of the Release Process. Handles STABLE (full per-version docs +
  javadoc) vs PREVIEW/beta (Changes page + Antora docs). Use for "update the
  site", "publish the site for X.Y.Z", "the website part of the release".
---

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

## 0. Inputs + release type

| Input | Example | Notes |
|---|---|---|
| `NEW_VERSION` | `3.3.2` | the release |
| `RELEASE_TYPE` | `stable` | `stable` or `preview` (alpha/beta/BETA) |
| `PREV_STABLE` / `PREV_PREVIEW` | `3.3.1` / `4.0.0-beta-1` | current `tika.stable.version` / `tika.preview.version` |
| `PREV_TAG` / `NEW_TAG` | `3.3.1` / `3.3.2` | git tags, for the GitHub contributor query |
| `JIRA fixVersion` | `3.3.2` | **may differ** from the label (betas often use the base version, e.g. `4.0.0`) |
| `CHANGES` file | `$SCRATCH/CHANGES-3.3.2.txt` | notable-changes source |
| src release zip | `tika-<NEW>-src.zip` | **stable only** (javadoc) |
| release date | `2026-07-21` | doap.rdf + news blurb |

Confirm current values in `src/site/pom.xml`.

| Step | STABLE | PREVIEW / beta |
|---|---|---|
| `pom.xml` `<parent><version>` | → `<NEW>` | leave at stable |
| `pom.xml` `tika.stable.version` | → `<NEW>` | leave |
| `pom.xml` `tika.preview.version` | leave | → `<NEW>` |
| `src/site/apt/<NEW>/` | full 8-file set (scaffold from prev) | **only `index.apt`** |
| `site.xml` entry | full sub-menu, expanded | minimal `Changes` item under `docs/<major>-SNAPSHOT` |
| formats.apt | regenerate from `tika-app` jar | n/a (Antora docs) |
| javadoc | `clean install -Pfast` + `javadoc:aggregate` → `publish/<NEW>/api` (step 7) | n/a |
| Antora docs | n/a | `mvn package -pl docs` + `docs/publish-docs.sh` |
| Download page | automatic | automatic |

doap.rdf, index.apt.vm news, verify, publish are common to both.

---

## 1. `src/site/pom.xml` versions  [AGENT]

- **STABLE:** bump `<parent><version>` **and** `<tika.stable.version>` to `<NEW>`.
  (parent version drives `${project.parent.version}` home-page links → must be
  newest stable.)
- **PREVIEW:** bump only `<tika.preview.version>`; leave parent + stable.

Download page auto-reads these — no manual edit.

---

## 2. `src/site/site.xml` menu  [AGENT]

Current stable + current preview expanded; older = `collapse="true"`.

- **STABLE:** new expanded block above the previous stable; add `collapse="true"`
  to the old stable block:
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
- **PREVIEW:** minimal block above the old preview (collapse it); `href` → Antora
  snapshot, not an apt page:
  ```xml
  <item name="Apache Tika 4.0.0-beta-2" href="docs/4.0.0-SNAPSHOT">
    <item name="Changes" href="4.0.0-beta-2/index.html"/>
  </item>
  ```

---

## 3. Per-version apt docs `src/site/apt/<NEW>/`  [AGENT]

**STABLE** — scaffold (these docs are version-string-identical across 3.x):
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

**PREVIEW** — create only `index.apt` (copy prev preview's; title `Apache Tika
<NEW>`; fill step 4). No other files.

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

**STABLE — javadoc [AGENT].** NOT the wiki's `javadoc:aggregate-no-fork` (runs
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

**PREVIEW (4.x) — Antora docs [AGENT]:**
```bash
cd tika-<NEW>            # unzipped src release
./mvnw package -pl docs
./docs/publish-docs.sh $SITE/publish
```
`publish-docs.sh` copies target/site into `publish/docs/`, flattens URLs, rewrites
the search index (has its own guards).

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

**Big-commit caveat (stable):** `publish/<NEW>/api` is ~3,000 files / ~55 MB; a
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

- [ ] stable vs preview decided
- [ ] `pom.xml` versions (parent+stable for stable; preview only for preview)
- [ ] `site.xml`: new entry added, previous same-track entry collapsed
- [ ] per-version apt docs (full set for stable / `index.apt` only for preview)
- [ ] `formats.apt` regenerated (stable)
- [ ] `index.apt`: notable changes + curated contributors + shortlink
- [ ] `doap.rdf` entry
- [ ] `index.apt.vm`: news block + superseded CHANGES link → archive
- [ ] javadoc → `publish/<NEW>/api` (stable) / Antora docs (preview)
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
