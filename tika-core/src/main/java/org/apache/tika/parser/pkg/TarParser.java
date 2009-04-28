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
package org.apache.tika.parser.pkg;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.pkg.tar.TarEntry;
import org.apache.tika.parser.pkg.tar.TarInputStream;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Tar parser.
 */
public class TarParser extends PackageParser {

    /**
     * Parses the given stream as a tar file.
     */
    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, TikaException, SAXException {
        metadata.set(Metadata.CONTENT_TYPE, "application/x-tar");

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        // At the end we want to close the tar stream to release any associated
        // resources, but the underlying document stream should not be closed
        TarInputStream tar =
            new TarInputStream(new CloseShieldInputStream(stream));
        try {
            TarEntry entry = tar.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory()) {
                    Metadata entrydata = new Metadata();
                    entrydata.set(Metadata.RESOURCE_NAME_KEY, entry.getName());
                    parseEntry(tar, xhtml, entrydata);
                }
                entry = tar.getNextEntry();
            }
        } finally {
            tar.close();
        }

        xhtml.endDocument();
    }

}
