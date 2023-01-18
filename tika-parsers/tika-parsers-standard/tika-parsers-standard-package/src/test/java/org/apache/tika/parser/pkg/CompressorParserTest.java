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

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class CompressorParserTest extends TikaTest {


    @Test
    public void testLZ4Framed() throws Exception {
        XMLResult r = getXML("testLZ4-framed.lz4");
        assertEquals("application/x-lz4", r.metadata.get(Metadata.CONTENT_TYPE));
        assertContains("0123456789", r.xml);
    }

    @Test
    public void testZstd() throws Exception {
        XMLResult r = getXML("testZSTD.zst");
        assertContains("0123456789", r.xml);
    }

    @Test
    public void testSnappyFramed() throws Exception {
        XMLResult r = getXML("testSnappy-framed.sz");
        assertEquals("application/x-snappy", r.metadata.get(Metadata.CONTENT_TYPE));
        assertContains("Lorem ipsum dolor sit amet", r.xml);
    }

    @Test
    public void testBrotli() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "testBROTLI_compressed.br");
        List<Metadata> metadataList = getRecursiveMetadata("testBROTLI_compressed.br", metadata);

        assertContains("XXXXXXXXXXYYYYYYYYYY",
                metadataList.get(1).get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("testBROTLI_compressed",
                metadataList.get(1).get(TikaCoreProperties.RESOURCE_NAME_KEY));
    }
}
