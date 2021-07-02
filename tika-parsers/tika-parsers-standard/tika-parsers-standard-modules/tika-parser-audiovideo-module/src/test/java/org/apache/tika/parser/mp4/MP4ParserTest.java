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
package org.apache.tika.parser.mp4;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMP;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Test case for parsing mp4 files.
 */
public class MP4ParserTest extends TikaTest {

    Set<String> skipKeysA = new HashSet<>();
    Set<String> skipKeysB = new HashSet<>();

    /*
    @Before
    public void setUp() {

        skipKeysB.add("X-TIKA:Parsed-By");
        skipKeysA.add("X-TIKA:parse_time_millis");
        skipKeysB.add("X-TIKA:content_handler");
        skipKeysA.add("X-TIKA:content_handler");
        skipKeysB.add("X-TIKA:parse_time_millis");
        skipKeysB.add("xmpDM:videoCompressor");
        //skipKeysB.add("xmpDM:audioChannelType");
        //skipKeysB.add("xmpDM:audioChannelType");
        skipKeysA.add("X-TIKA:content");
        skipKeysB.add("X-TIKA:content");
        skipKeysB.add("xmpDM:copyright");
    }*/
    /**
     * Test that we can extract information from
     * a M4A MP4 Audio file
     */
    @Test
    public void testMP4ParsingAudio() throws Exception {
        Metadata metadata = new Metadata();
        String content = getText("testMP4.m4a", metadata);

        // Check core properties
        assertEquals("audio/mp4", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Test Title", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Test Artist", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("2012-01-28T18:39:18Z", metadata.get(TikaCoreProperties.CREATED));
        assertEquals("2012-01-28T18:40:25Z", metadata.get(TikaCoreProperties.MODIFIED));

        // Check the textual contents
        assertContains("Test Title", content);
        assertContains("Test Artist", content);
        assertContains("Test Album", content);
        assertContains("2008", content);
        assertContains("Test Comment", content);
        assertContains("Test Genre", content);

        // Check XMPDM-typed audio properties
        assertEquals("Test Album", metadata.get(XMPDM.ALBUM));
        assertEquals("Test Artist", metadata.get(XMPDM.ARTIST));
        assertEquals("Test Composer", metadata.get(XMPDM.COMPOSER));
        assertEquals("2008", metadata.get(XMPDM.RELEASE_DATE));
        assertEquals("Test Genre", metadata.get(XMPDM.GENRE));
        assertEquals("Test Comments", metadata.get(XMPDM.LOG_COMMENT.getName()));
        assertEquals("1", metadata.get(XMPDM.TRACK_NUMBER));
        assertEquals("Test Album Artist", metadata.get(XMPDM.ALBUM_ARTIST));
        assertEquals("6", metadata.get(XMPDM.DISC_NUMBER));
        assertEquals("0", metadata.get(XMPDM.COMPILATION));


        assertEquals("44100", metadata.get(XMPDM.AUDIO_SAMPLE_RATE));
        assertEquals("Stereo", metadata.get(XMPDM.AUDIO_CHANNEL_TYPE));
        assertEquals("M4A", metadata.get(XMPDM.AUDIO_COMPRESSOR));
        assertEquals("0.07", metadata.get(XMPDM.DURATION));

        assertEquals("iTunes 10.5.3.3", metadata.get(XMP.CREATOR_TOOL));

        assertContains("org.apache.tika.parser.mp4.MP4Parser",
                Arrays.asList(metadata.getValues(TikaCoreProperties.TIKA_PARSED_BY)));

        // Check again by file, rather than stream
        TikaInputStream tstream =
                TikaInputStream.get(getResourceAsStream("/test-documents/testMP4.m4a"));
        tstream.getFile();
        ContentHandler handler = new BodyContentHandler();
        try {
            AUTO_DETECT_PARSER.parse(tstream, handler, metadata, new ParseContext());
        } finally {
            tstream.close();
        }
        //TODO: why don't we check the output here?
    }

    // TODO Test a MP4 Video file
    // TODO Test an old QuickTime Video File
    @Test(timeout = 30000)
    public void testInfiniteLoop() throws Exception {
        XMLResult r = getXML("testMP4_truncated.m4a");
        assertEquals("audio/mp4", r.metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("M4A", r.metadata.get(XMPDM.AUDIO_COMPRESSOR));
    }

/*

    @Test
    public void compareMetadata() throws Exception {
        Path dir = Paths.get("/data/mp4s");
        processDir(dir);

    }

    private void processDir(Path dir) {
        for (File f : dir.toFile().listFiles()) {
            if (f.isDirectory()) {
                processDir(f.toPath());
            } else {

                if (! f.getName().contains("MB3EOKALN337SEYQE6WXIGMY5VQ2ZU7M")) {
                   // continue;
                }
                System.out.println(f);
                processFile(f.toPath());
                System.out.println("");
            }
        }
    }

    private void processFile(Path p) {

        Metadata a;
        Metadata b;
        try {
            List<Metadata> metadataList = getRecursiveMetadata(p, new LegacyMP4Parser(), true);
            if (metadataList.size() > 0) {
                a = metadataList.get(0);
            } else {
                System.out.println("a is empty");
                return;
            }
        } catch (AssertionError | Exception e) {
            e.printStackTrace();
            return;
        }

        try {
            List<Metadata> metadataList = getRecursiveMetadata(p);
            if (metadataList.size() > 0) {
                b = metadataList.get(0);
            } else {
                System.out.println("b is empty");
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        compare(p, a, b);
    }

    private void compare(Path p, Metadata a, Metadata b) {
       /* System.out.println("A");
        debug(a);
        System.out.println("B");
        debug(b);
        Set<String> aKeys = getKeys(a, skipKeysA);
        Set<String> bKeys = getKeys(b, skipKeysB);
        for (String k : aKeys) {
            if (! bKeys.contains(k)) {
                System.out.println("not in b: " + k + " : " + a.get(k) + " : " +
                                p.getFileName().toString());
            }
        }
        for (String k : bKeys) {
            if (!aKeys.contains(k)) {
                System.out.println("not in a: " + k + " : " + b.get(k) + " : " +
                        p.getFileName().toString());
            }
        }
        for (String k : aKeys) {
            if (! bKeys.contains(k)) {
                continue;
            }
            Set<String> aVals = getVals(a, k);
            Set<String> bVals = getVals(b, k);
            for (String v : aVals) {
                if (!bVals.contains(v)) {
                    System.out.println("b missing value: " + v + " for key " + k + " in " + p.getFileName().toString());
                    for (String bVal : bVals) {
                        System.out.println("\tb has " + bVal);
                    }
                }
            }
        }
    }

    private Set<String> getKeys(Metadata m, Set<String> skipFields) {
        Set<String> keys = new HashSet<>();
        for (String n : m.names()) {
            if (! skipFields.contains(n)) {
                keys.add(n);
            }
        }
        return keys;

    }

    private Set<String> getVals(Metadata m, String k) {
        Set<String> vals = new HashSet<>();
        for (String v : m.getValues(k)) {
            vals.add(v);
        }
        return vals;
    } */
}
