#!/usr/bin/env python3
"""
Extract plain-text sentences from a MediaWiki XML dump (.xml.bz2).
Outputs one sentence per line to stdout, suitable for use as charset
training data (same format as sentences_madlad.txt).

Usage:
    python3 extract_wiki_sentences.py <dump.xml.bz2> > sentences.txt
"""

import bz2
import html
import re
import sys
import xml.etree.ElementTree as ET

# ---------------------------------------------------------------------------
# Wiki markup strippers
# ---------------------------------------------------------------------------

# Remove {{templates}} (handles one level of nesting well enough)
_re_tmpl = re.compile(r'\{\{[^{}]*\}\}')
# Remove [[File:...]] / [[Image:...]] blocks
_re_file = re.compile(r'\[\[(?:File|Image|檔案|圖像|分類|Category)[^\[\]]*\]\]', re.IGNORECASE)
# Convert [[link|text]] → text,  [[link]] → link
_re_wikilink = re.compile(r'\[\[(?:[^|\[\]]+\|)?([^\[\]]+)\]\]')
# Remove [http://... optional text] external links → keep text
_re_extlink = re.compile(r'\[https?://\S+\s*([^\]]*)\]')
# Remove HTML tags
_re_html = re.compile(r'<[^>]+>')
# Remove section headers ==Heading==
_re_heading = re.compile(r'={2,}[^=]+=+')
# Remove table markup lines starting with | or {|
_re_table = re.compile(r'^[|{].*$', re.MULTILINE)
# Collapse runs of whitespace
_re_ws = re.compile(r'[ \t]+')


def strip_markup(text: str) -> str:
    text = html.unescape(text)
    # Templates (two passes handles some nesting)
    for _ in range(3):
        text = _re_tmpl.sub(' ', text)
    text = _re_file.sub(' ', text)
    text = _re_wikilink.sub(r'\1', text)
    text = _re_extlink.sub(r'\1', text)
    text = _re_html.sub(' ', text)
    text = _re_heading.sub('\n', text)
    text = _re_table.sub('', text)
    text = _re_ws.sub(' ', text)
    return text


# ---------------------------------------------------------------------------
# Sentence splitter (simple: split on 。！？; keep ≥10 Chinese chars)
# ---------------------------------------------------------------------------

_re_sent_split = re.compile(r'[。！？!?]+')
_re_cjk = re.compile(r'[\u4e00-\u9fff\u3400-\u4dbf]')


def split_sentences(text: str):
    for para in text.split('\n'):
        para = para.strip()
        if not para:
            continue
        for sent in _re_sent_split.split(para):
            sent = sent.strip()
            if len(_re_cjk.findall(sent)) >= 10:
                yield sent


# ---------------------------------------------------------------------------
# XML streaming parser
# ---------------------------------------------------------------------------

NS = '{http://www.mediawiki.org/xml/export-0.11/}'


def iter_articles(path: str):
    """Yield (title, wikitext) for each article in the dump."""
    with bz2.open(path, 'rb') as f:
        title = None
        in_text = False
        buf = []
        for event, elem in ET.iterparse(f, events=('start', 'end')):
            tag = elem.tag
            if event == 'end' and tag == f'{NS}title':
                title = elem.text or ''
                elem.clear()
            elif event == 'end' and tag == f'{NS}text':
                wikitext = elem.text or ''
                elem.clear()
                # Skip redirects and special pages
                if title and not title.startswith(('Wikipedia:', 'Template:', 'Help:',
                                                   'Category:', 'Portal:', 'File:',
                                                   'MediaWiki:', 'Module:')):
                    if not wikitext.lstrip().lower().startswith('#redirect'):
                        yield title, wikitext
                title = None
            elif event == 'end' and tag == f'{NS}page':
                elem.clear()


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    if len(sys.argv) < 2:
        print(f'Usage: {sys.argv[0]} <dump.xml.bz2>', file=sys.stderr)
        sys.exit(1)

    path = sys.argv[1]
    count = 0
    articles = 0
    for title, wikitext in iter_articles(path):
        articles += 1
        clean = strip_markup(wikitext)
        for sent in split_sentences(clean):
            print(sent, flush=False)
            count += 1

    print(f'\n# {articles} articles → {count} sentences', file=sys.stderr)


if __name__ == '__main__':
    main()
