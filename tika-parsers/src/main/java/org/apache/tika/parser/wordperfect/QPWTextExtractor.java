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
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Strings;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.UnsupportedFormatException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.QuattroPro;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.sax.XHTMLContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 * Extracts text from a Quattro Pro document according to QPW v9 File Format.
 * This format appears to be compatible with more recent versions too.
 * @author Pascal Essiembre
 */
class QPWTextExtractor {
    private static final Logger LOG = LoggerFactory.getLogger(QPWTextExtractor.class);

    private static final String OLE_DOCUMENT_NAME = "NativeContent_MAIN";

    private enum Extractor {
        IGNORE { @Override public void extract(Context ctx) throws IOException {
            ctx.in.skipWPByte(ctx.bodyLength);
        }},
        BOF { @Override public void extract(Context ctx) throws IOException {
            ctx.metadata.set(QuattroPro.ID, ctx.in.readWPString(4));
            ctx.metadata.set(QuattroPro.VERSION, ctx.in.readWPShort());
            ctx.metadata.set(QuattroPro.BUILD, ctx.in.readWPShort());
            ctx.in.readWPShort(); // Last saved bits
            ctx.metadata.set(QuattroPro.LOWEST_VERSION, ctx.in.readWPShort());
            ctx.metadata.set(Office.PAGE_COUNT, ctx.in.readWPShort());
            ctx.in.skipWPByte(ctx.bodyLength - 14);
        }},
        USER { @Override public void extract(Context ctx) throws IOException {
            ctx.metadata.set(TikaCoreProperties.CREATOR, getQstrLabel(ctx.in));
            ctx.metadata.set(TikaCoreProperties.MODIFIER, getQstrLabel(ctx.in));
        }},
        EXT_LINK { @Override public void extract(Context ctx)
                throws IOException, SAXException {
            ctx.in.readWPShort(); // index
            ctx.in.readWPShort(); // page first
            ctx.in.readWPShort(); // page last
            ctx.xhtml.characters(getQstrLabel(ctx.in));
            ctx.xhtml.characters(System.lineSeparator());
        }},
        STRING_TABLE { @Override public void extract(Context ctx)
                throws IOException, SAXException {
            long entries = ctx.in.readWPLong();
            ctx.in.readWPLong();  // Total used
            ctx.in.readWPLong();  // Total saved
            for (int i = 0; i < entries; i++) {
                ctx.xhtml.characters(getQstrLabel(ctx.in));
                ctx.xhtml.characters(System.lineSeparator());
            }
        }},
        BOS { @Override public void extract(Context ctx)
                throws IOException, SAXException {
            ctx.in.readWPShort(); // sheet #
            ctx.in.readWPShort(); // first col index
            ctx.in.readWPShort(); // last col index
            ctx.in.readWPLong();  // first row index
            ctx.in.readWPLong();  // last row index
            ctx.in.readWPShort(); // format
            ctx.in.readWPShort(); // flags
            ctx.xhtml.characters(getQstrLabel(ctx.in));
            ctx.xhtml.characters(System.lineSeparator());
        }},
        SHEET_HEADFOOT { @Override public void extract(Context ctx)
                throws IOException, SAXException {
            ctx.in.readWPShort(); // flag
            ctx.xhtml.characters(getQstrLabel(ctx.in));
            ctx.xhtml.characters(System.lineSeparator());
        }},
        FORMULA_STRING_VALUE { @Override public void extract(Context ctx)
                throws IOException, SAXException {
            ctx.in.readWPShort(); // column
            ctx.in.readWPLong();  // row
            ctx.xhtml.characters(getQstrLabel(ctx.in));
        }},
        CGENERICLABEL { @Override public void extract(Context ctx)
                throws IOException, SAXException {
            ctx.in.readWPShort(); // column
            ctx.in.readWPLong();  // row
            ctx.in.readWPShort(); // format index
            ctx.xhtml.characters(getQstrLabel(ctx.in));
        }},
        CCOMMENT { @Override public void extract(Context ctx)
                throws IOException, SAXException {
            ctx.in.readWPShort(); // column
            ctx.in.readWPLong();  // row
            ctx.in.readWPLong();  // flag
            ctx.xhtml.characters(getQstrLabel(ctx.in));  // author name
            ctx.xhtml.characters(getQstrLabel(ctx.in));  // comment
        }},
        // Use to print out a chunk
        DEBUG { @Override public void extract(Context ctx) throws IOException {
            LOG.error("REC ({}/{}):{}", Integer.toHexString(ctx.type), ctx.bodyLength, ctx.in.readWPString(ctx.bodyLength));
        }};
        public abstract void extract(Context ctx)
                throws IOException, SAXException;
    }

    // Holds extractors for each record types we are interested in.
    // All record types not defined here will be skipped.
    private static final Map<Integer, Extractor> EXTRACTORS =
            new HashMap<>();
    static {
        //--- Global Records ---
        EXTRACTORS.put(0x0001, Extractor.BOF);     // Beginning of file
        EXTRACTORS.put(0x0005, Extractor.USER);    // User

        //--- Notebook Records ---
        EXTRACTORS.put(0x0403, Extractor.EXT_LINK);// External link
        EXTRACTORS.put(0x0407, Extractor.STRING_TABLE); // String table

        //--- Sheet Records ---
        EXTRACTORS.put(0x0601, Extractor.BOS); // Beginning of sheet
        EXTRACTORS.put(0x0605, Extractor.SHEET_HEADFOOT); // Sheet header
        EXTRACTORS.put(0x0606, Extractor.SHEET_HEADFOOT); // Sheet footer

        //--- Cells ---
        EXTRACTORS.put(0x0c02, Extractor.FORMULA_STRING_VALUE);
        EXTRACTORS.put(0x0c72, Extractor.CGENERICLABEL);
        EXTRACTORS.put(0x0c80, Extractor.CCOMMENT);
    }

    class Context {
        private final WPInputStream in;
        private final XHTMLContentHandler xhtml;
        private final Metadata metadata;
        private int type;
        private int bodyLength;
        public Context(WPInputStream in, XHTMLContentHandler xhtml,
                Metadata metadata) {
            super();
            this.in = in;
            this.xhtml = xhtml;
            this.metadata = metadata;
        }
    }

    @SuppressWarnings("resource")
    public void extract(
            InputStream input, XHTMLContentHandler xhtml, Metadata metadata)
                    throws IOException, SAXException, TikaException {

        POIFSFileSystem pfs = new POIFSFileSystem(input);
        DirectoryNode rootNode = pfs.getRoot();
        if (rootNode == null || !rootNode.hasEntry(OLE_DOCUMENT_NAME)) {
            throw new UnsupportedFormatException("Unsupported QuattroPro file format. "
                    + "Looking for OLE entry \"" + OLE_DOCUMENT_NAME
                    + "\". Found: " + (rootNode == null ? "null" : rootNode.getEntryNames()));
        }

        //TODO shall we validate and throw warning/error if the file does not 
        //start with a BOF and ends with a EOF?
        xhtml.startElement("p");
        try (WPInputStream in = new WPInputStream(
                pfs.createDocumentInputStream(OLE_DOCUMENT_NAME))) {
            Context ctx = new Context(in, xhtml, metadata);
            while (hasNext(in)) {
                ctx.type = in.readWPShort();
                ctx.bodyLength = in.readWPShort();
                Extractor extractor = EXTRACTORS.get(ctx.type);
                if (extractor != null) {
                    extractor.extract(ctx);
                } else {
                    // Use DEBUG to find out what we are ignoring
//                    Extractor.DEBUG.extract(ctx);
                    Extractor.IGNORE.extract(ctx);
                }
            }
        }
        xhtml.endElement("p");
    }

    private boolean hasNext(InputStream in) throws IOException {
        try {
            in.mark(1);
            return in.read() != -1;
        } finally {
            in.reset();
        }
    }

    private static String getQstrLabel(WPInputStream in) throws IOException {
        // QSTR
        int count = in.readWPShort();
        in.readWPByte(); // string type
        char[] text = new char[count+1];
        text[0] = in.readWPChar();

        // QSTRLABEL
        for (int i = 0; i < count; i++) {
            text[i+1] = in.readWPChar();
        }
        return new String(text);
    }
}
