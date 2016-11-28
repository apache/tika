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

package org.apache.tika.extractor;

import org.apache.tika.TikaTest;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.junit.Test;

/**
 * Integration tests for EmbeddedDocumentUtil
 */
public class EmbeddedDocumentUtilTest extends TikaTest {

    @Test
    public void testAutomaticAdditionOfAutoDetectParserIfForgotten() throws Exception {
        String needle = "When in the Course";
        //TIKA-2096
        TikaTest.XMLResult xmlResult = getXML("test_recursive_embedded.doc", new ParseContext());
        assertContains(needle, xmlResult.xml);

        ParseContext context = new ParseContext();
        context.set(Parser.class, new EmptyParser());
        xmlResult = getXML("test_recursive_embedded.doc", context);
        assertNotContained(needle, xmlResult.xml);
    }
}
