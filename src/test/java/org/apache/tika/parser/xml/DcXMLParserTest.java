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
package org.apache.tika.parser.xml;

import java.io.InputStream;
import java.io.StringWriter;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.WriteOutContentHandler;
import org.xml.sax.ContentHandler;

public class DcXMLParserTest extends TestCase {

    public void testXMLParser() throws Exception {
        InputStream input = DcXMLParserTest.class.getResourceAsStream(
                "/test-documents/testXML.xml");
        try {
            Metadata metadata = new Metadata();
            StringWriter writer = new StringWriter();
            ContentHandler handler = new WriteOutContentHandler(writer);
            new DcXMLParser().parse(input, handler, metadata);

            assertEquals(
                    "application/xml",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("Archimède et Lius", metadata.get(Metadata.TITLE));
            assertEquals("Rida Benjelloun", metadata.get(Metadata.CREATOR));
            assertEquals(
                    "Java, XML, XSLT, JDOM, Indexation",
                    metadata.get(Metadata.SUBJECT));
            assertEquals(
                    "Framework d\'indexation des documents XML, HTML, PDF etc.. ",
                    metadata.get(Metadata.DESCRIPTION));
            assertEquals(
                    "http://www.apache.org",
                    metadata.get(Metadata.IDENTIFIER));
            assertEquals("test", metadata.get(Metadata.TYPE));
            assertEquals("application/msword", metadata.get(Metadata.FORMAT));
            assertEquals("Fr", metadata.get(Metadata.LANGUAGE));
            assertEquals("Non restreint", metadata.get(Metadata.RIGHTS));

            String content = writer.toString();
            assertTrue(content.contains("Archimède et Lius"));
        } finally {
            input.close();
        }
    }

}
