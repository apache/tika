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

package org.apache.tika.parser.microsoft;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class XML2003ParserTest extends TikaTest {
    @Test
    public void testBasicWord() throws Exception {
        List<Metadata> list =  getRecursiveMetadata("testWORD2003.xml");
        assertEquals(6, list.size());
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
                Arrays.asList(list.get(5).getValues(TikaCoreProperties.SUBJECT)));

        assertEquals("testJPEG_EXIF.jpg", list.get(5).get(TikaCoreProperties.ORIGINAL_RESOURCE_NAME));

        //check that text is extracted with breaks between elements
        String txt = getText(getResourceAsStream("/test-documents/testWORD2003.xml"),AUTO_DETECT_PARSER);
        txt = txt.replaceAll("\\s+", " ");
        assertNotContained("beforeR1", txt);
        assertContains("R1 c1 R1 c2", txt);
        assertNotContained("footnoteFigure", txt);
        assertContains("footnote Figure", txt);
        assertContains("test space", txt);

    }
}
