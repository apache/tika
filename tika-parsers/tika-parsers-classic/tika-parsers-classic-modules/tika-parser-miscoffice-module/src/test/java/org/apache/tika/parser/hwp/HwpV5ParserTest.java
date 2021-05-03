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
package org.apache.tika.parser.hwp;

import static org.junit.Assert.assertEquals;

import org.apache.commons.io.filefilter.RegexFileFilter;
import org.junit.Test;

import org.apache.tika.MultiThreadedTikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;


public class HwpV5ParserTest extends MultiThreadedTikaTest {

    @Test
    public void testHwpV5Parser() throws Exception {
        for (Parser parser : new Parser[]{new HwpV5Parser(), AUTO_DETECT_PARSER}) {
            XMLResult result = getXML("testHWP-v5b.hwp", parser);
            assertContains("<p>Apache Tika - \uCEE8\uD150\uCE20", result.xml);
            Metadata metadata = result.metadata;
            assertEquals("application/x-hwp-v5", metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("Apache Tika", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("SooMyung Lee", metadata.get(TikaCoreProperties.CREATOR));

            assertContains("Apache Tika", result.xml);
        }
    }

    @Test
    public void testDistributedHwp() throws Exception {
        XMLResult result = getXML("testHWP-v5-dist.hwp");
        String content = result.xml;
        assertContains("<p>Apache Tika - \uCEE8\uD150\uCE20", content);

        assertEquals("application/x-hwp-v5", result.metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Apache Tika", result.metadata.get(TikaCoreProperties.TITLE));
        assertEquals("SooMyung Lee", result.metadata.get(TikaCoreProperties.CREATOR));
    }

    @Test
    public void testExisting() throws Exception {
        XMLResult result = getXML("testHWP_5.0.hwp");
        String content = result.xml;
        Metadata metadata = result.metadata;
        assertContains("\uD14C\uC2A4\uD2B8", content);
        assertContains("test", content);
        assertEquals("next1009", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("\uD14C\uC2A4\uD2B8", metadata.get(TikaCoreProperties.TITLE));
    }

    @Test
    public void testMultiThreadedSkipFully() throws Exception {
        //TIKA-3092
        int numThreads = 2;
        int numIterations = 50;
        ParseContext[] parseContexts = new ParseContext[numThreads];

        testMultiThreaded(new RecursiveParserWrapper(AUTO_DETECT_PARSER), parseContexts, numThreads,
                numIterations, new RegexFileFilter(".*\\.hwp"));
    }
}
