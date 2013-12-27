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

package org.apache.tika.parser.chm;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.chm.accessor.DirectoryListingEntry;
import org.apache.tika.parser.chm.core.ChmExtractor;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

/**
 * Extracts text and metadata from chm file
 */
class CHMDocumentInformation {

    private final ChmExtractor chmExtractor;

    /**
     * Loads chm file as input stream and returns a new instance of chm doc info
     * 
     * @param stream chm input stream
     */
    public CHMDocumentInformation(InputStream stream)
            throws TikaException, IOException {
        this.chmExtractor = new ChmExtractor(stream);
    }

    /**
     * Checks if an entry is a html or not.
     * 
     * @param entry
     *            chm directory listing entry
     * 
     * @return boolean
     */
    private boolean isRightEntry(DirectoryListingEntry entry) {
        return (entry.getName().endsWith(".html") || entry.getName().endsWith(".htm"));
    }

    /**
     * Returns extracted text from chm file
     * 
     * @return text
     * 
     * @throws TikaException
     */
    public String getText() throws TikaException {
        StringBuilder sb = new StringBuilder();
        DirectoryListingEntry entry;
        
        for (Iterator<DirectoryListingEntry> it = chmExtractor
                .getChmDirList().getDirectoryListingEntryList().iterator(); it.hasNext();) 
        {
            try {
                entry = it.next();
                if (isRightEntry(entry)) {
                    byte[][] tmp = chmExtractor.extractChmEntry(entry);
                    if (tmp != null) {
                        sb.append(extract(tmp));
                    }
                }
            } catch (TikaException e) {
                //ignore
            } // catch (IOException e) {//Pushback exception from tagsoup
            // System.err.println(e.getMessage());
        }
        return sb.toString();
    }

    /**
     * Extracts data from byte[][]
     * 
     * @param byteObject
     * @return
     * @throws IOException
     * @throws SAXException
     */
    private String extract(byte[][] byteObject) {// throws IOException
        StringBuilder wBuf = new StringBuilder();
        InputStream stream = null;
        Metadata metadata = new Metadata();
        HtmlParser htmlParser = new HtmlParser();
        BodyContentHandler handler = new BodyContentHandler(-1);// -1
        ParseContext parser = new ParseContext();
        try {
            for (int i = 0; i < byteObject.length; i++) {
                stream = new ByteArrayInputStream(byteObject[i]);
                try {
                    htmlParser.parse(stream, handler, metadata, parser);
                } catch (TikaException e) {
                    wBuf.append(new String(byteObject[i]));
//                    System.err.println("\n"
//                            + CHMDocumentInformation.class.getName()
//                            + " extract " + e.getMessage());
                } finally {
                    wBuf.append(handler.toString()
                            + System.getProperty("line.separator"));
                    stream.close();
                }
            }
        } catch (SAXException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {// 
        // Pushback overflow from tagsoup
        }
        return wBuf.toString();
    }

}
