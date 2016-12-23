/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
 * version 6+.
 * @author Pascal Essiembre
 */
class WP6DocumentAreaExtractor extends WPDocumentAreaExtractor {
    
    protected void extract(int c, WPInputStream in, StringBuilder out)
            throws IOException {
        if (c > 0 && c <= 32) {
            out.append(WP6Charsets.DEFAULT_EXTENDED_INTL_CHARS[c]);
        } else if (c >= 33 && c <= 126) {
            out.append((char) c);
        } else if (c == 128) {
            out.append(' ');      // Soft space
        } else if (c == 129) {
            out.append('\u00A0'); // Hard space
        } else if (c == 129) {
            out.append('-');      // Hard hyphen
        } else if (c == 135 || c == 137) {
            out.append('\n');      // Dormant Hard return
        } else if (c == 138) {
            // skip to closing pair surrounding page number
            skipUntilChar(in, 139);
        } else if (c == 198) {
            // end of cell
            out.append('\t');
        } else if (c >= 180 && c <= 207) {
            out.append('\n');
        } else if (c >= 208 && c <= 239) {
            // Variable-Length Multi-Byte Functions
            int subgroup = in.readWP();
            int functionSize = in.readWPShort();
            for (int i = 0; i < functionSize - 4; i++) {
                in.readWP();
            }
            
            // End-of-Line group
            if (c == 208) {
                if (subgroup >= 1 && subgroup <= 3) {
                    out.append(' ');
                } else if (subgroup == 10) {
                    // end of cell
                    out.append('\t');
                } else if (subgroup >= 4 && subgroup <= 19) {
                    out.append('\n');
                } else if (subgroup >= 20 && subgroup <= 22) {
                    out.append(' ');
                } else if (subgroup >= 23 && subgroup <= 28) {
                    out.append('\n');
                }
            } else if (c == 213) {
                out.append(' ');
            } else if (c == 224) {
                out.append('\t');
            }
            //TODO Are there functions containing data? Like footnotes?
            
        } else if (c == 240) {
            // extended char
            int charval = in.readWP();
            int charset = in.readWP();
            in.readWP(); // closing character
            WP6Charsets.append(out, charset, charval);
        } else if (c >= 241 && c <= 254) {
            skipUntilChar(in, c);
        } else if (c == 255) {
            skipUntilChar(in, c);
        }
        
        // Ignored codes above 127:
        
        // 130,131,133: soft hyphens
        // 134: invisible return in line
        // 136: soft end of center/align
        // 140: style separator mark
        // 141,142: start/end of text to skip
        // 143: exited hyphenation
        // 144: cancel hyphenation
        // 145-151: match functions
        // 152-179: unknown/ignored
        // 255: reserved, cannot be used
    }
}
