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
package org.apache.tika.parser.ibooks;

import static org.junit.Assert.assertEquals;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.junit.Test;

public class iBooksParserTest extends TikaTest {

    @Test
    public void testiBooksParser() throws Exception {

        XMLResult xmlResult = getXML("testiBooks.ibooks");

        assertEquals("application/x-ibooks+zip",
                xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("en-GB",
                xmlResult.metadata.get(TikaCoreProperties.LANGUAGE));
        assertEquals("iBooks Author v1.0",
                xmlResult.metadata.get(TikaCoreProperties.CONTRIBUTOR));
        assertEquals("Apache",
                xmlResult.metadata.get(TikaCoreProperties.CREATOR));

        String content = xmlResult.xml;
        //appears twice in section 1
        // (we skip it in searchRefText.xml because of that file's suffix)
        assertContainsCount("rutur", content, 2);
        //only appears in section 2
        assertContains("Morbi", content);
        //Glossary has no body content so we can't test for that

        //Toc does
        assertContains("1.1\tUntitled", content);

        //this is a legacy comment...I can't find this content in the current ibooks
        //test file.  I think we're good?
        /* TODO For some reason, the xhtml files in iBooks-style ePub are not parsed properly, and the content comes back empty.git che
            String content = handler.toString();
            System.out.println("content="+content);
            assertContains("Plus a simple div", content);
            assertContains("First item", content);
            assertContains("The previous headings were subchapters", content);
            assertContains("Table data", content);
            assertContains("Lorem ipsum dolor rutur amet", content);
        */

    }
}
