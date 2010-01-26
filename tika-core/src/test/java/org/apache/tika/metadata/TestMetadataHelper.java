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
package org.apache.tika.metadata;

import java.io.File;
import java.net.URL;

import junit.framework.TestCase;

/**
 * Unit tests for {@link MetadataHelper}.
 */
public class TestMetadataHelper extends TestCase {

    public void testGetInputStream() throws Exception {
        URL url = TestMetadataHelper.class.getResource("test.txt");
        File file = new File(url.toURI());

        Metadata urlMetadata = new Metadata();
        MetadataHelper.getInputStream(url, urlMetadata).close();
        assertEquals("test.txt", urlMetadata.get(Metadata.RESOURCE_NAME_KEY));
        assertEquals(
                Long.toString(file.length()),
                urlMetadata.get(Metadata.CONTENT_LENGTH));

        Metadata fileMetadata = new Metadata();
        MetadataHelper.getInputStream(file, fileMetadata).close();
        assertEquals("test.txt", fileMetadata.get(Metadata.RESOURCE_NAME_KEY));
        assertEquals(
                Long.toString(file.length()),
                fileMetadata.get(Metadata.CONTENT_LENGTH));
    }

}
