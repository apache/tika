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
package org.apache.tika.parser.microsoft.ooxml;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;

public class TruncatedOOXMLTest extends TikaTest {

    @Test
    public void testWordTrunc13138() throws Exception {
        //this truncates the content_types.xml
        //this tests that there's a backoff to the pkg parser
        List<Metadata> metadataList =
                getRecursiveMetadata(truncate("testWORD_various.docx", 13138), true);
        assertEquals(19, metadataList.size());
        Metadata m = metadataList.get(0);
        assertEquals("application/x-tika-ooxml", m.get(Metadata.CONTENT_TYPE));
    }

    @Test
    public void testWordTrunc774() throws Exception {
        //this is really truncated
        List<Metadata> metadataList =
                getRecursiveMetadata(truncate("testWORD_various.docx", 774), true);
        assertEquals(4, metadataList.size());
        Metadata m = metadataList.get(0);
        assertEquals("application/x-tika-ooxml", m.get(Metadata.CONTENT_TYPE));
    }
}
