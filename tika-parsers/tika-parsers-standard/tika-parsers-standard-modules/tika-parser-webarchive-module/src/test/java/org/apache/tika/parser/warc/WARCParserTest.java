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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.sax.BasicContentHandlerFactory;

public class WARCParserTest extends TikaTest {

    // the cc.warc.gz and gzip_extra_sl.warc.gz and the testARC.arc files come
    // from the jwarc unit tests.

    @Test
    public void testBasic() throws Exception {

        List<Metadata> metadataList = getRecursiveMetadata("cc.warc.gz");
        assertEquals(2, metadataList.size());
        assertEquals("application/warc+gz", metadataList.get(0).get(Metadata.CONTENT_TYPE));
        assertContains("text/html", metadataList.get(1).get(Metadata.CONTENT_TYPE));
        assertContains("Common Crawl on Twitter", metadataList.get(1).get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("<urn:uuid:c3f02271-44d2-4159-9cdb-3e3efeb16ba0>",
                metadataList.get(1).get("warc:WARC-Warcinfo-ID"));
        assertEquals("http://commoncrawl.org/",
                metadataList.get(1).get("warc:WARC-Target-URI"));
    }

    @Test
    public void testMultipleRecords() throws Exception {
        //TIKA-
        List<Metadata> metadataList = getRecursiveMetadata("testWARC_multiple.warc",
                BasicContentHandlerFactory.HANDLER_TYPE.TEXT);
        List<Metadata> gzMetadataList = getRecursiveMetadata("testWARC_multiple.warc.gz",
                BasicContentHandlerFactory.HANDLER_TYPE.TEXT);

        Set<String> fieldsToIgnore = new HashSet<>();
        fieldsToIgnore.add("X-TIKA:parse_time_millis");
        fieldsToIgnore.add("Content-Type");
        assertMetadataListEquals(metadataList, gzMetadataList, fieldsToIgnore);

        assertEquals("application/warc", metadataList.get(0).get(Metadata.CONTENT_TYPE));
        assertEquals("application/warc+gz", gzMetadataList.get(0).get(Metadata.CONTENT_TYPE));
    }

    @Test
    public void testARC() throws Exception {
        //test file comes from:
        // https://github.com/iipc/jwarc/blob/master/test/org/netpreserve/jwarc/apitests/ArcTest.java

        List<Metadata> metadataList = getRecursiveMetadata("testARC.arc",
                BasicContentHandlerFactory.HANDLER_TYPE.TEXT);
        assertEquals(2, metadataList.size());
        assertContains("The document has moved here",
                metadataList.get(1).get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("http://www.uq.edu.au/robots.txt",
                metadataList.get(1).get("warc:WARC-Target-URI"));
        assertEquals("http://www.uq.edu.au/",
                metadataList.get(1).get("warc:http:Location"));
    }

    @Test
    public void testArcGZ() throws Exception {
        //test file from https://github.com/webrecorder/warcio/blob/master/test/data/example.arc.gz
        List<Metadata> metadataList = getRecursiveMetadata("example.arc.gz",
                BasicContentHandlerFactory.HANDLER_TYPE.TEXT);
        assertEquals(2, metadataList.size());
        assertEquals("application/arc+gz", metadataList.get(0).get(Metadata.CONTENT_TYPE));
        assertContains("This domain is established",
                metadataList.get(1).get(TikaCoreProperties.TIKA_CONTENT));

        //TODO -- we should try to find an example gz with multiple arcs
    }
}
