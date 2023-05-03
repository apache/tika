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
package org.apache.tika.parser.warc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class WARCParserTest extends TikaTest {

    // the cc.warc.gz and gzip_extra_sl.warc.gz files come
    // from the jwarc unit tests.

    @Test
    public void testBasic() throws Exception {

        List<Metadata> metadataList = getRecursiveMetadata("cc.warc.gz");
        assertEquals(3, metadataList.size());
        assertContains("text/html", metadataList.get(1).get(Metadata.CONTENT_TYPE));
        assertContains("Common Crawl on Twitter", metadataList.get(1).get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("application/warc", metadataList.get(2).get(Metadata.CONTENT_TYPE));
        assertEquals("<urn:uuid:c3f02271-44d2-4159-9cdb-3e3efeb16ba0>",
                metadataList.get(1).get("warc:WARC-Warcinfo-ID"));
        assertEquals("http://commoncrawl.org/",
                metadataList.get(1).get("warc:WARC-Target-URI"));

    }
}
