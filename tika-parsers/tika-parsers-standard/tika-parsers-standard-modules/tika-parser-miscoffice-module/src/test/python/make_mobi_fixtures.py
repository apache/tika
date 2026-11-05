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
"""Builds the small Mobipocket/KF8 fixtures for MobiParserTest: PDB container, record 0
with PalmDOC + MOBI header + EXTH, PalmDOC- or HUFF/CDIC-compressed text records with
trailing entries, image records (cover JPEG, thumbnail PNG, inline GIF), FLIS/FCIS/EOF
records, and a hybrid of a version 6 and a version 8 book.

Usage: make_mobi_fixtures.py <output directory>. Needs Pillow (pip install Pillow) for the
images; nothing is downloaded. The committed fixtures were written with Pillow 13.0.0; another
Pillow may encode the images differently, which changes the image bytes but nothing a test
looks at."""
import collections, io, os, re, struct, sys
from PIL import Image

def u16(v): return struct.pack('>H', v)
def u32(v): return struct.pack('>I', v & 0xffffffff)

def palmdoc_compress(data):
    out = bytearray(); i = 0; n = len(data)
    while i < n:
        best_len = 0; best_dist = 0
        start = max(0, i - 2047)
        for j in range(start, i):
            l = 0
            while l < 10 and i + l < n and data[j + l] == data[i + l]: l += 1
            if l > best_len: best_len, best_dist = l, i - j
        if best_len >= 3:
            pair = 0x8000 | (best_dist << 3) | (best_len - 3)
            out += u16(pair); i += best_len; continue
        c = data[i]
        if c == 0x20 and i + 1 < n and 0x40 <= data[i + 1] <= 0x7f:
            out.append(data[i + 1] | 0x80); i += 2; continue
        if c == 0 or 0x09 <= c <= 0x7f:
            out.append(c); i += 1; continue
        run = bytearray()
        while i < n and len(run) < 8 and (1 <= data[i] <= 8 or data[i] >= 0x80):
            run.append(data[i]); i += 1
        out.append(len(run)); out += run
    return bytes(out)

def palmdoc_decompress(data):
    out = bytearray(); i = 0
    while i < len(data):
        c = data[i]; i += 1
        if c == 0: out.append(0)
        elif c <= 8: out += data[i:i+c]; i += c
        elif c <= 0x7f: out.append(c)
        elif c >= 0xc0: out += b' '; out.append(c ^ 0x80)
        else:
            c = (c << 8) | data[i]; i += 1
            dist = (c >> 3) & 0x7ff; ln = (c & 7) + 3
            for _ in range(ln): out.append(out[-dist])
    return bytes(out)

# ---- HUFF/CDIC: canonical Huffman over a phrase dictionary; the record layout is known from the
# ---- open-source readers of the format, there is no specification
def huff_encode(text):
    """Returns (huff_record, [cdic_records], stream, phrase_sequence, codes): the two record kinds, a
    function that encodes a phrase sequence to bytes, the text as phrases, and the code table. The text is split into
    phrases (words with their trailing space); the most frequent get 8-bit codes, the rest longer
    ones, so no code is shorter than a byte and zero padding can never form a code. One phrase is
    stored compressed (flag 0): its slice is itself a code stream, which exercises the recursion."""
    words = re.findall(rb'[^ ]+ ?', text)
    freq = collections.Counter(words)
    phrases = [w for w, _ in freq.most_common()]
    # a compound phrase made of two existing phrases, stored as a compressed slice
    compound = phrases[0] + phrases[1]
    n = len(phrases) + 1
    # code lengths: the 16 most frequent phrases get 8 bits, the rest 10, the compound 12
    lengths = {}
    k8 = min(len(phrases), 16)
    for i, w in enumerate(phrases):
        lengths[w] = 8 if i < k8 else 10
    lengths[compound] = 12
    assert sum(2.0 ** -l for l in lengths.values()) <= 1.0, "Kraft"
    # dictionary order: grouped by length, canonical codes descending with the index inside a group
    order = sorted(lengths, key=lambda w: (lengths[w], phrases.index(w) if w in phrases else n))
    index = {w: i for i, w in enumerate(order)}
    first_code = {}; code = 0; prev_len = 0
    by_len = collections.defaultdict(list)
    for w in order: by_len[lengths[w]].append(w)
    for L in sorted(by_len):
        code <<= (L - prev_len); first_code[L] = code; code += len(by_len[L]); prev_len = L
    # The reader settles a code's length by climbing while the window is below that length's
    # smallest code, so longer codes have to be numerically smaller than shorter ones: the
    # canonical codes are complemented, which keeps them prefix-free and turns the order round.
    codes = {}; base = {}; smallest = {}
    for L, ws in by_len.items():
        top = (1 << L) - 1
        for k, w in enumerate(ws): codes[w] = (top - (first_code[L] + k), L)
        smallest[L] = top - (first_code[L] + len(ws) - 1)
        base[L] = (top - first_code[L]) + index[ws[0]]  # index base: index = base - code
        assert all(base[L] - codes[w][0] == index[w] for w in ws)
    dict1 = []
    for p in range(256):
        entry = None
        for L in sorted(by_len):
            lo_code, hi_code = smallest[L], smallest[L] + len(by_len[L]) - 1
            if L <= 8:
                c = p >> (8 - L)
                if lo_code <= c <= hi_code:
                    entry = L | 0x80 | (base[L] << 8); break
            else:
                # prefix of a longer code: the shortest length that can start with it
                lo = p << (L - 8); hi = lo + (1 << (L - 8)) - 1
                if hi >= lo_code and lo <= hi_code:
                    entry = L; break
        # a prefix no code starts with: a settled 8-bit code whose index lies outside the
        # dictionary, so a stream that reaches it is refused, and the padding of a record
        # (fewer than 8 bits) can never complete it
        dict1.append(entry if entry is not None else 8 | 0x80 | (0xffffff << 8))
    dict2 = []
    for L in range(1, 33):
        if L in by_len:
            dict2 += [smallest[L], base[L]]
        else:
            # no code of this length: the smallest code is set above every window that can
            # reach the climb, so the reader climbs past the length instead of stopping at it
            dict2 += [(1 << L) - 1, 0]
    huff = b'HUFF' + u32(0x18) + u32(0x18) + u32(0x18 + 256 * 4) + u32(0) + u32(0) + b''.join(u32(v) for v in dict1) + b''.join(u32(v) for v in dict2)
    def stream(seq):
        bits = ''.join(format(codes[w][0], '0%db' % codes[w][1]) for w in seq)
        bits += '0' * (-len(bits) % 8)
        return int(bits, 2).to_bytes(len(bits) // 8, 'big') if bits else b''
    slices = []
    for w in order:
        if w == compound: slices.append((stream([phrases[0], phrases[1]]), 0))
        else: slices.append((w, 1))
    bits = 4
    cdics = []
    for start in range(0, n, 1 << bits):
        chunk = slices[start:start + (1 << bits)]
        offs = []; body = b''
        for data, flag in chunk:
            offs.append(len(body)); body += u16(len(data) | (0x8000 if flag else 0)) + data
        offs = [o + 2 * len(chunk) for o in offs]
        cdics.append(b'CDIC' + u32(0x10) + u32(n) + u32(bits) + b''.join(u16(o) for o in offs) + body)
    # the text as phrases, using the compound where it occurs
    seq = []; i = 0
    while i < len(words):
        if i + 1 < len(words) and words[i] == phrases[0] and words[i + 1] == phrases[1]:
            seq.append(compound); i += 2
        else:
            seq.append(words[i]); i += 1
    return huff, cdics, stream, seq, codes

def huff_roundtrip(codes, stream_bytes, expected):
    """Checks a code stream against the encoder's own code table: read codes most significant
    bit first, look each one up, and compare the phrases with what was encoded. This is the
    encoder's inverse, not a reader of the HUFF/CDIC records."""
    by_code = {(c, L): w for w, (c, L) in codes.items()}
    bits = ''.join(format(b, '08b') for b in stream_bytes)
    pos = 0; out = []
    while pos < len(bits):
        for L in range(8, 13):
            if pos + L <= len(bits) and (int(bits[pos:pos + L], 2), L) in by_code:
                out.append(by_code[(int(bits[pos:pos + L], 2), L)]); pos += L; break
        else:
            assert set(bits[pos:]) <= {'0'} and len(bits) - pos < 8, 'bits left over'
            break
    assert out == expected, 'roundtrip'

def image(fmt, size, color):
    buf = io.BytesIO(); Image.new('RGB', size, color).save(buf, fmt); return buf.getvalue()

def exth(records):
    body = b''.join(u32(t) + u32(len(d) + 8) + d for t, d in records)
    length = 12 + len(body); pad = (-length) % 4
    return b'EXTH' + u32(length + pad) + u32(len(records)) + body + b'\0' * pad

def record0(text, text_records, compression, encryption, codepage, version, exth_records,
            full_name, first_image, trailing_flags, nrecords, huff=(0, 0), locale=0x409, fdst=None):
    """Record 0. The record index fields count from this record, also for the second
    header of a hybrid, as real hybrids have them; `nrecords` counts from here too.
    `fdst` is the index of a KF8 book's FDST record."""
    kf8 = version >= 8
    header_len = 264 if kf8 else 232
    h = bytearray()
    h += u16(compression) + u16(0) + u32(len(text)) + u16(len(text_records)) + u16(4096) + u16(encryption) + u16(0)
    h += b'MOBI' + u32(header_len) + u32(2) + u32(codepage) + u32(0x1234) + u32(version)
    h += u32(0xffffffff) * 10                       # 40..79 index records
    h += u32(1 + len(text_records))                 # 80 first non-book record
    name_offset_pos = len(h); h += u32(0) + u32(len(full_name))  # 84, 88 filled below
    h += u32(locale) + u32(0) + u32(0)              # 92 locale, 96, 100
    h += u32(version)                               # 104 min version
    h += u32(first_image)                           # 108 first image index
    h += u32(huff[0]) + u32(huff[1]) + u32(0) + u32(0)  # 112 huff record, 116 huff count, 120, 124
    h += u32(0x50)                                  # 128 EXTH flags
    h += b'\0' * 32                                 # 132..163
    h += u32(0xffffffff) + u32(0xffffffff) + u32(0) + u32(0) + u32(0)  # 164 unknown, 168 drm offset, 172 count, 176 size, 180 flags
    h += b'\0' * 8                                  # 184..191
    if kf8:
        h += u32(0xffffffff if fdst is None else fdst) + u32(1)  # 192 FDST record, 196 flow count
    else:
        h += u16(1) + u16(nrecords - 1) + u32(1)    # 192 first/last content, 196
    h += u32(nrecords - 2) + u32(1) + u32(nrecords - 3) + u32(1)  # 200 FCIS, 204, 208 FLIS, 212 (tail is FLIS, FCIS, EOF)
    h += b'\0' * 8 + u32(0xffffffff) + u32(0) + u32(0xffffffff) + u32(0xffffffff)  # 216..239
    h += u16(0) + u16(trailing_flags)               # 240 extra record data flags (u16 at 242)
    h += u32(0xffffffff)                            # 244 INDX
    if kf8:
        h += u32(0xffffffff) * 4                    # 248 fragment, 252 skeleton, 256 datp, 260 guide
        h += u32(0xffffffff) * 4                    # 264..279
    assert len(h) == 16 + header_len, (len(h), header_len)
    h += exth(exth_records)
    struct.pack_into('>I', h, name_offset_pos, len(h))
    h += full_name + b'\0' * (4 - len(full_name) % 4) + b'\0' * 4
    return bytes(h)

def pdb(name, records):
    n = len(records)
    header = name[:31].ljust(32, b'\0') + u16(0) + u16(0) + u32(0) * 3 + u32(0) + u32(0) + u32(0)
    header += b'BOOKMOBI' + u32(2 * n) + u32(0) + u16(n)
    assert len(header) == 78
    offset = 78 + 8 * n + 2
    table = b''
    for i, r in enumerate(records):
        table += u32(offset) + b'\0' + (2 * i).to_bytes(3, 'big')
        offset += len(r)
    return header + table + b'\0\0' + b''.join(records)

def fdst_record(flows):
    """FDST: the offset of the table (12), the flow count, then start and end per flow."""
    return b'FDST' + u32(12) + u32(len(flows)) + b''.join(u32(a) + u32(b) for a, b in flows)

KF8_CSS = b'.calibre { display: block; margin: 0 } @page { margin-top: 5pt }'

def text_records(text, compression, trailing_flags):
    recs = []
    for i in range(0, len(text), 4096):
        chunk = text[i:i+4096]
        body = palmdoc_compress(chunk) if compression == 2 else chunk
        if compression == 2: assert palmdoc_decompress(body) == chunk
        trailer = b''
        if trailing_flags & 1: trailer += b'\x00'            # multibyte overflow: none
        if trailing_flags & 2: trailer += b'\xaa\xbb\x83'    # TBS entry of 3 bytes incl. its size
        recs.append(body + trailer)
    return recs

NULL = u32(0xffffffff)

def build(path, version, compression=2, encryption=0, codepage=65001, text=None, exth_extra=(), name=b'Test Book',
          cover=(201, u32(0)), huff_broken=False, title=b'The Test Book', language=b'en', locale=0x409):
    trailing_flags = 0x3
    full_name = b'A Test Book for Apache Tika'
    huff_recs = []
    flows = None
    if version >= 8:
        # the text records hold the book's HTML and then its stylesheet, as kindlegen writes them
        flows = [(0, len(text)), (len(text), len(text) + len(KF8_CSS))]
        text = text + KF8_CSS
    if compression == 17480:
        huff_rec, cdics, stream, seq, codes = huff_encode(text)
        # record boundaries fall on phrase boundaries: re-chunk the text by phrases of 4096 max
        chunks = []; cur = []; size = 0
        for w in seq:
            if size + len(w) > 4096: chunks.append(cur); cur = []; size = 0
            cur.append(w); size += len(w)
        chunks.append(cur)
        text_recs = []
        for ch in chunks:
            body = stream(ch)
            huff_roundtrip(codes, body, ch)
            trailer = (b'\x00' if trailing_flags & 1 else b'') + (b'\xaa\xbb\x83' if trailing_flags & 2 else b'')
            text_recs.append(body + trailer)
        huff_recs = [huff_rec] + cdics
        text = b''.join(seq)
    elif encryption == 0 and compression in (1, 2):
        text_recs = text_records(text, compression, trailing_flags)
    else:
        text_recs = [bytes(range(256)) * 4]  # unreadable records stand in for encrypted text
    images = [image('JPEG', (24, 32), (200, 30, 30)), image('PNG', (12, 16), (30, 200, 30)), image('GIF', (8, 8), (30, 30, 200))]
    first_image = 1 + len(text_recs) + len(huff_recs)
    tail = [b'FLIS' + b'\0' * 32, b'FCIS' + b'\0' * 40, b'\xe9\x8e\r\n']
    extra = [fdst_record(flows)] if flows else []
    fdst = 1 + len(text_recs) + len(huff_recs) + len(images) if flows else None
    nrecords = 1 + len(text_recs) + len(huff_recs) + len(images) + len(extra) + len(tail)
    records = ([(503, title)] if title else []) + [(100, b'Ada Example'), (100, b'Bob Example'), (101, b'Tika Press'),
               (103, b'<p>A small Mobipocket book that exercises the parser.</p>'), (104, b'9780000000002'),
               (105, b'Testing'), (105, b'Fixtures'), (106, b'2021-03-04'), (108, b'generator script'),
               (109, b'Apache License 2.0'), (113, b'B000TEST01'), (504, b'B000TEST01')] + \
              ([(524, language)] if language else []) + [cover, (202, u32(1)), (203, u32(0))] + list(exth_extra)
    r0 = record0(text if encryption == 0 else b'x' * 1024, text_recs, compression, encryption, codepage, version,
                 records, full_name, first_image, trailing_flags, nrecords,
                 huff=(1, len(huff_recs)) if huff_broken else (1 + len(text_recs), len(huff_recs)) if huff_recs else (0, 0),
                 locale=locale, fdst=fdst)
    data = pdb(name, [r0] + text_recs + huff_recs + images + extra + tail)
    with open(path, 'wb') as f: f.write(data)
    print(path, len(data), 'bytes', nrecords, 'records')

def build_hybrid(path, text7, text8, name=b'Hybrid Book'):
    """kindlegen's dual file: a version 6 book, the shared images, a BOUNDARY record, then a
    version 8 book whose header counts records from its own record 0, as real hybrids have
    it. EXTH 121 of the first header is the index of the second header."""
    trailing_flags = 0x3
    full_name = b'A Hybrid Test Book'
    recs7 = text_records(text7, 2, trailing_flags)
    flows8 = [(0, len(text8)), (len(text8), len(text8) + len(KF8_CSS))]
    text8 = text8 + KF8_CSS
    recs8 = text_records(text8, 2, trailing_flags)
    images = [image('JPEG', (24, 32), (200, 30, 30)), image('PNG', (12, 16), (30, 200, 30))]
    first_image = 1 + len(recs7)
    kf8_index = first_image + len(images) + 1          # after the BOUNDARY record
    tail = [b'FLIS' + b'\0' * 32, b'FCIS' + b'\0' * 40, b'\xe9\x8e\r\n']
    nrecords = kf8_index + 1 + len(recs8) + 1 + len(tail)
    nrecords8 = 1 + len(recs8) + 1 + len(tail)          # counted from the KF8 record 0
    # no EXTH 524: the language has to come from the header's locale (0x407 = German)
    common = [(503, b'The Hybrid Book'), (100, b'Ada Example'), (201, u32(0)), (202, u32(1))]
    r0_7 = record0(text7, recs7, 2, 0, 1252, 6, common + [(121, u32(kf8_index))], full_name, first_image, trailing_flags, nrecords, locale=0x407)
    r0_8 = record0(text8, recs8, 2, 0, 65001, 8, common + [(129, b'kindle:embed:0001'), (121, NULL)], full_name,
                   first_image, trailing_flags, nrecords8, locale=0x407, fdst=1 + len(recs8))
    data = pdb(name, [r0_7] + recs7 + images + [b'BOUNDARY'] + [r0_8] + recs8 + [fdst_record(flows8)] + tail)
    with open(path, 'wb') as f: f.write(data)
    print(path, len(data), 'bytes', nrecords, 'records, kf8 header at', kf8_index)

out = sys.argv[1]
mobi7 = ('<html><head><guide><reference type="toc" title="Table of Contents" filepos=0000000000 /></guide></head><body>'
         '<h1>Chapter One</h1><p>The caf\xe9 opened at dawn. ' + 'Words of the first chapter repeat here. ' * 120 + '</p>'
         '<mbp:pagebreak/><h1>Chapter Two</h1><p>Second chapter, second record. ' + 'More text to cross the record size. ' * 40 + '</p></body></html>')
build(os.path.join(out, 'testMOBI.mobi'), 6, codepage=1252, text=mobi7.encode('cp1252'))
kf8 = ('<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>Cover</title></head>'
       '<body aid="0"><div class="cover" aid="1"><img src="kindle:embed:0001?mime=image/jpeg"/></div></body></html>'
       '<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>Chapter One</title></head>'
       '<body aid="2"><h1 aid="3">Erstes Kapitel</h1><p aid="4">Zwölf Bögen, grüne Türen. ' + 'Fragment text repeats here. ' * 150 + '</p>'
       '<p aid="5">Second record text with äöü umlauts. ' + 'Again and again. ' * 60 + '</p></body></html>')
build(os.path.join(out, 'testMOBI_kf8.azw3'), 8, text=kf8.encode('utf-8'), exth_extra=[(129, b'kindle:embed:0001'), (125, u32(3))])
build(os.path.join(out, 'testMOBI_stored.mobi'), 6, compression=1, codepage=1252, text=mobi7.encode('cp1252'),
      title=None, language=b'fr', locale=0x407, exth_extra=[(501, b'EBOK')])
build(os.path.join(out, 'testMOBI_encrypted.mobi'), 6, encryption=2, codepage=1252, text=mobi7.encode('cp1252'),
      exth_extra=[(208, b'atv:kin:2:test'), (209, b'\x01\x00\x00\x00\xd0')])
huffbook = ('<html><head></head><body><h1>Chapter One</h1><p>The caf\xe9 opened at dawn and the words of the first chapter '
            'repeat here: ' + 'the dawn the words the dawn ' * 300 + '</p><h1>Chapter Two</h1><p>Second chapter, second record, '
            'with other words. ' + 'quick brown fox ' * 200 + '</p></body></html>')
build(os.path.join(out, 'testMOBI_huffcdic.mobi'), 6, compression=17480, codepage=1252, text=huffbook.encode('cp1252'))
build(os.path.join(out, 'testMOBI_unknown_compression.mobi'), 6, compression=3, codepage=1252, text=mobi7.encode('cp1252'),
      cover=(201, NULL))
build(os.path.join(out, 'testMOBI_huffcdic_broken.mobi'), 6, compression=17480, codepage=1252, text=huffbook.encode('cp1252'),
      huff_broken=True)
build_hybrid(os.path.join(out, 'testMOBI_hybrid.mobi'),
             ('<html><head></head><body><h1>Chapter One</h1><p>The old half, caf\xe9 and all. ' + 'Seven words. ' * 60 + '</p></body></html>').encode('cp1252'),
             ('<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>Eins</title></head>'
              '<body aid="0"><h1 aid="1">Erstes Kapitel</h1><p aid="2">Die neue H\u00e4lfte. ' + 'Acht Worte. ' * 60 + '</p></body></html>').encode('utf-8'))
