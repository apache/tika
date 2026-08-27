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
Emit a CANDIDATE Apache-Tika contributor list (apt bullet format) for a release,
merging two sources:

  1. JIRA  -- reporters, assignees, and comment authors on every issue in the
     fix version. (Reimplements Chris Mattmann's original `extract-tika-contribs`
     bash script, from
     https://github.com/chrismattmann/apachestuff/blob/master/extract-tika-contribs
     which scraped the JIRA printable HTML via `grep ViewProfile | cut | sort |
     uniq` behind a `tika` shell alias. This version uses the JIRA REST + GitHub
     compare APIs instead and adds GitHub PR authors, login->name resolution,
     bot/AI filtering, and a case-insensitive sort.)

  2. GitHub -- commit authors and Co-authored-by trailers between the previous
     release tag and the new one. Increasingly contributors go straight to
     GitHub and never touch JIRA, so this catches PR authors the JIRA query
     alone misses.

  STILL A CANDIDATE, not the final list. It over-reports (drive-by commenters,
  bots) and can under-report -- e.g. someone whose only contribution was a
  comment on a GitHub *issue* (not a PR) appears in neither source. The tika-site
  3.3.1 index.apt lists "Chengxin Xu", who is absent from both JIRA fixVersion
  3.3.1 and the 3.3.0..3.3.1 git range. The release manager MUST review the
  output, prune noise, normalise names, and add anyone missing.

Usage:
  extract-tika-contribs.py FIX_VERSION [--prev-tag T] [--tag T]
                           [--repo apache/tika] [--project TIKA]
                           [--no-github] [--include-bots]

  FIX_VERSION            JIRA fixVersion. NOT always the release label -- for a
                        beta, issues are often tagged with the base version
                        (4.0.0-beta-1 contributors came from fixVersion "4.0.0").
  --prev-tag / --tag    git tags bounding this release, e.g. --prev-tag 3.3.1
                        --tag 3.3.2. If omitted, the GitHub half is skipped.
  --no-github           JIRA only.

GitHub auth: uses GITHUB_TOKEN / GH_TOKEN if set, else `gh auth token`, else
anonymous (60 req/hr). `gh` is the easiest -- if you can run gh, this just works.

Examples:
  ./extract-tika-contribs.py 3.3.2 --prev-tag 3.3.1 --tag 3.3.2
  ./extract-tika-contribs.py 4.0.0 --prev-tag 3.3.1 --tag 4.0.0-beta-1
"""
import argparse
import json
import re
import subprocess
import sys
import urllib.parse
import urllib.request

JIRA = "https://issues.apache.org/jira"
GITHUB = "https://api.github.com"

# Automated / non-human accounts (JIRA display names or GitHub author names).
# Matched case-insensitively as substrings.
BOT_DENYLIST = [
    "asf github bot", "asf subversion and git services", "hudson", "jenkins",
    "gridgain integration", "jira bot", "githubbot",
    "dependabot", "[bot]", "copilot", "github-actions",
    # AI coding assistants that show up as commit/co-authors. Kept specific so a
    # real person named e.g. "Claude Warren" is NOT filtered.
    "claude sonnet", "claude opus", "claude haiku", "claude code",
    "chatgpt", "openai", "gpt-4", "gpt-5", "cursor agent", "devin ai",
]

# Co-author email domains that identify AI assistants regardless of display name.
AI_EMAIL_DOMAINS = ["anthropic.com", "openai.com"]


def is_bot(name):
    low = name.lower()
    return any(bot in low for bot in BOT_DENYLIST)


def _get_json(url, headers):
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.load(resp)


# ---------------------------------------------------------------- JIRA ----
def collect_jira(fix_version, project):
    jql = f'project = {project} AND fixVersion = "{fix_version}"'
    names, start_at, total, issue_count = set(), 0, None, 0
    while total is None or start_at < total:
        params = urllib.parse.urlencode({
            "jql": jql, "fields": "reporter,assignee,comment",
            "startAt": start_at, "maxResults": 100,
        })
        data = _get_json(f"{JIRA}/rest/api/2/search?{params}", {"Accept": "application/json"})
        total = data.get("total", 0)
        issues = data.get("issues", [])
        if not issues:
            break
        for issue in issues:
            issue_count += 1
            f = issue.get("fields", {})
            for who in (f.get("reporter"), f.get("assignee")):
                if who and who.get("displayName"):
                    names.add(who["displayName"].strip())
            for c in (f.get("comment") or {}).get("comments", []):
                a = c.get("author") or {}
                if a.get("displayName"):
                    names.add(a["displayName"].strip())
        start_at += len(issues)
    return names, issue_count, total or 0


# -------------------------------------------------------------- GitHub ----
def _github_token():
    import os
    tok = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if tok:
        return tok.strip()
    try:
        out = subprocess.run(["gh", "auth", "token"], capture_output=True, text=True, timeout=15)
        if out.returncode == 0 and out.stdout.strip():
            return out.stdout.strip()
    except (FileNotFoundError, subprocess.SubprocessError):
        pass
    return None


def _resolve_login(login, headers, cache):
    """GitHub login -> display name (falls back to the login). Collapses
    login-vs-real-name duplicates like THausherr / Tilman Hausherr."""
    if login in cache:
        return cache[login]
    name = login
    try:
        data = _get_json(f"{GITHUB}/users/{login}", headers)
        if data.get("name"):
            name = data["name"].strip()
    except Exception:  # noqa: BLE001 - keep the login on any lookup failure
        pass
    cache[login] = name
    return name


def collect_github(repo, prev_tag, tag):
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "update-site-for-release"}
    tok = _github_token()
    if tok:
        headers["Authorization"] = f"Bearer {tok}"
    names, logins, page, total_commits = set(), set(), 1, None
    while True:
        url = f"{GITHUB}/repos/{repo}/compare/{prev_tag}...{tag}?per_page=100&page={page}"
        data = _get_json(url, headers)
        if "commits" not in data:
            raise RuntimeError(data.get("message", "unexpected GitHub response"))
        total_commits = data.get("total_commits", total_commits)
        commits = data["commits"]
        if not commits:
            break
        for c in commits:
            login = (c.get("author") or {}).get("login")
            if login:
                logins.add(login)
            else:
                # No linked GitHub account (rare) -- fall back to the git author name.
                a = c["commit"]["author"]
                if a.get("name"):
                    names.add(a["name"].strip())
            for m in re.finditer(r"Co-authored-by:\s*(.+?)\s*<([^>]*)>", c["commit"]["message"]):
                nm, email = m.group(1).strip(), m.group(2).strip().lower()
                if any(dom in email for dom in AI_EMAIL_DOMAINS):
                    continue
                names.add(nm)
        if len(commits) < 100:
            break
        page += 1
    # Resolve logins to display names so they dedupe against JIRA/co-author names.
    cache = {}
    for login in logins:
        names.add(_resolve_login(login, headers, cache))
    return names, total_commits or 0, bool(tok)


# ---------------------------------------------------------------- main ----
def main():
    ap = argparse.ArgumentParser(description="Candidate Tika contributor list (JIRA + GitHub).")
    ap.add_argument("fix_version")
    ap.add_argument("--prev-tag")
    ap.add_argument("--tag")
    ap.add_argument("--repo", default="apache/tika")
    ap.add_argument("--project", default="TIKA")
    ap.add_argument("--no-github", action="store_true")
    ap.add_argument("--include-bots", action="store_true")
    args = ap.parse_args()

    all_names = set()
    notes = []

    try:
        jira_names, issue_count, total = collect_jira(args.fix_version, args.project)
        all_names |= jira_names
        notes.append(f"JIRA fixVersion={args.fix_version}: {len(jira_names)} name(s) "
                     f"from {issue_count} issue(s).")
    except Exception as e:  # noqa: BLE001
        notes.append(f"JIRA query FAILED: {e}")

    do_github = not args.no_github and args.prev_tag and args.tag
    if do_github:
        try:
            gh_names, ncommits, authed = collect_github(args.repo, args.prev_tag, args.tag)
            all_names |= gh_names
            notes.append(f"GitHub {args.prev_tag}..{args.tag}: {len(gh_names)} "
                         f"name(s) from {ncommits} commit(s) "
                         f"[{'authenticated' if authed else 'anonymous'}].")
        except Exception as e:  # noqa: BLE001
            notes.append(f"GitHub query FAILED: {e}")
    elif not args.no_github:
        notes.append("GitHub half SKIPPED: pass --prev-tag and --tag to include PR authors.")

    if not args.include_bots:
        all_names = {n for n in all_names if not is_bot(n)}

    # Simple case-insensitive alphabetical sort by the full display string
    # (first-name-first, as written) -- the site's historical convention, which
    # came from the old bash script piping through `sort`. NOT a last-name sort.
    for name in sorted(all_names, key=str.casefold):
        print(f"     * {name}")

    print("", file=sys.stderr)
    for n in notes:
        print(f"# {n}", file=sys.stderr)
    print(f"# {len(all_names)} candidate contributor(s) total. CANDIDATE LIST -- review "
          f"before use: prune drive-by/bot names, normalise duplicates (login vs real "
          f"name), and add GitHub-issue commenters not captured by either source.",
          file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
