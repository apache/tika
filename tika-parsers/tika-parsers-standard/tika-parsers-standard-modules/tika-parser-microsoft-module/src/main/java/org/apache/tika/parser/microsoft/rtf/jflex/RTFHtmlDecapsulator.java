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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
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

    private static final int DEFAULT_MEMORY_LIMIT_KB = 20 * 1024; // 20 MB

    private final ContentHandler handler;
    private final ParseContext context;
    private final int memoryLimitInKb;

    /**
     * Creates a decapsulator that extracts embedded objects through the given handler.
     *
     * @param handler the content handler for embedded document extraction
     * @param context the parse context (provides EmbeddedDocumentExtractor, etc.)
     * @param memoryLimitInKb max bytes per embedded object (in KB), or -1 for unlimited
     */
    public RTFHtmlDecapsulator(ContentHandler handler, ParseContext context,
                               int memoryLimitInKb) {
        this.handler = handler;
        this.context = context;
        this.memoryLimitInKb = memoryLimitInKb;
    }

    /**
     * Creates a decapsulator with default memory limit and no embedded extraction.
     */
    public RTFHtmlDecapsulator() {
        this(null, null, DEFAULT_MEMORY_LIMIT_KB);
    }

    /**
     * Extracts the HTML content from an encapsulated-HTML RTF document.
     * Embedded objects and pictures are extracted as a side effect through
     * the {@link ContentHandler} provided at construction time.
     *
     * @param rtfBytes the decompressed RTF bytes
     * @return the extracted HTML string, or {@code null} if the RTF does not contain
     *         encapsulated HTML
     * @throws IOException if the tokenizer encounters an I/O error
     */
    public String extract(byte[] rtfBytes) throws IOException, SAXException, TikaException {
        if (rtfBytes == null || rtfBytes.length == 0) {
            return null;
        }

        String rtf = new String(rtfBytes, StandardCharsets.US_ASCII);

        RTFTokenizer tokenizer = new RTFTokenizer(new StringReader(rtf));
        RTFState state = new RTFState();
        RTFEmbeddedHandler embHandler = (handler != null && context != null)
                ? new RTFEmbeddedHandler(handler, context, memoryLimitInKb)
                : null;

        StringBuilder html = new StringBuilder(rtf.length() / 2);
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

            // Let RTFState handle group stack, font table, codepage, unicode skip
            boolean consumed = state.processToken(tok);

            // Let embedded handler process objdata/pict/sp in the same pass
            if (embHandler != null && !consumed) {
                RTFGroupState closingGroup =
                        (type == RTFTokenType.GROUP_CLOSE) ? state.getLastClosedGroup() : null;
                try {
                    embHandler.processToken(tok, state, closingGroup);
                } catch (TikaException | IOException e) {
                    // record and continue — don't let a bad embedded object kill decapsulation
                }
            }

            RTFGroupState group = state.getCurrentGroup();

            // Skip tokens that are part of objdata/pict hex streams
            if (!consumed && (group.objdata || group.pictDepth > 0)) {
                // Embedded handler already consumed these
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
                    if ("*".equals(tok.getName())) {
                        sawIgnorable = true;
                    }
                    if (!foundHtmlTag || inHtmlRtfSkip) {
                        break;
                    }
                    if (inHtmlTag || isContentArea(htmlTagDepth)) {
                        String sym = tok.getName();
                        if ("{".equals(sym) || "}".equals(sym) || "\\".equals(sym)) {
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

                    if (inHtmlTag || isContentArea(htmlTagDepth)) {
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
                    if (consumed) {
                        break;
                    }
                    if (!foundHtmlTag || inHtmlRtfSkip) {
                        break;
                    }
                    if (inHtmlTag || isContentArea(htmlTagDepth)) {
                        pendingBytes.write(tok.getHexValue());
                    }
                    break;

                case UNICODE_ESCAPE:
                    if (!foundHtmlTag || inHtmlRtfSkip) {
                        break;
                    }
                    if (inHtmlTag || isContentArea(htmlTagDepth)) {
                        flushPendingBytes(pendingBytes, html, state);
                        int cp = tok.getParameter();
                        if (Character.isValidCodePoint(cp)) {
                            html.appendCodePoint(cp);
                        }
                    }
                    break;

                case TEXT:
                    if (consumed) {
                        break;
                    }
                    if (!foundHtmlTag || inHtmlRtfSkip) {
                        break;
                    }
                    if (inHtmlTag || isContentArea(htmlTagDepth)) {
                        flushPendingBytes(pendingBytes, html, state);
                        html.append(tok.getName());
                    }
                    break;

                case CRLF:
                case BIN:
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

    private static boolean isContentArea(int htmlTagDepth) {
        return htmlTagDepth == -1;
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
