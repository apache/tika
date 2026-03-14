#!/usr/bin/env python3
"""
Extract Traditional Chinese paragraphs from a zhwiki XML dump.

Chinese Wikipedia stores articles in their original script (Traditional
or Simplified).  This script extracts paragraphs and keeps only those
that are predominantly Traditional Chinese, tested by Big5 encodability.

Usage:
    python3 extract_zhwiki_traditional.py ~/datasets/zhwiki/zhwiki-latest-pages-articles.xml.bz2 \
        > ~/datasets/zhwiki/sentences_traditional.txt

Output format matches MADLAD convention: linenum\ttext
"""

import bz2
import html
import re
import sys
import xml.etree.ElementTree as ET

# Markup strippers (same as extract_wiki_sentences.py)
_re_tmpl = re.compile(r'\{\{[^{}]*\}\}')
_re_file = re.compile(
    r'\[\[(?:File|Image|檔案|圖像|分類|Category)[^\[\]]*\]\]',
    re.IGNORECASE)
_re_wikilink = re.compile(r'\[\[(?:[^|\[\]]+\|)?([^\[\]]+)\]\]')
_re_extlink = re.compile(r'\[https?://\S+\s*([^\]]*)\]')
_re_html = re.compile(r'<[^>]+>')
_re_heading = re.compile(r'={2,}[^=]+=+')
_re_table = re.compile(r'^[|{].*$', re.MULTILINE)
_re_ws = re.compile(r'[ \t]+')
_re_cjk = re.compile(r'[\u4e00-\u9fff\u3400-\u4dbf]')

# MediaWiki namespace (common version)
NS_CANDIDATES = [
    '{http://www.mediawiki.org/xml/export-0.11/}',
    '{http://www.mediawiki.org/xml/export-0.10/}',
]

MIN_CJK_CHARS = 5
MIN_BIG5_RATIO = 0.85


def strip_markup(text: str) -> str:
    text = html.unescape(text)
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


def is_traditional(text: str) -> bool:
    """Check if text is predominantly Traditional Chinese by testing
    Big5 encodability of CJK characters."""
    cjk_chars = _re_cjk.findall(text)
    if len(cjk_chars) < MIN_CJK_CHARS:
        return False
    encodable = 0
    for ch in cjk_chars:
        try:
            ch.encode('big5hkscs')
            encodable += 1
        except (UnicodeEncodeError, UnicodeDecodeError):
            pass
    return (encodable / len(cjk_chars)) >= MIN_BIG5_RATIO


def iter_articles(path: str):
    """Yield (title, wikitext) for each article."""
    ns = None
    with bz2.open(path, 'rb') as f:
        for event, elem in ET.iterparse(f, events=('start', 'end')):
            if ns is None and event == 'start':
                tag = elem.tag
                for candidate in NS_CANDIDATES:
                    if tag.startswith(candidate):
                        ns = candidate
                        break
                if ns is None:
                    ns = ''

            if not ns:
                continue

            title_tag = f'{ns}title'
            text_tag = f'{ns}text'
            page_tag = f'{ns}page'

            if event == 'end' and elem.tag == title_tag:
                title = elem.text or ''
                elem.clear()
            elif event == 'end' and elem.tag == text_tag:
                wikitext = elem.text or ''
                elem.clear()
                skip_prefixes = ('Wikipedia:', 'Template:', 'Help:',
                                 'Category:', 'Portal:', 'File:',
                                 'MediaWiki:', 'Module:',
                                 '维基百科:', '模板:', '帮助:',
                                 '分類:', 'Wikipedia talk:')
                if title and not any(title.startswith(p)
                                     for p in skip_prefixes):
                    if not wikitext.lstrip().lower().startswith('#redirect') \
                       and not wikitext.lstrip().startswith('#重定向'):
                        yield title, wikitext
                title = None
            elif event == 'end' and elem.tag == page_tag:
                elem.clear()


def main():
    if len(sys.argv) < 2:
        print(f'Usage: {sys.argv[0]} <zhwiki-dump.xml.bz2>',
              file=sys.stderr)
        sys.exit(1)

    path = sys.argv[1]
    count = 0
    articles = 0
    trad_articles = 0

    for title, wikitext in iter_articles(path):
        articles += 1
        if articles % 50000 == 0:
            print(f'# {articles} articles scanned, {trad_articles} '
                  f'Traditional, {count} paragraphs',
                  file=sys.stderr)

        clean = strip_markup(wikitext)

        for para in clean.split('\n'):
            para = para.strip()
            if not para or len(para) < 20:
                continue
            if is_traditional(para):
                count += 1
                print(f"{count}\t{para}")

        # Track if any paragraph was Traditional
        if any(is_traditional(p.strip())
               for p in clean.split('\n')
               if p.strip() and len(p.strip()) >= 20):
            trad_articles += 1

    print(f'\n# {articles} articles, {trad_articles} Traditional, '
          f'{count} paragraphs extracted',
          file=sys.stderr)


if __name__ == '__main__':
    main()
