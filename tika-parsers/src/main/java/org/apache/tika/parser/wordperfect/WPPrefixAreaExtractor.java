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
 * Extracts WordPerfect Prefix Area data from a WordPerfect document.
 * Applies to both 5.x and 6+ documents.
 * @author Pascal Essiembre
 */
final class WPPrefixAreaExtractor {

    private WPPrefixAreaExtractor() {
        super();
    }

    //WP5.x:
    //  Prefix Area:            16 bytes (standard header)
    
    //WP6.x:
    //  Prefix Area:            30 bytes (16 standard header + 14 index header)
    
    public static WPPrefixArea extract(WPInputStream in) 
            throws IOException {
        WPPrefixArea header = new WPPrefixArea();

        in.mark(30);
        header.setFileId(in.readWPString(4));         // 1-4
        header.setDocAreaPointer(in.readWPLong());    // 5-8
        header.setProductType(in.readWP());           // 9
        header.setFileType(in.readWPChar());          // 10
        header.setMajorVersion(in.readWP());          // 11
        header.setMinorVersion(in.readWP());          // 12
        header.setEncrypted(in.readWPShort() != 0);   // 13-14
        header.setIndexAreaPointer(in.readWPShort()); // 15-16
        try {
            in.skip(4); // 4 reserved bytes: skip     // 17-20
            header.setFileSize(in.readWPLong());      // 21-24
        } catch (IOException e) {
            // May fail if no index header, which is fine.
        }
        in.reset();

        return header;
    }
}
