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
package org.apache.tika.parser.wordperfect;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections4.MapUtils;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

/**
 * Extracts WordPerfect Document Area text from a WordPerfect document
 * version 5.x.
 * @author Pascal Essiembre
 */
class WP5DocumentAreaExtractor extends WPDocumentAreaExtractor {
    
    /* 192-207 characters represent fixed-length multi-byte functions.  
     * Those that are not handled explicitely in the code below should be
     * skipped according to their size (minus the first char if already read).
     */
    private static final Map<Integer, Integer> FIXED_LENGTH_FUNCTION_SIZES = 
            MapUtils.putAll(new HashMap<Integer, Integer>(), new Integer[] {
        192, 4,  // Extended character
        193, 9,  // Center/Align/ Tab/Left Margin Release
        194, 11, // Indent
        195, 3,  // Attribute ON
        196, 3,  // Attribute OFF
        197, 5,  // Block Protect
        198, 6,  // End of Indent
        199, 7,  // Different Display Character when Hyphenated
        200, 4,  // (Reserved)
        201, 5,  // (Reserved)
        202, 6,  // (Reserved)
        203, 6,  // (Reserved)
        204, 8,  // (Reserved)
        205, 10, // (Reserved)
        206, 10, // (Reserved)
        207, 12, // (Reserved)
    });
    
    protected void extract(int c, WPInputStream in, StringBuilder out, 
            XHTMLContentHandler xhtml) throws IOException, SAXException {

        // 0-31: control characters
        if (c == 10) {
            endParagraph(out, xhtml);// hard return ("Enter")
        } else if (c == 11) {
            out.append(' ');      // soft page break
        } else if (c == 12) {
            endParagraph(out, xhtml);// hard page break
        } else if (c == 13) {
            out.append(' ');      // soft return (line wrap)
            
        // 32-126: ASCII characters
        } else if (c >= 32 && c <= 126) {
            out.append((char) c); // ASCII character

        // 128-191: single-byte functions
        } else if (c == 140) {
            endParagraph(out, xhtml);// combination hard return/soft page (WP5.1)
        } else if (c >= 144 && c <= 149) {
            out.append(' ');      // deletable/invisible soft return/page
        } else if (c == 153) {
            endParagraph(out, xhtml);// Dormant Hard return (WP5.1)
        } else if (c == 160) {
            out.append('\u00A0'); // Hard space
        } else if (c >= 169 && c <= 171) {
            out.append('-');      // Hard hyphen
            
        // 192-207: fixed-length multi-byte function
        } else if (c == 192) {
            // extended char
            int charval = in.readWP();
            int charset = in.readWP();
            in.readWP(); // closing character
            WP5Charsets.append(out, charset, charval);
        } else if (c >= 193 && c <= 207) {
            // removing 1 from function length since first char already read
            in.skipWPByte(FIXED_LENGTH_FUNCTION_SIZES.get(c) - 1);
            
        // 208-255: variable-length multi-byte function
        } else if (c >= 208 && c <= 255) {
            // Variable-Length Multi-Byte Functions
            in.readWP(); // subgroup (the function code)
            int functionSize = in.readWPShort();
            for (int i = 0; i < functionSize; i++) {
                in.readWP();
            }
            //TODO Are there functions containing data? Like footnotes?
        }
    }
}
