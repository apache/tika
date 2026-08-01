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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.drew.lang.SequentialByteArrayReader;
import com.drew.metadata.mp4.Mp4Context;
import com.drew.metadata.mp4.Mp4Directory;
import com.drew.metadata.mp4.media.Mp4MetaDirectory;
import com.drew.metadata.mp4.media.Mp4SoundDirectory;
import com.drew.metadata.mp4.media.Mp4VideoDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Audio;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.QuickTime;
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

        skipKeysB.add("tk:parsed-by");
        skipKeysA.add("tk:parse-time-millis");
        skipKeysB.add("tk:content-handler");
        skipKeysA.add("tk:content-handler");
        skipKeysB.add("tk:parse-time-millis");
        skipKeysB.add("xmpDM:videoCompressor");
        //skipKeysB.add("xmpDM:audioChannelType");
        //skipKeysB.add("xmpDM:audioChannelType");
        skipKeysA.add("tk:content");
        skipKeysB.add("tk:content");
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
        //average bitrate from the esds elementary stream descriptor
        assertEquals("256000", metadata.get(Audio.BITRATE));
        assertNull(metadata.get(Audio.HAS_DRM));
        //the totals from the trkn/disk atoms were previously read and discarded
        assertEquals("42", metadata.get(Audio.TRACK_COUNT));
        assertEquals("Test Album Artist", metadata.get(XMPDM.ALBUM_ARTIST));
        assertEquals("6", metadata.get(XMPDM.DISC_NUMBER));
        assertEquals("12", metadata.get(Audio.DISC_COUNT));
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
    @Test
    @Timeout(30000)
    public void testInfiniteLoop() throws Exception {
        XMLResult r = getXML("testMP4_truncated.m4a");
        assertEquals("audio/mp4", r.metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("M4A", r.metadata.get(XMPDM.AUDIO_COMPRESSOR));
    }

    @Test
    public void testAudioOnlyMP4() throws Exception {
        final XMLResult xmlResult = getXML("testMP4AudioOnly.mp4");
        final Metadata metadata = xmlResult.metadata;

        assertEquals("audio/mp4", metadata.get(Metadata.CONTENT_TYPE));
    }

    @Test
    public void testAudioOnlyCheck() {
        assertTrue(MP4Parser.isAudioOnly(List.of(new Mp4SoundDirectory())));
    }

    @Test
    public void testMetadataWithSoundConsideredAudio() {
        assertTrue(MP4Parser.isAudioOnly(List.of(new Mp4SoundDirectory(), new Mp4MetaDirectory())));
    }

    @Test
    public void testVideoDirectoriesNotConsideredAudio() {
        final Collection<Mp4Directory> directories =
                List.of(new Mp4VideoDirectory(), new Mp4VideoDirectory(), new Mp4SoundDirectory());

        assertFalse(MP4Parser.isAudioOnly(directories));
    }

    @Test
    public void testNoDirectoriesNotConsideredAudio() {
        assertFalse(MP4Parser.isAudioOnly(Collections.emptyList()));
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

    @Test
    public void testDrmProtectedM4a() throws Exception {
        //the sample description declares a protected 'drms' sample entry
        Metadata metadata = new Metadata();
        getText("testMP4_drm.m4a", metadata);
        assertEquals("true", metadata.get(Audio.HAS_DRM));
    }

    @Test
    public void testEsdsWithDescriptorFlags() throws Exception {
        //the ES descriptor declares the optional stream dependence, URL and
        //OCR fields, which shift the DecoderConfigDescriptor; the URL string
        //deliberately reads "sinf" so a raw fourcc scan would misreport DRM
        Metadata metadata = new Metadata();
        getText("testMP4_esdsFlags.m4a", metadata);
        assertEquals("96000", metadata.get(Audio.BITRATE));
        assertNull(metadata.get(Audio.HAS_DRM));
    }

    @Test
    public void testQuickTimeMetadataKeys() throws Exception {
        //QuickTime item-list metadata (moov/meta/keys+ilst, the com.apple.quicktime.*
        //keys such as the content identifier and ISO 6709 location) was previously
        //dropped by the MP4 handler. See TIKA-2861.
        Metadata metadata = new Metadata();
        getText("testMP4_QuickTimeMetadata.mov", metadata);
        assertEquals("TEST-UUID-0001-LIVEPHOTO",
                metadata.get("com.apple.quicktime.content.identifier"));

        //the raw ISO 6709 location is preserved ...
        assertEquals("+12.3456-098.7654+010.500/",
                metadata.get("com.apple.quicktime.location.ISO6709"));
        //... and also mapped to the standard geo:* properties (incl. altitude)
        assertEquals(12.3456, Double.parseDouble(metadata.get(TikaCoreProperties.LATITUDE)), 0.00001);
        assertEquals(-98.7654, Double.parseDouble(metadata.get(TikaCoreProperties.LONGITUDE)), 0.00001);
        assertEquals(10.5, Double.parseDouble(metadata.get(TikaCoreProperties.ALTITUDE)), 0.00001);

        //numeric well-known value types (uint8, float32, int32, float64)
        assertEquals("1", metadata.get("com.apple.quicktime.live-photo.auto"));
        assertEquals("0.75", metadata.get("com.apple.quicktime.live-photo.vitality-score"));
        assertEquals("-13",
                metadata.get("com.apple.quicktime.camera.focal_length.35mm_equivalent"));
        assertEquals("1.5",
                metadata.get("com.apple.quicktime.full-frame-rate-playback-intent"));

        //the Live Photo still moment: presentation time of the single sample of
        //the timed metadata track declaring still-image-time (mebx, leading empty
        //edit of 740/600s). TIKA-4777
        assertEquals("1233333", metadata.get(QuickTime.STILL_IMAGE_TIME));
        //foreign mebx keys get no property (the fixture's other timed metadata
        //tracks are delayed, non-leading and multi-sample variants), and the
        //per-key suffix scheme from earlier iterations is gone
        assertNull(metadata.get("com.apple.quicktime.still-image-time.track-start-us"));
        assertNull(metadata.get("test.quicktime.v1delayed.track-start-us"));
        assertNull(metadata.get("test.quicktime.nonleading.track-start-us"));
        assertNull(metadata.get("test.quicktime.multisample.track-start-us"));
    }

    @Test
    public void testStillImageTimeZero() throws Exception {
        //a declared but undelayed single-sample still-image-time track (version 1
        //edit list with only a media edit): 0 = the still is the first frame,
        //distinguishable from "no Live Photo" (absent). The track follows a
        //delayed foreign-key track, so a leaked empty-edit duration would show
        //up as a non-zero value here. TIKA-4777
        Metadata metadata = new Metadata();
        getText("testMP4_StillImageTimeZero.mov", metadata);
        assertEquals("0", metadata.get(QuickTime.STILL_IMAGE_TIME));
    }

    @Test
    public void testStsdEntrySizeOverflow() throws Exception {
        //a crafted sample description declaring entry size 0xFFFFFFFF used to
        //turn negative in the int cast and escape parse() as a
        //NegativeArraySizeException; the handler must treat it as malformed
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(new byte[]{0, 0, 0, 0}); //version and flags
        bos.write(new byte[]{0, 0, 0, 1}); //entry count
        bos.write(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
        bos.write("mebx".getBytes(StandardCharsets.ISO_8859_1));

        Metadata tikaMetadata = new Metadata();
        TikaMp4MetaHandler handler = new TikaMp4MetaHandler(new com.drew.metadata.Metadata(),
                new Mp4Context(), tikaMetadata, 740, 600);
        handler.processSampleDescription(new SequentialByteArrayReader(bos.toByteArray()));
        assertNull(tikaMetadata.get(QuickTime.STILL_IMAGE_TIME));
    }

    @Test
    public void testUdtaLocation() throws Exception {
        //the udta "(c)xyz" ISO 6709 location is mapped to geo:lat/geo:long, and its
        //optional altitude, which was previously dropped, to geo:alt. See TIKA-2861.
        Metadata metadata = new Metadata();
        getText("testMP4_udtaLocation.mp4", metadata);
        assertEquals(12.3456, Double.parseDouble(metadata.get(TikaCoreProperties.LATITUDE)), 0.00001);
        assertEquals(-98.7654, Double.parseDouble(metadata.get(TikaCoreProperties.LONGITUDE)), 0.00001);
        assertEquals(10.5, Double.parseDouble(metadata.get(TikaCoreProperties.ALTITUDE)), 0.00001);

        //the fixture's disk atom uses the padded 8-byte form; the title after
        //it proves the ilst walk consumes exactly the declared length
        assertEquals("6", metadata.get(XMPDM.DISC_NUMBER));
        assertEquals("12", metadata.get(Audio.DISC_COUNT));
        assertEquals("Test Title", metadata.get(TikaCoreProperties.TITLE));
    }
}
