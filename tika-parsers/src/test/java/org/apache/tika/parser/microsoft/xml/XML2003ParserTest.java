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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.List;

import org.apache.tika.MultiThreadedTikaTest;
import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.utils.XMLReaderUtils;
import org.junit.AfterClass;
import org.junit.Test;

public class XML2003ParserTest extends MultiThreadedTikaTest {

    @AfterClass
    public static void tearDown() throws TikaException {
        XMLReaderUtils.setPoolSize(XMLReaderUtils.DEFAULT_POOL_SIZE);
    }

    @Test
    public void testBasicWord() throws Exception {
        List<Metadata> list =  getRecursiveMetadata("testWORD2003.xml");
        assertEquals(8, list.size());
        Metadata m = list.get(0);//container doc
        String xml = m.get(RecursiveParserWrapper.TIKA_CONTENT);
        xml = xml.replaceAll("\\s+", " ");
        //make sure that metadata gets dumped to xml
        assertContains("<meta name=\"meta:character-count-with-spaces\" content=\"256\"", xml);
        //do not allow nested <p> elements
        assertContains("<p /> <img href=\"02000003.jpg\" /><p /> <p><img href=\"02000004.jpg\" /></p>", xml);
        assertContains("<table><tbody>", xml);
        assertContains("</tbody></table>", xml);
        assertContains("<td><p>R1 c1</p> </td>", xml);
        assertContains("<a href=\"https://tika.apache.org/\">tika</a>", xml);
        assertContains("footnote", xml);
        assertContains("Mycomment", xml);
        assertContains("Figure 1: My Figure", xml);
        assertContains("myEndNote", xml);
        assertContains("We have always been at war with OceaniaEurasia", xml);
        assertContains("Text box", xml);
        assertNotContained("Text boxText box", xml);
        assertContains("MyHeader", xml);
        assertContains("MyFooter", xml);
        assertContains("<img href=\"02000003.jpg\" />", xml);
        assertEquals("219", m.get(Office.CHARACTER_COUNT));
        assertEquals("256", m.get(Office.CHARACTER_COUNT_WITH_SPACES));

        assertEquals("38", m.get(Office.WORD_COUNT));
        assertEquals("1", m.get(Office.PARAGRAPH_COUNT));
        assertEquals("Allison, Timothy B.", m.get(TikaCoreProperties.CREATOR));
        assertEquals("2016-04-27T17:49:00Z", m.get(TikaCoreProperties.CREATED));
        assertEquals("application/vnd.ms-wordml", m.get(Metadata.CONTENT_TYPE));

        //make sure embedded docs were properly processed
        assertContains("moscow-birds",
                Arrays.asList(list.get(7).getValues(TikaCoreProperties.KEYWORDS)));

        assertEquals("testJPEG_EXIF.jpg", list.get(7).get(TikaCoreProperties.ORIGINAL_RESOURCE_NAME));

        //check that text is extracted with breaks between elements
        String txt = getText(getResourceAsStream("/test-documents/testWORD2003.xml"),AUTO_DETECT_PARSER);
        txt = txt.replaceAll("\\s+", " ");
        assertNotContained("beforeR1", txt);
        assertContains("R1 c1 R1 c2", txt);
        assertNotContained("footnoteFigure", txt);
        assertContains("footnote Figure", txt);
        assertContains("test space", txt);

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
        String txt = getText(getResourceAsStream("/test-documents/testEXCEL2003.xml"), AUTO_DETECT_PARSER);
        txt = txt.replaceAll("\\s+", " ");
        assertContains("Col1 Col2 Col3 Col4 string 1 1.10", txt);

    }

    @Test(timeout = 60000)
    public void testMultiThreaded() throws Exception {
        XMLReaderUtils.setPoolSize(4);
        int numThreads = XMLReaderUtils.getPoolSize()*2;
        ParseContext[] contexts = new ParseContext[numThreads];
        for (int i = 0; i < contexts.length; i++) {
            contexts[i] = new ParseContext();
        }

        testMultiThreaded(AUTO_DETECT_PARSER, contexts, numThreads, 2,
                new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        if (pathname.getName().equals("testWORD2003.xml")) {
                            return true;
                        }
                        return false;
                    }
                });

    }
}
