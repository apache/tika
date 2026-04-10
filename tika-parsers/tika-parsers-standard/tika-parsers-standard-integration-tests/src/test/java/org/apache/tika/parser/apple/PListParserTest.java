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
package org.apache.tika.parser.apple;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class PListParserTest extends TikaTest {

    @Test
    public void testWebArchive() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testWEBARCHIVE.webarchive");
        assertEquals(12, metadataList.size());
        Metadata m0 = metadataList.get(0);
        assertEquals("application/x-bplist-webarchive", m0.get(Metadata.CONTENT_TYPE));
        Metadata m1 = metadataList.get(1);
        String content = m1.get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("December 2008: Apache Tika Release", content);
    }

}
