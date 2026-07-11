/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.xmp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.utils.XMLReaderUtils;

/** SAX-flattens an RDF/XML XMP packet to xmpcore-style (uri, path, value) leaves. */
public final class XmpSaxFlattener {

    static final String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    static final String XMLNS = "http://www.w3.org/XML/1998/namespace";
    static final String META = "adobe:ns:meta/";   // x:xmpmeta/x:xapmeta wrapper + x:xmptk

    public List<XmpProperty> flatten(byte[] packet) throws IOException, TikaException, SAXException {
        return flatten(packet, new ParseContext());
    }

    public List<XmpProperty> flatten(byte[] packet, ParseContext context)
            throws IOException, TikaException, SAXException {
        try (InputStream is = new ByteArrayInputStream(packet)) {
            return flatten(is, context);
        }
    }

    public List<XmpProperty> flatten(InputStream packet, ParseContext context)
            throws IOException, TikaException, SAXException {
        Handler h = new Handler();
        // Tika's hardened, pooled path (secure factory + entity-expansion cap +
        // OfflineContentHandler). CloseShield so it can't close a stream we don't own.
        XMLReaderUtils.parseSAX(CloseShieldInputStream.wrap(packet), h, context);
        return h.out;
    }

    private static final class Handler extends DefaultHandler {
        // Cap the leaf list so a hostile packet (millions of elements) can't inflate metadata.
        static final int MAX_LEAVES = 50_000;
        final List<XmpProperty> out = new ArrayList<>();
        final ArrayDeque<String> segs = new ArrayDeque<>();
        final ArrayDeque<String> uris = new ArrayDeque<>();     // leaf/property namespace per frame
        final ArrayDeque<int[]> liCount = new ArrayDeque<>();
        final ArrayDeque<StringBuilder> text = new ArrayDeque<>();
        final ArrayDeque<int[]> childCount = new ArrayDeque<>();
        final ArrayDeque<String[]> langs = new ArrayDeque<>();  // xml:lang per frame (holder so it can be set after push)
        final ArrayDeque<boolean[]> hasValue = new ArrayDeque<>();   // frame carries an explicit rdf:value

        static boolean isContainer(String u, String l) {
            return RDF.equals(u) && (l.equals("Bag") || l.equals("Seq") || l.equals("Alt"));
        }

        void add(XmpProperty p) {
            if (out.size() < MAX_LEAVES) {
                out.add(p);
            }
        }

        String path() {
            StringBuilder p = new StringBuilder();
            Iterator<String> it = segs.descendingIterator();
            while (it.hasNext()) {
                String s = it.next();
                if (s.startsWith("[")) {
                    p.append(s);
                } else {
                    if (p.length() > 0) {
                        p.append('/');
                    }
                    p.append(s);
                }
            }
            return p.toString();
        }

        void pushFrame(String seg, String uri) {
            if (!childCount.isEmpty()) {
                childCount.peek()[0]++;
            }
            segs.push(seg);
            uris.push(uri == null ? "" : uri);
            text.push(new StringBuilder());
            childCount.push(new int[]{0});
            langs.push(new String[]{null});
            hasValue.push(new boolean[]{false});
        }

        @Override
        public void startElement(String u, String l, String qn, Attributes a) {
            if (META.equals(u)) {
                return;
            }
            boolean rdf = RDF.equals(u);
            if (rdf && l.equals("RDF")) {
                return;
            }
            if (rdf && l.equals("Description")) {
                emitAttrs(a, false);
                return;
            }
            if (isContainer(u, l)) {
                liCount.push(new int[]{0});
                return;
            }
            if (rdf && l.equals("li")) {
                // a bare rdf:li outside a Bag/Seq/Alt (malformed) has no counter -> treat as [1]
                int idx = liCount.isEmpty() ? 1 : ++liCount.peek()[0];
                pushFrame("[" + idx + "]", uris.isEmpty() ? "" : uris.peek());
            } else if (rdf && l.equals("value")) {
                // rdf:value holds the frame's value even when qualifier siblings are present
                if (!hasValue.isEmpty()) {
                    hasValue.peek()[0] = true;
                }
                return;
            } else if (!rdf) {
                pushFrame(qn, u);
            } else {
                return;
            }
            emitAttrs(a, true);
        }

        void emitAttrs(Attributes a, boolean valueFrame) {
            String base = path();
            for (int i = 0; i < a.getLength(); i++) {
                String au = a.getURI(i);
                String al = a.getLocalName(i);
                String aq = a.getQName(i);
                String v = a.getValue(i);
                if (v == null) {
                    continue;
                }
                v = v.trim();
                if (v.isEmpty()) {
                    continue;
                }
                if (XMLNS.equals(au)) {
                    if (al.equals("lang")) {
                        if (valueFrame) {
                            langs.peek()[0] = v;   // attach to this value's frame
                        }
                        if (!base.isEmpty()) {
                            add(new XmpProperty(XMLNS, base + "/xml:lang", v));
                        }
                    }
                    continue;
                }
                if (RDF.equals(au)) {
                    if (al.equals("resource")) {
                        add(new XmpProperty(uris.isEmpty() ? "" : uris.peek(), base, v));
                    }
                    continue;
                }
                if (au == null || au.isEmpty()) {
                    continue;
                }
                add(new XmpProperty(au, base.isEmpty() ? aq : base + "/" + aq, v));
            }
        }

        @Override
        public void characters(char[] c, int s, int len) {
            if (!text.isEmpty()) {
                text.peek().append(c, s, len);
            }
        }

        @Override
        public void endElement(String u, String l, String qn) {
            if (META.equals(u)) {
                return;
            }
            boolean rdf = RDF.equals(u);
            if (rdf && l.equals("RDF")) {
                return;
            }
            if (rdf && l.equals("Description")) {
                return;
            }
            if (isContainer(u, l)) {
                liCount.pop();
                return;
            }
            if (rdf && !l.equals("li")) {
                return;
            }
            String t = text.peek().toString().trim();
            // a frame is a leaf when it has no child elements, or when an rdf:value gave it a value
            if (!t.isEmpty() && (childCount.peek()[0] == 0 || hasValue.peek()[0])) {
                add(new XmpProperty(uris.peek(), path(), t, langs.peek()[0]));
            }
            segs.pop();
            uris.pop();
            text.pop();
            childCount.pop();
            langs.pop();
            hasValue.pop();
        }
    }
}
