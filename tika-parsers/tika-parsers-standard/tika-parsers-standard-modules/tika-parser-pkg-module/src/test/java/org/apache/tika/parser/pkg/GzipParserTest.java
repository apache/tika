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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Test case for parsing gzip files.
 */
public class GzipParserTest extends TikaTest {

    /**
     * Tests that the ParseContext parser is correctly
     * fired for all the embedded entries.
     */
    @Test
    public void testEmbedded() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("test-documents.tgz");

        // Container plus embedded tar contents
        assertTrue(metadataList.size() > 1);

        // Embedded documents should have path through the tar file
        String embeddedPath = metadataList.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH);
        assertTrue(embeddedPath.contains("test-documents.tar"));
    }

    @Test
    public void testGzipInternalFileName() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("bob.gz");
        assertEquals(2, metadataList.size());

        Metadata m1 = metadataList.get(1);
        assertEquals("alice.txt", m1.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("alice.txt", m1.get(TikaCoreProperties.INTERNAL_PATH));
    }
}
