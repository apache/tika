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

package org.apache.tika.parser.journal;

import static org.apache.tika.parser.journal.GrobidRESTParser.canRun;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

public class JournalParserTest {

    @Test
    public void testJournalParser() {
        String path = "/test-documents/testJournalParser.pdf";
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        assumeTrue(canRun());

        InputStream stream = JournalParserTest.class.getResourceAsStream(path);
        JournalParser jParser = new JournalParser();
        try {
            jParser.parse(stream, handler, metadata, new ParseContext());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }

        assertNotNull(metadata.get("grobid:header_Title"));
    }
}
