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

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.junit.BeforeClass;
import org.junit.Test;

public class CompressorParserTest extends TikaTest {
    //These compressed stream types can't currently
    //be detected.
    private static Set<MediaType> NOT_COVERED = new HashSet();

    @BeforeClass
    public static void setUp() {
        NOT_COVERED.add(MediaType.application("x-lz4-block"));
        NOT_COVERED.add(MediaType.application("x-snappy-raw"));
        NOT_COVERED.add(MediaType.application("deflate64"));
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
    public void testZstd() throws Exception {
        XMLResult r = getXML("testZSTD.zstd");
        assertContains("0123456789", r.xml);
    }

    @Test
    public void testBrotli() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, "testBROTLI_compressed.br");
        List<Metadata> metadataList = getRecursiveMetadata("testBROTLI_compressed.br", metadata);

        assertContains("XXXXXXXXXXYYYYYYYYYY", metadataList.get(1).get(RecursiveParserWrapper.TIKA_CONTENT));
        assertEquals("testBROTLI_compressed", metadataList.get(1).get(Metadata.RESOURCE_NAME_KEY));
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

    @Test(expected = TikaException.class)
    public void testQuineXHTML() throws Exception {
        //https://blog.matthewbarber.io/2019/07/22/how-to-make-compressed-file-quines
        getXML("quine.gz");
    }

    @Test
    public void testQuineRecursive() throws Exception {
        //https://blog.matthewbarber.io/2019/07/22/how-to-make-compressed-file-quines
        getRecursiveMetadata("quine.gz");
    }
}
