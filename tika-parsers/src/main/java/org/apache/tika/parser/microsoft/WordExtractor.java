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
package org.apache.tika.parser.microsoft;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

public class WordExtractor extends AbstractPOIFSExtractor {

    public WordExtractor(ParseContext context) {
        super(context);
    }

    protected void parse(
            POIFSFileSystem filesystem, XHTMLContentHandler xhtml)
            throws IOException, SAXException, TikaException {
        org.apache.poi.hwpf.extractor.WordExtractor wordExtractor =
            new org.apache.poi.hwpf.extractor.WordExtractor(filesystem);

        addTextIfAny(xhtml, "header", wordExtractor.getHeaderText());

        for (String paragraph : wordExtractor.getParagraphText()) {
            xhtml.element("p", paragraph);
        }

        for (String paragraph : wordExtractor.getFootnoteText()) {
            xhtml.element("p", paragraph);
        }

        for (String paragraph : wordExtractor.getCommentsText()) {
            xhtml.element("p", paragraph);
        }

        for (String paragraph : wordExtractor.getEndnoteText()) {
            xhtml.element("p", paragraph);
        }

        addTextIfAny(xhtml, "footer", wordExtractor.getFooterText());

        try {
            DirectoryEntry op =
                (DirectoryEntry) filesystem.getRoot().getEntry("ObjectPool");
            for (Entry entry : op) {
                if (entry.getName().startsWith("_")
                        && entry instanceof DirectoryEntry) {
                    handleEmbededOfficeDoc((DirectoryEntry) entry, xhtml);
                }
            }
        } catch(FileNotFoundException e) {
        }
    }

    /**
     * Outputs a section of text if the given text is non-empty.
     *
     * @param xhtml XHTML content handler
     * @param section the class of the &lt;div/&gt; section emitted
     * @param text text to be emitted, if any
     * @throws SAXException if an error occurs
     */
    private void addTextIfAny(
            XHTMLContentHandler xhtml, String section, String text)
            throws SAXException {
        if (text != null && text.length() > 0) {
            xhtml.startElement("div", "class", section);
            xhtml.element("p", text);
            xhtml.endElement("div");
        }
    }

}
