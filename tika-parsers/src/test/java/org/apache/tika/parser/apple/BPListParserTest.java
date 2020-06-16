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

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;


public class BPListParserTest extends TikaTest {

    @Test
    public void testBasicBinaryPList() throws Exception {
        //test file is MIT licensed:
        // https://github.com/joeferner/node-bplist-parser/blob/master/test/iTunes-small.bplist
        List<Metadata> metadataList = getRecursiveMetadata("testBPList.bplist");
        assertEquals(21, metadataList.size());
        Metadata m = metadataList.get(0);
        assertEquals("application/x-itunes-bplist", m.get(Metadata.CONTENT_TYPE));
        String content = m.get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT);
        assertContains("<key>Application Version</key><string>9.0", content);

        //TODO -- bad encoding right after this...smart quote?
        assertContains("<string>90", content);
    }

    @Test
    public void testWebArchive() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testWEBARCHIVE.webarchive");
        assertEquals(12, metadataList.size());
        Metadata m0 = metadataList.get(0);
        assertEquals("application/x-webarchive", m0.get(Metadata.CONTENT_TYPE));
        Metadata m1 = metadataList.get(1);
        String content = m1.get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT);
        assertContains("December 2008: Apache Tika Release", content);
    }

    //TODO -- add unit tests for memgraph
}
