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
package org.apache.tika.parser.microsoft.rtf.jflex;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;

/**
 * Extracts the original HTML from an RTF document that contains encapsulated HTML
 * (as indicated by the {@code \fromhtml1} control word), using a JFlex-based tokenizer
 * and shared {@link RTFState} for font/codepage tracking.
 *
 * <p>Embedded objects and pictures are extracted in the same pass via
 * {@link RTFEmbeddedHandler}.</p>
 */
public class RTFHtmlDecapsulator {

    private static final int DEFAULT_MAX_BYTES_KB = 2 * 1024 * 1024; // 2 GB

    private final RTFEmbeddedHandler embHandler;

    public RTFHtmlDecapsulator(ContentHandler handler, ParseContext context,
                               int maxBytesInKb) {
        this.embHandler = new RTFEmbeddedHandler(handler, context, maxBytesInKb);
    }

    public RTFHtmlDecapsulator(ContentHandler handler, ParseContext context) {
        this(handler, context, DEFAULT_MAX_BYTES_KB);
    }

    public String extract(byte[] rtfBytes) throws IOException, SAXException, TikaException {
        if (rtfBytes == null || rtfBytes.length == 0) {
            return null;
        }
        // Wrap byte[] in a Reader directly — RTF is 7-bit ASCII, so
        // US_ASCII decoding is a 1:1 byte-to-char mapping with no
        // intermediate String allocation.
        Reader reader = new InputStreamReader(
                new ByteArrayInputStream(rtfBytes), StandardCharsets.US_ASCII);
        RTFTokenizer tokenizer = new RTFTokenizer(reader);
        RTFState state = new RTFState();
        StringBuilder html = new StringBuilder(rtfBytes.length / 2);
        ByteArrayOutputStream pendingBytes = new ByteArrayOutputStream();

        boolean foundFromHtml = false;
        boolean foundHtmlTag = false;
        boolean inHtmlRtfSkip = false;
        boolean sawIgnorable = false;
        int htmlTagDepth = -1;
        boolean inHtmlTag = false;

        RTFToken tok;
        while ((tok = tokenizer.yylex()) != null) {
            RTFTokenType type = tok.getType();
            if (type == RTFTokenType.EOF) {
                break;
            }

            // Flush pending bytes before charset-changing events
            if (type == RTFTokenType.GROUP_CLOSE
                    || (type == RTFTokenType.CONTROL_WORD && "f".equals(tok.getName())
                        && tok.hasParameter())) {
                flushPendingBytes(pendingBytes, html, state);
            }

            boolean consumed = state.processToken(tok);

            // Embedded handler processes objdata/pict/sp in the same pass
            if (!consumed) {
                RTFGroupState closingGroup =
                        (type == RTFTokenType.GROUP_CLOSE) ? state.getLastClosedGroup() : null;
                try {
                    embHandler.processToken(tok, state, closingGroup);
                } catch (TikaException | IOException e) {
                    // don't let a bad embedded object kill decapsulation
                }
            }

            RTFGroupState group = state.getCurrentGroup();

            // Skip tokens that are part of objdata/pict hex streams
            if (!consumed && (group.objdata || group.pictDepth > 0)) {
                continue;
            }

            switch (type) {
                case GROUP_OPEN:
                    sawIgnorable = false;
                    break;

                case GROUP_CLOSE:
                    if (inHtmlTag && state.getDepth() < htmlTagDepth) {
                        flushPendingBytes(pendingBytes, html, state);
                        inHtmlTag = false;
                        htmlTagDepth = -1;
                    }
                    break;

                case CONTROL_SYMBOL:
                    if (tok.getChar() == '*') {
                        sawIgnorable = true;
                    }
                    if (!foundHtmlTag || inHtmlRtfSkip) {
                        break;
                    }
                    if (inHtmlTag || htmlTagDepth == -1) {
                        char sym = tok.getChar();
                        if (sym == '{' || sym == '}' || sym == '\\') {
                            flushPendingBytes(pendingBytes, html, state);
                            html.append(sym);
                        }
                    }
                    break;

                case CONTROL_WORD:
                    if (consumed) {
                        break;
                    }
                    String name = tok.getName();

                    if ("fromhtml".equals(name)) {
                        foundFromHtml = true;
                        break;
                    }
                    if ("htmltag".equals(name) && sawIgnorable) {
                        if (!foundFromHtml) {
                            break;
                        }
                        foundHtmlTag = true;
                        flushPendingBytes(pendingBytes, html, state);
                        inHtmlTag = true;
                        htmlTagDepth = state.getDepth();
                        break;
                    }
                    if ("htmlrtf".equals(name)) {
                        flushPendingBytes(pendingBytes, html, state);
                        inHtmlRtfSkip = !(tok.hasParameter() && tok.getParameter() == 0);
                        break;
                    }
                    if (!foundHtmlTag || inHtmlRtfSkip) {
                        break;
                    }
                    if (inHtmlTag || htmlTagDepth == -1) {
                        flushPendingBytes(pendingBytes, html, state);
                        switch (name) {
                            case "par":
                            case "pard":
                                html.append('\n');
                                break;
                            case "tab":
                                html.append('\t');
                                break;
                            case "line":
                                html.append("<br>");
                                break;
                            default:
                                break;
                        }
                    }
                    break;

                case HEX_ESCAPE:
                    if (consumed || !foundHtmlTag || inHtmlRtfSkip) {
                        break;
                    }
                    if (inHtmlTag || htmlTagDepth == -1) {
                        pendingBytes.write(tok.getHexValue());
                    }
                    break;

                case UNICODE_ESCAPE:
                    if (!foundHtmlTag || inHtmlRtfSkip) {
                        break;
                    }
                    if (inHtmlTag || htmlTagDepth == -1) {
                        flushPendingBytes(pendingBytes, html, state);
                        int cp = tok.getParameter();
                        if (Character.isValidCodePoint(cp)) {
                            html.appendCodePoint(cp);
                        }
                    }
                    break;

                case TEXT:
                    if (consumed || !foundHtmlTag || inHtmlRtfSkip) {
                        break;
                    }
                    if (inHtmlTag || htmlTagDepth == -1) {
                        flushPendingBytes(pendingBytes, html, state);
                        html.append(tok.getChar());
                    }
                    break;

                default:
                    break;
            }
        }

        flushPendingBytes(pendingBytes, html, state);
        if (!foundFromHtml || html.length() == 0) {
            return null;
        }
        return html.toString();
    }

    private static void flushPendingBytes(ByteArrayOutputStream pending, StringBuilder out,
                                          RTFState state) {
        if (pending.size() > 0) {
            Charset cs = state.getCurrentCharset();
            out.append(new String(pending.toByteArray(), cs));
            pending.reset();
        }
    }
}
