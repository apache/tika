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

public class OpenOfficeParserTest extends TestCase {

    public void testXMLParser() throws Exception {
        InputStream input = OpenOfficeParserTest.class.getResourceAsStream(
                "/test-documents/testOpenOffice2.odt");
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new OpenOfficeParser().parse(input, handler, metadata);

            assertEquals(
                    "application/vnd.oasis.opendocument.text",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("2007-09-14T11:07:10", metadata.get(Metadata.DATE));
            assertEquals("en-US", metadata.get(Metadata.LANGUAGE));
            assertEquals(
                    "NeoOffice/2.2$Unix OpenOffice.org_project/680m18$Build-9161",
                    metadata.get("generator"));
            assertEquals("0", metadata.get("nbTab"));
            assertEquals("0", metadata.get("nbObject"));
            assertEquals("0", metadata.get("nbImg"));
            assertEquals("1", metadata.get("nbPage"));
            assertEquals("1", metadata.get("nbPara"));
            assertEquals("14", metadata.get("nbWord"));
            assertEquals("78", metadata.get("nbCharacter"));

            String content = handler.toString();
            assertTrue(content.contains(
                    "This is a sample Open Office document,"
                    + " written in NeoOffice 2.2.1 for the Mac."));
        } finally {
            input.close();
        }
    }

}
