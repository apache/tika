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
package org.apache.tika.parser.txt;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Text parser
 */
public class TXTParser implements Parser {

    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        CharsetDetector detector = new CharsetDetector();

        // Use the declared character encoding, if available
        String encoding = metadata.get(Metadata.CONTENT_ENCODING);
        if (encoding != null) {
            detector.setDeclaredEncoding(encoding);
        }

        // CharsetDetector expects a stream to support marks
        if (!stream.markSupported()) {
            stream = new BufferedInputStream(stream);
        }
        detector.setText(stream);
    
        CharsetMatch match = detector.detect();
        if (match == null) {
            throw new TikaException("Unable to detect character encoding");
        }

        Reader reader = new BufferedReader(match.getReader());
        // TIKA-240: Drop the BOM when extracting plain text
        reader.mark(1);
        int bom = reader.read();
        if (bom != '\ufeff') { // zero-width no-break space
            reader.reset();
        }

        metadata.set(Metadata.CONTENT_TYPE, "text/plain");
        metadata.set(Metadata.CONTENT_ENCODING, match.getName());

        String language = match.getLanguage();
        if (language != null) {
            metadata.set(Metadata.CONTENT_LANGUAGE, match.getLanguage());
            metadata.set(Metadata.LANGUAGE, match.getLanguage());
        }

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.startElement("p");
        char[] buffer = new char[4096];
        for (int n = reader.read(buffer); n != -1; n = reader.read(buffer)) {
            xhtml.characters(buffer, 0, n);
        }
        xhtml.endElement("p");
        xhtml.endDocument();
    }

}
