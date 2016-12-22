/* Copyright 2015-2016 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
import java.io.InputStream;

import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.UnsupportedFormatException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.WordPerfect;
import org.apache.tika.mime.MediaType;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

/**
 * Extracts text from a WordPerfect document according to WP6 File Format.
 * This format appears to be compatible with more recent versions too.
 * @author Pascal Essiembre
 */
class WP6TextExtractor {

    public void extract(
            InputStream input, XHTMLContentHandler xhtml, Metadata metadata) 
            throws IOException, SAXException, TikaException {
        WPInputStream in = new WPInputStream(input);
        
        WP6FileHeader header = parseFileHeader(in);

        applyMetadata(header, metadata);

        if (header.getMajorVersion() == 0) {
            MediaType version = WordPerfectParser.WP_UNK;
            if (header.getMinorVersion() == 0) {
                version = WordPerfectParser.WP_5_0;
            } else if (header.getMinorVersion() == 1) {
                version = WordPerfectParser.WP_5_1;
            }
            metadata.set(Metadata.CONTENT_TYPE, version.toString());
            throw new UnsupportedFormatException("Parser doesn't support this version:"+version.toString());
        }

        if (header.getMajorVersion() != 2) {
            metadata.set(Metadata.CONTENT_TYPE, WordPerfectParser.WP_UNK.toString());
            throw new UnsupportedFormatException("Parser doesn't recognize this version");
        }

        if (header.isEncrypted()) {
            throw new EncryptedDocumentException();
        }

        // For text extraction we can safely ignore WP Index Area and
        // Packet Data Area and jump right away to Document Area.
        extractDocumentText(in, header.getDocAreaPointer(), xhtml);
        
    }

    private void applyMetadata(WP6FileHeader header, Metadata metadata) {
        metadata.set(WordPerfect.FILE_SIZE,
                Long.toString(header.getFileSize()));
        metadata.set(WordPerfect.FILE_ID, header.getFileId());
        metadata.set(WordPerfect.PRODUCT_TYPE, header.getProductType());
        metadata.set(WordPerfect.FILE_TYPE, header.getFileType());
        metadata.set(WordPerfect.MAJOR_VERSION, header.getMajorVersion());
        metadata.set(WordPerfect.MINOR_VERSION, header.getMinorVersion());
        metadata.set(WordPerfect.ENCRYPTED, 
                Boolean.toString(header.isEncrypted()));
    }
        
    private void extractDocumentText(
            WPInputStream in, long offset, XHTMLContentHandler xhtml) 
                    throws IOException, SAXException {
        xhtml.startElement("p");
        
        // Move to offset (for some reason skip() did not work).
        for (int i = 0; i < offset; i++) {
            in.readWPByte();
        }

        int chunk = 4096;
        StringBuilder out = new StringBuilder(chunk);
        
        int c;
        while ((c = in.read()) != -1) {
            if (c > 0 && c <= 32) {
                out.append(WP6Constants.DEFAULT_EXTENDED_INTL_CHARS[c]);
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
  
                //TODO implement all charsets
                if (charset == 4 || charset == 5) {
                    out.append(
                            WP6Constants.EXTENDED_CHARSETS[charset][charval]);
                } else {
                    out.append("[TODO:charset" + charset + "]");
                }
            } else if (c >= 241 && c <= 254) {
                skipUntilChar(in, c);
            } else if (c == 255) {
                skipUntilChar(in, c);
            }
            
            if (out.length() >= chunk) {
                xhtml.characters(out.toString());
                out.setLength(0);
            }
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
        
        xhtml.characters(out.toString());
        out.setLength(0);
        xhtml.endElement("p");
    }

    // Skips until the given character is encountered.
    private int skipUntilChar(WPInputStream in, int targetChar)
            throws IOException {
        int count = 0;
        int c;
        while ((c = in.read()) != -1) {
            count++;
            if (c == targetChar) {
                return count;
            }
        }
        return count;
    }
    
    private WP6FileHeader parseFileHeader(WPInputStream in) 
            throws IOException {
        WP6FileHeader header = new WP6FileHeader();

        // File header
        in.mark(30);
        header.setFileId(in.readWPString(4));         // 1-4
        header.setDocAreaPointer(in.readWPLong());    // 5-8
        header.setProductType(in.readWP());             // 9
        header.setFileType(in.readWPChar());          // 10
        header.setMajorVersion(in.readWP());            // 11
        header.setMinorVersion(in.readWP());            // 12
        header.setEncrypted(in.readWPShort() != 0);   // 13-14
        header.setIndexAreaPointer(in.readWPShort()); // 15-16
        try {
            in.skip(4); // 4 reserved bytes: skip     // 17-20
            header.setFileSize(in.readWPLong());      // 21-24
        } catch (IOException e) {
            // May fail if not extended error, which is fine.
        }
        in.reset();

        //TODO header may be shared between corel products, so move validation
        //specific to each product elsewhere?
        //TODO convert to logs only, and let it fail elsewhere?
//        if (!WP6Constants.WP6_FILE_ID.equals(header.getFileId())) {
//            throw new IOException("Not a WordPerfect file. File must start "
//                    + "with " + WP6Constants.WP6_FILE_ID + " but was "
//                    + header.getFileId());
//        }
//        if (WP6Constants.WP6_PRODUCT_TYPE != header.getProductType()) {
//            throw new IOException("Not a WordPerfect file. Product type "
//                    + "must be " + WP6Constants.WP6_PRODUCT_TYPE + " but was "
//                    + header.getProductType());
//        }
        //TODO perform file type validation?
        return header;
    }

}
