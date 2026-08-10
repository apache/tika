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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
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
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Audio;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.QuickTime;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.Video;
import org.apache.tika.metadata.XMP;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;


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
        assertEquals("2", metadata.get(Audio.CHANNELS));
        assertEquals("16", metadata.get(Audio.BITS_PER_SAMPLE));
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

    /**
     * Test that cover art in the covr atom becomes an embedded document,
     * with no extra metadata on the audio document itself
     */
    @Test
    public void testMP4CoverArt() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testMP4_coverArt.m4a");

        assertEquals(2, metadataList.size());
        assertEquals("audio/mp4", metadataList.get(0).get(Metadata.CONTENT_TYPE));

        Metadata pictureMetadata = metadataList.get(1);
        assertEquals("image/png", pictureMetadata.get(Metadata.CONTENT_TYPE));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.INLINE.toString(),
                pictureMetadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    /**
     * Test that a covr entry with several data atoms, one image each,
     * yields one embedded document per image, in file order
     */
    @Test
    public void testMP4MultipleCovers() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testMP4_twoCovers.m4a");

        assertEquals(3, metadataList.size());
        //a png data atom (well-known type 14) followed by a jpeg one (13)
        Metadata front = metadataList.get(1);
        assertEquals("image/png", front.get(Metadata.CONTENT_TYPE));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.INLINE.toString(),
                front.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        Metadata back = metadataList.get(2);
        assertEquals("image/jpeg", back.get(Metadata.CONTENT_TYPE));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.INLINE.toString(),
                back.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    // TODO Test an old QuickTime Video File
    @Test
    public void testVideoFrameRate() throws Exception {
        // a 10 fps H.264 clip generated with ffmpeg (color source, 16x16, 1s);
        // libx264 also writes the average bitrate into the btrt BitRateBox
        XMLResult r = getXML("testMP4Video.mp4");
        assertEquals("video/mp4", r.metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("10.0", r.metadata.get(Video.FRAME_RATE));
        assertEquals("6536", r.metadata.get(Video.BITRATE));
    }

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
    public void testStsdNestedWaveRecursion() throws Exception {
        //a crafted sound sample description whose child boxes are a deep chain of
        //nested 'wave' boxes used to recurse in findEsdsAverageBitRate until the
        //stack overflowed (an uncaught Error, not caught by Mp4Reader or
        //CompositeParser); the handler must bound the box nesting depth. TIKA-4812
        int depth = 100_000;
        int childLen = depth * 8;
        ByteBuffer buf = ByteBuffer.allocate(44 + childLen); //big-endian by default
        buf.putInt(0);             //version and flags
        buf.putInt(1);             //entry count
        buf.putInt(36 + childLen); //sample entry size
        buf.put("mp4a".getBytes(StandardCharsets.ISO_8859_1));
        buf.position(44);          //leave the 28 fixed sound fields zero (version 0 -> 36 byte entry)
        for (int k = 0; k < depth; k++) {
            buf.putInt(8 * (depth - k)); //'wave' box size, shrinking to the chain end
            buf.put("wave".getBytes(StandardCharsets.ISO_8859_1));
        }

        Metadata tikaMetadata = new Metadata();
        TikaMp4SoundHandler handler = new TikaMp4SoundHandler(new com.drew.metadata.Metadata(),
                new Mp4Context(), tikaMetadata);
        //must return without a StackOverflowError, and find no bitrate
        handler.processBox("stsd", buf.array(), buf.array().length, new Mp4Context());
        assertNull(tikaMetadata.get(Audio.BITRATE));
    }

    @Test
    public void testOversizedBoxIsSkippedNotAllocated() throws Exception {
        //an accepted box whose declared payload exceeds the cap must be skipped (a
        //lazy stream advance, no allocation) rather than read into a byte[]; the
        //metadata-extractor reader would instead do new byte[(int) boxSize - 8].
        //Use ftyp, an accepted top-level box with an observable side effect (the
        //major brand). Payload is 16 bytes. TIKA-4812
        byte[] ftyp = ftypBox();
        assertNull(majorBrand(ftyp, 8L));            //cap below the payload -> skipped
        assertEquals("isom", majorBrand(ftyp, 1000L)); //cap above it -> read and processed
    }

    @Test
    public void testNestedContainerRecursionIsBounded() throws Exception {
        //a crafted chain of nested container boxes (moov is accepted as a container)
        //used to recurse in TikaMp4Reader.processBoxes until the stack overflowed (an
        //uncaught Error); the reader must bound the nesting depth. TIKA-4812
        int depth = 100_000;
        ByteBuffer buf = ByteBuffer.allocate(depth * 8); //big-endian by default
        for (int k = 0; k < depth; k++) {
            buf.putInt(8 * (depth - k)); //moov box size, shrinking to the chain end
            buf.put("moov".getBytes(StandardCharsets.ISO_8859_1));
        }
        byte[] boxes = buf.array();
        TikaMp4BoxHandler handler = new TikaMp4BoxHandler(new com.drew.metadata.Metadata(),
                new Metadata(), new XHTMLContentHandler(new DefaultHandler(), new Metadata()),
                new ParseContext());
        //must return without a StackOverflowError
        assertDoesNotThrow(() ->
                TikaMp4Reader.extract(new ByteArrayInputStream(boxes), handler, 1000L,
                        boxes.length));
    }

    @Test
    public void testLargeSizeBoxHeaderAccounting() throws Exception {
        //a 64-bit largesize box (size field == 1) has a 16-byte header, not 8; accounting
        //for only 8 over-reads it by 8 bytes and misparses everything after it. Put a
        //largesize udta before a normal ftyp and confirm the ftyp's major brand still
        //comes through, which it only does if the largesize header is 16 bytes. TIKA-4812
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        //box 1: largesize udta, 24 bytes total (16-byte header + 8-byte payload)
        bos.write(new byte[]{0, 0, 0, 1});              //size == 1 -> a 64-bit size follows
        bos.write("udta".getBytes(StandardCharsets.ISO_8859_1));
        bos.write(new byte[]{0, 0, 0, 0, 0, 0, 0, 24}); //64-bit box size = 24
        bos.write(new byte[]{0, 0, 0, 8});              //payload: a dummy 8-byte 'free' sub-box
        bos.write("free".getBytes(StandardCharsets.ISO_8859_1));
        //box 2: normal ftyp, 16 bytes
        bos.write(new byte[]{0, 0, 0, 16});
        bos.write("ftyp".getBytes(StandardCharsets.ISO_8859_1));
        bos.write("isom".getBytes(StandardCharsets.ISO_8859_1)); //major brand
        bos.write(new byte[]{0, 0, 0, 0});              //minor version

        assertEquals("isom", majorBrand(bos.toByteArray(), 1000L));
    }

    private static String majorBrand(byte[] boxes, long maxBoxSize) throws Exception {
        com.drew.metadata.Metadata mp4Metadata = new com.drew.metadata.Metadata();
        Metadata tikaMetadata = new Metadata();
        XHTMLContentHandler xhtml = new XHTMLContentHandler(new DefaultHandler(), tikaMetadata);
        TikaMp4BoxHandler handler =
                new TikaMp4BoxHandler(mp4Metadata, tikaMetadata, xhtml, new ParseContext());
        TikaMp4Reader.extract(new ByteArrayInputStream(boxes), handler, maxBoxSize, boxes.length);
        Mp4Directory dir = mp4Metadata.getFirstDirectoryOfType(Mp4Directory.class);
        return dir == null ? null : dir.getString(Mp4Directory.TAG_MAJOR_BRAND);
    }

    private static byte[] ftypBox() {
        ByteBuffer buf = ByteBuffer.allocate(24); //big-endian by default
        buf.putInt(24);            //box size
        buf.put("ftyp".getBytes(StandardCharsets.ISO_8859_1));
        buf.put("isom".getBytes(StandardCharsets.ISO_8859_1)); //major brand
        buf.putInt(0);             //minor version
        buf.put("mp41".getBytes(StandardCharsets.ISO_8859_1)); //compatible brand
        buf.put("mp42".getBytes(StandardCharsets.ISO_8859_1)); //compatible brand
        return buf.array();
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
