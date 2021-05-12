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
package org.apache.tika.parser.pkg;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;

import org.junit.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Test case for parsing zlib compressed
 */
public class ZlibParserTest extends AbstractPkgTest {
    @Test
    public void testZlibParsing() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = getResourceAsStream("/test-documents/testTXT.zlib")) {
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, recursingContext);
        }

        assertEquals("application/zlib", metadata.get(Metadata.CONTENT_TYPE));
        String content = handler.toString();
        assertContains("Test d'indexation de Txt", content);
        assertContains("http://www.apache.org", content);
    }
}
