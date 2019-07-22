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

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.junit.Test;

public class HwpV5ParserTest extends TikaTest {

    @Test
    public void testHwpV5Parser() throws Exception {
        for (Parser parser : new Parser[]{new HwpV5Parser(),
                new AutoDetectParser()}) {
            XMLResult result = getXML("test-documents-v5.hwp", parser);
            Metadata metadata = result.metadata;
            assertEquals(
                    "application/x-hwp-v5", metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("Apache Tika", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("SooMyung Lee", metadata.get(TikaCoreProperties.CREATOR));

            assertContains("Apache Tika", result.xml.toString());
        }
    }

    @Test
    public void testDistributedHwp() throws Exception {
        XMLResult result = getXML("test-documents-v5-dist.hwp");
        assertContains("Apache Tika", result.xml);

        assertEquals(
                "application/x-hwp-v5",
                result.metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Apache Tika", result.metadata.get(TikaCoreProperties.TITLE));
        assertEquals("SooMyung Lee", result.metadata.get(TikaCoreProperties.CREATOR));
    }
}
