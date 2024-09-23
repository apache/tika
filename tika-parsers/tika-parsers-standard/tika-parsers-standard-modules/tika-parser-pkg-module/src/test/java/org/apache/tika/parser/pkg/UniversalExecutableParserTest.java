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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Test case for parsing universal executable files.
 */
public class UniversalExecutableParserTest extends AbstractPkgTest {

    @Test
    public void testMachO() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = getResourceAsStream("/test-documents/testMacOS-x86_64-arm64")) {
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, monitoringContext);
        }

        assertEquals(2, monitor.filenames.size());
        assertEquals(2, monitor.mediaTypes.size());

        for (String filename : monitor.filenames) {
            assertNull(filename);
        }
        for (String mediaType : monitor.mediaTypes) {
            assertEquals("application/x-mach-o-executable", mediaType);
        }
    }
}
