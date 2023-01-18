/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.wacz;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;

public class WACZParserTest extends TikaTest {

    @Test
    public void testBasic() throws Exception {
        //the embedded warc is of type warc-info so there's no real content to parse
        List<Metadata> metadataList = getRecursiveMetadata("testWACZ.wacz");
        assertEquals(2, metadataList.size());
        assertEquals("application/x-wacz", metadataList.get(0).get(Metadata.CONTENT_TYPE));
        assertEquals("application/warc", metadataList.get(1).get(Metadata.CONTENT_TYPE));
    }
}
