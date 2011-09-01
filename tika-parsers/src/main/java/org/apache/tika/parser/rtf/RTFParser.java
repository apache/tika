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
package org.apache.tika.parser.rtf;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import javax.swing.text.StyleContext;
import javax.swing.text.rtf.RTFEditorKit;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TaggedInputStream;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * RTF parser
 */
public class RTFParser extends AbstractParser {

    private static final Set<MediaType> SUPPORTED_TYPES = Collections
            .singleton(MediaType.application("rtf"));

    private static final Pattern F_PATTERN = Pattern.compile("\\\\a?f([0-9]+)");

    private static final Pattern FCHARSET_PATTERN = Pattern
            .compile("\\\\fcharset[0-9]+");

    private static final Pattern ANSICPG_PATTERN = Pattern
            .compile("\\\\ansicpg[0-9]+");

    private static final Pattern DEFAULT_FONT_PATTERN = Pattern.compile("\\\\deff(0-9)+");

    private static final Pattern FONT_FAMILY_PATTERN = Pattern.compile("\\\\f(nil|roman|swiss|modern|script|decor|tech|bidi)");

    private static Map<Integer, String> FONTSET_MAP = new HashMap<Integer, String>();
    static {
        FONTSET_MAP.put(0, "windows-1251"); // ANSI
        // charset 1 is Default
        // charset 2 is Symbol

        FONTSET_MAP.put(77, "MacRoman"); // Mac Roman
        FONTSET_MAP.put(78, "Shift_JIS"); // Mac Shift Jis
        FONTSET_MAP.put(79, "ms949"); // Mac Hangul
        FONTSET_MAP.put(80, "GB2312"); // Mac GB2312
        FONTSET_MAP.put(81, "Big5"); // Mac Big5
        FONTSET_MAP.put(82, "johab"); // Mac Johab (old)
        FONTSET_MAP.put(83, "MacHebrew"); // Mac Hebrew
        FONTSET_MAP.put(84, "MacArabic"); // Mac Arabic
        FONTSET_MAP.put(85, "MacGreek"); // Mac Greek
        FONTSET_MAP.put(86, "MacTurkish"); // Mac Turkish
        FONTSET_MAP.put(87, "MacThai"); // Mac Thai
        FONTSET_MAP.put(88, "cp1250"); // Mac East Europe
        FONTSET_MAP.put(89, "cp1251"); // Mac Russian

        FONTSET_MAP.put(128, "MS932"); // Shift JIS
        FONTSET_MAP.put(129, "ms949"); // Hangul
        FONTSET_MAP.put(130, "ms1361"); // Johab
        FONTSET_MAP.put(134, "ms936"); // GB2312
        FONTSET_MAP.put(136, "ms950"); // Big5
        FONTSET_MAP.put(161, "cp1253"); // Greek
        FONTSET_MAP.put(162, "cp1254"); // Turkish
        FONTSET_MAP.put(163, "cp1258"); // Vietnamese
        FONTSET_MAP.put(177, "cp1255"); // Hebrew
        FONTSET_MAP.put(178, "cp1256"); // Arabic
        // FONTSET_MAP.put( 179, "" ); // Arabic Traditional
        // FONTSET_MAP.put( 180, "" ); // Arabic user
        // FONTSET_MAP.put( 181, "" ); // Hebrew user
        FONTSET_MAP.put(186, "cp1257"); // Baltic

        FONTSET_MAP.put(204, "cp1251"); // Russian
        FONTSET_MAP.put(222, "ms874"); // Thai
        FONTSET_MAP.put(238, "cp1250"); // Eastern European
        FONTSET_MAP.put(254, "cp437"); // PC 437
        FONTSET_MAP.put(255, "cp850"); // OEM
    }

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        TaggedInputStream tagged = new TaggedInputStream(stream);
        TemporaryResources tmp = new TemporaryResources();
        try {
            File tempFile = tmp.createTemporaryFile();
            createUnicodeRtfTempFile(tempFile, stream);

            InputStream in = TikaInputStream.get(tempFile);
            try {
                Document sd = new CustomStyledDocument();
                new RTFEditorKit().read(in, sd, 0);

                XHTMLContentHandler xhtml =
                    new XHTMLContentHandler(handler, metadata);
                xhtml.startDocument();
                xhtml.element("p", sd.getText(0, sd.getLength()));
                xhtml.endDocument();
                
                // TODO Extract some of the metadata
                metadata.add(Metadata.CONTENT_TYPE, "application/rtf");
            } finally {
                in.close();
            }
        } catch (IOException e) {
            tagged.throwIfCauseOf(e);
            throw new TikaException("Error parsing an RTF document", e);
        } catch (BadLocationException e) {
            throw new TikaException("Error parsing an RTF document", e);
        } catch (NullPointerException e) {
            // TIKA-621: RTF parsing fails with Java 7 early access
            // on 64bit platforms
            throw new TikaException("Error parsing an RTF document", e);
        } finally {
            tmp.dispose();
        }
    }

    private String escapeByUnicode(String data, String enc) {
        StringBuilder dataBuf = new StringBuilder(data.length() + 16);
        StringBuilder keywordBuf = new StringBuilder(4);
        StringBuilder origDataBuf = new StringBuilder();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int i = 0; i < data.length(); i++) {
            char c1 = data.charAt(i);
            keywordBuf.append(c1);
            if (c1 == '\\' && data.length()>i+1) {
                i++;
                char c2 = data.charAt(i);
                keywordBuf.append(c2);
                if (c2 == '\'') {
                    i++;
                    char c3 = data.charAt(i);
                    keywordBuf.append(c3);
                    if ((c3 >= '0' && c3 <= '9') || (c3 >= 'a' && c3 <= 'f')
                            || (c3 >= 'A' && c3 <= 'F')) {
                        i++;
                        char c4 = data.charAt(i);
                        keywordBuf.append(c4);
                        if ((c4 >= '0' && c4 <= '9')
                                || (c4 >= 'a' && c4 <= 'f')
                                || (c4 >= 'A' && c4 <= 'F')) {
                            int value = Integer.parseInt(
                                    String.valueOf(new char[] { c3, c4 }), 16);
                            baos.write(value);
                            origDataBuf.append(keywordBuf.toString());
                            keywordBuf.delete(0, 4);
                            continue;
                        }
                    }
                }
            }
            if (baos.size() != 0) {
                try {
                    appendUnicodeStr(dataBuf, new String(baos.toByteArray(),
                            enc));
                } catch (UnsupportedEncodingException e) {
                    dataBuf.append(origDataBuf.toString());
                }
                origDataBuf.delete(0, origDataBuf.length());
                baos.reset();
            }
            dataBuf.append(keywordBuf.toString());
            keywordBuf.delete(0, 4);
        }

        if (baos.size() != 0) {
            try {
                appendUnicodeStr(dataBuf, new String(baos.toByteArray(), enc));
            } catch (UnsupportedEncodingException e) {
                dataBuf.append(origDataBuf.toString());
            }
        }

        return dataBuf.toString();
    }

    private void appendUnicodeStr(StringBuilder dataBuf, String value) {
        for (int j = 0; j < value.length(); j++) {
            char ch = value.charAt(j);
            if (ch >= 20 && ch < 80) {
                dataBuf.append(ch);
            } else {
                dataBuf.append("{\\u");
                dataBuf.append((int) ch);
                dataBuf.append('}');
            }
        }
    }

    private void createUnicodeRtfTempFile(File tempFile, InputStream in)
            throws IOException {
        OutputStream out =
            new BufferedOutputStream(new FileOutputStream(tempFile));
        try {
            String defaultCharset = "windows-1251"; // ansi
            String defaultFont = "0";
            Map<String, String> fontTableMap = new HashMap<String, String>();
            StringBuilder dataBuf = new StringBuilder(255);
            int ch;
            LinkedList<String> charsetQueue = new LinkedList<String>();
            int depth = 0;
            String prevFt = null;
            int prevCh = -1;
            while ((ch = in.read()) != -1) {
                if ( ((ch == '{' || ch == '}') && prevCh!='\\') || ( ch == ' ' && (! FONT_FAMILY_PATTERN.matcher(dataBuf.toString()).find())) ) {
                    if (charsetQueue.size() > depth + 1) {
                        charsetQueue.removeLast();
                    }

                    String data = dataBuf.toString();
                    data = data.replace("\\cell","\\u0020\\cell");

                    if(data.indexOf("\\colortbl")!=-1){
                        // End of font table, clear last/previous font encountered.
                        prevFt = null;
                    }

                    if (depth == 1) {
                        // check control words for a default charset
                        String cset = loadAnsiCpg(data);
                        if (cset != null) {
                            defaultCharset = cset;
                        }
                        Matcher matcher = DEFAULT_FONT_PATTERN.matcher(data);
                        if(matcher.find()){
                            defaultFont = matcher.group(1);
                        }
                    }

                    String ft = loadFontTable(data);
                    String charset = loadCharset(data);
                    if (ft != null && charset != null) {
                        fontTableMap.put(ft, charset);
                    }

                    if (ft == null && prevCh == ' ') {
                        ft = prevFt;
                    } else if (ft != null) {
                        prevFt = ft;
                    }
                    if(ft==null){
                        ft = defaultFont;
                    }

                    // set a current charset
                    if (charset == null && ft != null) {
                        charset = fontTableMap.get(ft);
                    }
                    if (charset == null && charsetQueue.size() > 0) {
                        charset = charsetQueue.getLast();
                    }
                    if (charset == null) {
                        charset = defaultCharset;
                    }

                    // add the current charset to a queue
                    if (charsetQueue.size() < depth + 1) {
                        charsetQueue.add(charset);
                    }

                    String escapedStr = "windows-1251".equals(charset) ? data
                            : escapeByUnicode(data, charset);
                    out.write(escapedStr.getBytes("UTF-8"));
                    out.write(ch);
                    dataBuf.delete(0, dataBuf.length());

                    prevCh = ch;

                    // update a depth
                    if (ch == '{') {
                        depth++;
                    } else if (ch == '}') {
                        depth--;
                    }
                } else {
                    dataBuf.append((char) ch);
                }
            }
        } finally {
            out.close();
        }
    }

    private String loadFontTable(String line) {
        Matcher m = F_PATTERN.matcher(line);
        String font = null;
        while((m.find())) {
            font = m.group(1);
        }
        return font;
    }

    private String loadAnsiCpg(String line) {
        Matcher m = ANSICPG_PATTERN.matcher(line);
        String charset = null;
        if (m.find()) {
            int encVal;
            try {
                encVal = Integer.parseInt(m.group().substring(8));
                charset = FONTSET_MAP.get(encVal);
            } catch (NumberFormatException e) {
                // ignore
            }
        }

        return charset;
    }

    private String loadCharset(String line) {
        Matcher m = FCHARSET_PATTERN.matcher(line);
        String charset = null;
        if (m.find()) {
            int encVal;
            try {
                encVal = Integer.parseInt(m.group().substring(9));
            } catch (NumberFormatException e) {
                encVal = 0;
            }
            charset = FONTSET_MAP.get(encVal);
        }

        return charset;
    }

    /**
     * Customized version of {@link DefaultStyledDocument}. Adds whitespace
     * to places where words otherwise could have run together (see
     * <a href="https://issues.apache.org/jira/browse/TIKA-392">TIKA-392</a>),
     * and works around the problem of Swing expecting a GUI environment (see
     * <a href="https://issues.apache.org/jira/browse/TIKA-282">TIKA-282</a>).
     */
    private static class CustomStyledDocument extends DefaultStyledDocument {
        private boolean isPrevUnicode = false;

        public CustomStyledDocument() {
            super(new NoReclaimStyleContext());
        }

        @Override
        public void insertString(int offs, String str, AttributeSet a)
                throws BadLocationException {
            boolean isUnicode = str.length() == 1 && str.charAt(0) > 127;

            if (offs > 0 && offs == getLength() && !isPrevUnicode && !isUnicode) {
                super.insertString(offs, " ", a);
                super.insertString(getLength(), str, a);
            } else {
                super.insertString(offs, str, a);
            }

            isPrevUnicode = isUnicode;
        }

    }

    /**
     * A workaround to
     * <a href="https://issues.apache.org/jira/browse/TIKA-282">TIKA-282</a>:
     * RTF parser expects a GUI environment. This class simply disables the
     * troublesome SwingUtilities.isEventDispatchThread() call that's made in
     * the {@link StyleContext#reclaim(AttributeSet)} method.
     */
    private static class NoReclaimStyleContext extends StyleContext {

        /** Ignored. */
        public void reclaim(AttributeSet a) {
        }

    }

}
