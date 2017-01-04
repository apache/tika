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

/**
 * Extracts WordPerfect Document Area text from a WordPerfect document
 * version 5.x.
 * @author Pascal Essiembre
 */
class WP5DocumentAreaExtractor extends WPDocumentAreaExtractor {
    
    protected void extract(int c, WPInputStream in, StringBuilder out)
            throws IOException {

        // 0-31: control characters
        if (c == 10) {
            out.append('\n');     // hard return ("Enter")
        } else if (c == 11) {
            out.append(' ');      // soft page break
        } else if (c == 12) {
            out.append('\n');     // hard page break
        } else if (c == 13) {
            out.append(' ');      // soft return (line wrap)
            
        // 32-126: ASCII characters
        } else if (c >= 32 && c <= 126) {
            out.append((char) c); // ASCII character

        // 128-191: single-byte functions
        } else if (c == 140) {
            out.append('\n');     // combination hard return/soft page (WP5.1)
        } else if (c >= 144 && c <= 149) {
            out.append(' ');      // deletable/invisible soft return/page
        } else if (c == 153) {
            out.append('\4');     // Dormant Hard return (WP5.1)
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
            skipUntilChar(in, c); // opening/closing chars are same

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
