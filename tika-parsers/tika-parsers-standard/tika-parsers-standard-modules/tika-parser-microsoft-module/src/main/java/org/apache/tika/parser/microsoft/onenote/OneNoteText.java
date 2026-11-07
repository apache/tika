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
package org.apache.tika.parser.microsoft.onenote;

import java.nio.charset.Charset;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.sax.XHTMLContentHandler;

/** Text clean-up and markup shared by the .one and FSSHTTPB walkers. */
public final class OneNoteText {

    /** MS-ONE TextExtendedAscii holds code-page bytes; Western text is windows-1252. */
    public static final Charset EXTENDED_ASCII = Charset.forName("windows-1252");

    private OneNoteText() {
    }

    /**
     * @return the text with NUL terminators removed and vertical tabs (OneNote soft line
     * breaks) and CR/CRLF (alt text) as newlines, or {@code null} if nothing but spaces and U+FFFC
     * object-placeholders is left
     */
    public static String clean(String text) {
        if (text == null) {
            return null;
        }
        String s = text.replace("\u0000", "").replace('\u000B', '\n')
                .replace("\r\n", "\n").replace('\r', '\n');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\uFFFC' && !Character.isWhitespace(c) && !Character.isSpaceChar(c)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Emits a picture as {@code <p><img src="embedded:fileName" alt="altText"/></p>}, leaving out
     * whichever is missing.
     *
     * @return whether an element was emitted
     */
    public static boolean emitImage(XHTMLContentHandler xhtml, String fileName, String altText)
            throws SAXException {
        fileName = fileName == null ? null : clean(fileName.strip());
        altText = altText == null ? null : clean(altText.strip());
        if (fileName == null && altText == null) {
            return false;
        }
        AttributesImpl atts = new AttributesImpl();
        if (fileName != null) {
            atts.addAttribute("", "src", "src", "CDATA", "embedded:" + fileName);
        }
        if (altText != null) {
            atts.addAttribute("", "alt", "alt", "CDATA", altText);
        }
        // its own paragraph, so Markdown does not run it into the next inline element
        xhtml.startElement("p");
        xhtml.startElement("img", atts);
        xhtml.endElement("img");
        xhtml.endElement("p");
        return true;
    }

    public static boolean isImageText(OneNotePropertyEnum property) {
        return property == OneNotePropertyEnum.ImageFilename ||
                property == OneNotePropertyEnum.ImageAltText;
    }
}
