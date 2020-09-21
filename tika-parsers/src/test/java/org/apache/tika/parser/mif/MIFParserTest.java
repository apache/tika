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

package org.apache.tika.parser.mif;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Test case for the Adobe MIF Parser.
 */
public class MIFParserTest extends TikaTest {

    /**
     * Shared MIFParser instance.
     */
    private final Parser parser = new MIFParser();

    @Test
    public void testParserToText() throws Exception {
        Metadata metadata = new Metadata();
        String content = getText("testFramemakerMif.mif", parser, metadata);
        assertEquals("1", metadata.get("PageCount"));
        assertEquals("application/x-mif", metadata.get(Metadata.CONTENT_TYPE));
        assertContains("Lorem ipsum dolor sit amet, consectetur adipiscing elit", content);
    }

    @Test
    public void testParserToXML() throws Exception {
        Metadata metadata = new Metadata();
        String xml = getXML("testFramemakerMif.mif", parser, metadata).xml;
        assertEquals("1", metadata.get("PageCount"));
        assertContains("<p>Lorem ipsum dolor sit amet, consectetur adipiscing elit", xml);
        assertContains("<meta name=\"Content-Type\" content=\"application/x-mif\" />", xml);
    }

    @Test(expected = TikaException.class)
    public void testParserVersionCheck() throws Exception {
        Metadata metadata = new Metadata();
        getText("testMIF.mif", parser, metadata);
    }

}