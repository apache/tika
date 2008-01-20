/**
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
package org.apache.tika.parser.microsoft;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.CharacterRun;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.util.LittleEndian;
import org.apache.tika.exception.TikaException;

import java.io.IOException;

/**
 * Word parser
 */
public class WordParser extends OfficeParser {

    protected String getContentType() {
        return "application/msword";
    }

    /**
     * Gets the text from a Word document.
     *
     * @param fsys the <code>POIFSFileSystem</code> to read the word document from.
     * @param appendable the <code>Appendable</code> to add the text content to.
     */
    public void extractText(POIFSFileSystem fsys, Appendable appendable)
            throws IOException, TikaException {
        // load our POIFS document streams.
        DocumentEntry headerProps =
            (DocumentEntry) fsys.getRoot().getEntry("WordDocument");
        DocumentInputStream din = fsys.createDocumentInputStream("WordDocument");
        byte[] header = new byte[headerProps.getSize()];

        din.read(header);
        din.close();

        int info = LittleEndian.getShort(header, 0xa);
        if ((info & 0x4) != 0) {
            throw new TikaException("Fast-saved files are unsupported");
        }
        if ((info & 0x100) != 0) {
            throw new TikaException("This document is password protected");
        }

        // determine the version of Word this document came from.
        int nFib = LittleEndian.getShort(header, 0x2);
        switch (nFib) {
        case 101:
        case 102:
        case 103:
        case 104:
            // this is a Word 6.0 doc send it to the extractor for that version.
            Word6Extractor oldExtractor = new Word6Extractor(appendable);
            oldExtractor.extractText(header);

            // Set POI values to null
            headerProps = null;
            header = null;
            din = null;
            fsys = null;
            return;
        }

        WordTextBuffer finalTextBuf = new WordTextBuffer(appendable);

        HWPFDocument doc = new HWPFDocument(fsys);
        Range range = doc.getRange();
        for (int i = 0; i < range.numCharacterRuns(); i++) {
            CharacterRun cr = range.getCharacterRun(i);
            if (!cr.isMarkedDeleted()) {
                finalTextBuf.append(cr.text());
            }
        }

        // Set POI values to null
        headerProps = null;
        header = null;
        din = null;
        doc = null;
        fsys = null;
    }
}
