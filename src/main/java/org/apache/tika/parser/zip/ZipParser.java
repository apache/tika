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
package org.apache.tika.parser.zip;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Zip File Parser.
 */
public class ZipParser extends AbstractParser {

    private Parser parser;

    public void parse(InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, TikaException, SAXException {

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        ZipInputStream zis = new ZipInputStream(stream);
        ZipEntry ze;
        while ((ze = zis.getNextEntry()) != null) {
            xhtml.startElement("div", "class", "file");
            xhtml.element("h1", ze.getName());

            ContentHandler content = new BodyContentHandler();
            getParser().parse(new CloseShieldInputStream(zis), content, new Metadata());

            xhtml.element("content", content.toString());
            xhtml.endElement("div");

            zis.closeEntry();
        }
        zis.close();
        xhtml.endDocument();
    }

    public Parser getParser() {
        if (parser == null)
        {
            return new AutoDetectParser();
        }
        return parser;
    }

    public void setParser(Parser parser) {
        this.parser = parser;
    }
}
