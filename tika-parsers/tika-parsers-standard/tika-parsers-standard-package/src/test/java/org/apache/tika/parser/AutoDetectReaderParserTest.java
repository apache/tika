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
package org.apache.tika.parser;

import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.MultiThreadedTikaTest;
import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.sax.XHTMLContentHandler;

public class AutoDetectReaderParserTest extends MultiThreadedTikaTest {


    @Test
    public void testMulti() throws Exception {

        int numThreads = 10;
        int numIterations = 10;
        ParseContext[] contexts = new ParseContext[numThreads];
        for (int i = 0; i < numThreads; i++) {
            contexts[i] = new ParseContext();
        }
        FileFilter fileFilter = pathname -> pathname.getName().endsWith(".txt") || pathname.getName().endsWith(".html");
        testMultiThreaded(AUTO_DETECT_PARSER, contexts, numThreads, numIterations, fileFilter);
    }

    //this class mimics creating a new AutoDetectReader w/o supplying
    //a detector.
    public static class AutoDetectingReaderParser implements Parser {

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return Collections.unmodifiableSet(
                    new HashSet<>(Arrays.asList(MediaType.text("html"), MediaType.text("plain"))));
        }

        @Override
        public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                          ParseContext context) throws IOException, SAXException, TikaException {
            try (AutoDetectReader reader = new AutoDetectReader(stream)) {
                Charset charset = reader.getCharset();
                MediaType type = new MediaType(MediaType.parse("text/plhtml"), charset);
                metadata.set(Metadata.CONTENT_TYPE, type.toString());
                XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
                xhtml.startDocument();

                xhtml.startElement("p");
                char[] buffer = new char[4096];
                int n = reader.read(buffer);
                while (n != -1) {
                    xhtml.characters(buffer, 0, n);
                    n = reader.read(buffer);
                }
                xhtml.endElement("p");

                xhtml.endDocument();

            }
        }


    }
}
