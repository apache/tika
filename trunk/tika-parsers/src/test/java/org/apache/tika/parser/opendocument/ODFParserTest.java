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
package org.apache.tika.parser.opendocument;

import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

import org.apache.tika.parser.odf.OpenDocumentParser;

public class ODFParserTest extends TestCase {

    public void testXMLParser() throws Exception {
        InputStream input = ODFParserTest.class.getResourceAsStream(
                "/test-documents/testODFwithOOo3.odt");
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new OpenDocumentParser().parse(input, handler, metadata);

            assertEquals(
                    "application/vnd.oasis.opendocument.text",
                    metadata.get(Metadata.CONTENT_TYPE));
            
            String content = handler.toString();
            assertTrue(content.contains("Tika is part of the Lucene project."));
            assertTrue(content.contains("Solr"));
            assertTrue(content.contains("one embedded"));
            assertTrue(content.contains("Rectangle Title"));
            assertTrue(content.contains("a blue background and dark border"));        } finally {
            input.close();
        }
    }

}