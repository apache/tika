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
package org.apache.tika.parser.microsoft.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.apache.tika.MultiThreadedTikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.utils.XMLReaderUtils;

public class XML2003ParserTest extends MultiThreadedTikaTest {

    @AfterAll
    public static void tearDown() throws TikaException {
        XMLReaderUtils.setPoolSize(XMLReaderUtils.DEFAULT_POOL_SIZE);
    }


    @Test
    public void testBasicExcel() throws Exception {
        XMLResult r = getXML("testEXCEL2003.xml");
        Metadata m = r.metadata;
        assertEquals("Allison, Timothy B.", m.get(TikaCoreProperties.CREATOR));
        assertEquals("16.00", m.get(OfficeOpenXMLCore.VERSION));
        assertEquals("application/vnd.ms-spreadsheetml", m.get(Metadata.CONTENT_TYPE));

        String xml = r.xml;
        xml = xml.replaceAll("\\s+", " ");
        //confirm metadata was dumped to xml
        assertContains("<meta name=\"cp:version\" content=\"16.00\" />", xml);
        assertContains("<tr> <td>Col1</td> <td>Col2</td>", xml);
        assertContains("<td>2016-04-27T00:00:00.000</td>", xml);
        assertContains("<a href=\"https://tika.apache.org/\">tika_hyperlink</a>", xml);
        assertContains("<td>5.5</td>", xml);

        //check that text is extracted with breaks between elements
        String txt = getText(getResourceAsStream("/test-documents/testEXCEL2003.xml"),
                AUTO_DETECT_PARSER);
        txt = txt.replaceAll("\\s+", " ");
        assertContains("Col1 Col2 Col3 Col4 string 1 1.10", txt);

    }

    @Test
    @Timeout(60000)
    public void testMultiThreaded() throws Exception {
        XMLReaderUtils.setPoolSize(4);
        int numThreads = XMLReaderUtils.getPoolSize() * 2;
        ParseContext[] contexts = new ParseContext[numThreads];
        for (int i = 0; i < contexts.length; i++) {
            contexts[i] = new ParseContext();
        }

        testMultiThreaded(AUTO_DETECT_PARSER, contexts, numThreads, 2,
                pathname -> pathname.getName().equals("testWORD2003.xml"));

    }
}
