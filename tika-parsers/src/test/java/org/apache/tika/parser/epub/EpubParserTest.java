/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.epub;

import static org.junit.Assert.assertEquals;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.junit.Test;

public class EpubParserTest extends TikaTest {

    @Test
    public void testXMLParser() throws Exception {

        XMLResult xmlResult = getXML("testEPUB.epub");

        assertEquals("application/epub+zip",
                xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("en",
                xmlResult.metadata.get(TikaCoreProperties.LANGUAGE));
        assertEquals("This is an ePub test publication for Tika.",
                xmlResult.metadata.get(TikaCoreProperties.DESCRIPTION));
        assertEquals("Apache",
                xmlResult.metadata.get(TikaCoreProperties.PUBLISHER));

        String content = xmlResult.xml;
        assertContains("Plus a simple div", content);
        assertContains("First item", content);
        assertContains("The previous headings were <strong>subchapters</strong>", content);
        assertContains("Table data", content);
        assertContains("This is the text for chapter Two", content);

        //make sure style/script elements aren't extracted
        assertNotContained("nothing to see here", content);
        assertNotContained("nor here", content);
        assertNotContained("font-style", content);

        //make sure that there is only one of each
        assertContainsCount("<html", content, 1);
        assertContainsCount("<head", content, 1);
        assertContainsCount("<body", content, 1);

    }

}
