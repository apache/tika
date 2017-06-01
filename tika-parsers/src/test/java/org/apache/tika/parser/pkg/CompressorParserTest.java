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


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.junit.BeforeClass;
import org.junit.Test;

public class CompressorParserTest extends TikaTest {
    //These compressed stream types can't currently
    //be detected.
    private static Set<MediaType> NOT_COVERED = new HashSet();

    @BeforeClass
    public static void setUp() {
        NOT_COVERED.add(MediaType.application("x-brotli"));
        NOT_COVERED.add(MediaType.application("x-lz4-block"));
        NOT_COVERED.add(MediaType.application("x-snappy-raw"));
    }

    @Test
    public void testSnappyFramed() throws Exception {
        XMLResult r = getXML("testSnappy-framed.sz");
        assertEquals("application/x-snappy", r.metadata.get(Metadata.CONTENT_TYPE));
        assertContains("Lorem ipsum dolor sit amet", r.xml);
    }

    @Test
    public void testLZ4Framed() throws Exception {
        XMLResult r = getXML("testLZ4-framed.lz4");
        assertEquals("application/x-lz4", r.metadata.get(Metadata.CONTENT_TYPE));
        //xml parser throws an exception for test1.xml
        //for now, be content that the container file is correctly identified
        assertContains("test1.xml", r.xml);
    }

    @Test
    public void testCoverage() throws Exception {
        //test that the package parser covers all inputstreams handled
        //by CompressorStreamFactory.  When we update commons-compress, and they add
        //a new stream type, we want to make sure that we're handling it.
        CompressorStreamFactory archiveStreamFactory = new CompressorStreamFactory(true, 1000);
        CompressorParser compressorParser = new CompressorParser();
        ParseContext parseContext = new ParseContext();
        for (String name : archiveStreamFactory.getInputStreamCompressorNames()) {
            MediaType mt = CompressorParser.getMediaType(name);
            if (NOT_COVERED.contains(mt)) {
                continue;
            }
            //use this instead of assertNotEquals so that we report the
            //name of the missing stream
            if (mt.equals(MediaType.OCTET_STREAM)) {
                fail("getting octet-stream for: "+name);
            }

            if (! compressorParser.getSupportedTypes(parseContext).contains(mt)) {
                fail("CompressorParser should support: "+mt.toString());
            }
        }
    }
}
