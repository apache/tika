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

package org.apache.tika.parser.html;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class SrcDocTest extends TikaTest {


    @Test
    public void testBasic() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testSrcDoc.html");
        debug(metadataList);
        assertEquals(2, metadataList.size());
        assertContains("outside", metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
        assertContains("this is the iframe content",
                metadataList.get(1).get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.INLINE.toString(),
                metadataList.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }
}
