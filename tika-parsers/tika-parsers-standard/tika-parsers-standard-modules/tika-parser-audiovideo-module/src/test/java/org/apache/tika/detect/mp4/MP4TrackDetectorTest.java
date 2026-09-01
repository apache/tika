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
package org.apache.tika.detect.mp4;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * An MP4 is typed by the tracks it holds, not by its brand: Tika's own
 * fixtures for video and for audio both carry the isom brand (TIKA-3646).
 */
public class MP4TrackDetectorTest extends TikaTest {

    private final MP4TrackDetector detector = new MP4TrackDetector();

    @ParameterizedTest
    @CsvSource({"testMP4Video.mp4, video/mp4", "testMP4AudioOnly.mp4, audio/mp4"})
    public void testTypeFollowsTheTracks(String file, String expected) throws Exception {
        try (InputStream is = getResourceAsStream("/test-documents/" + file);
             TikaInputStream tis = TikaInputStream.get(is)) {
            assertEquals(expected, detect(tis));
        }
    }

    /**
     * The movie box may sit behind the media data; walking the box sizes
     * skips over that rather than reading it.
     */
    @Test
    public void testMovieBoxAfterTheMediaData(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("moov-last.mp4");
        Files.write(file, mp4(box("free", new byte[8]), mdat(1024 * 1024), moov("soun")));
        try (TikaInputStream tis = TikaInputStream.get(file)) {
            assertEquals("audio/mp4", detect(tis));
        }
    }

    /**
     * Without a file to seek in, only a movie box near the start is found;
     * anything further in is left to the magic.
     */
    @Test
    public void testMovieBoxBeyondTheStreamPrefix() throws Exception {
        byte[] mp4 = mp4(mdat(1024 * 1024), moov("soun"));
        try (TikaInputStream tis = TikaInputStream.get(mp4)) {
            assertEquals("application/octet-stream", detect(tis));
        }
    }

    /**
     * A video track anywhere in the movie makes it a video.
     */
    @Test
    public void testAudioAndVideoTracks() throws Exception {
        byte[] mp4 = mp4(box("moov", concat(track("soun"), track("vide"))));
        try (TikaInputStream tis = TikaInputStream.get(mp4)) {
            assertEquals("video/mp4", detect(tis));
        }
    }

    /**
     * A movie whose tracks are neither audio nor video is neither.
     */
    @Test
    public void testTrackless() throws Exception {
        byte[] mp4 = mp4(moov("hint"));
        try (TikaInputStream tis = TikaInputStream.get(mp4)) {
            assertEquals("application/mp4", detect(tis));
        }
    }

    /**
     * The handler of the metadata is not a track handler: it lives in
     * moov/udta/meta, not in moov/trak/mdia.
     */
    @Test
    public void testMetadataHandlerIsNotATrack() throws Exception {
        byte[] meta = box("udta", box("meta", hdlr("vide")));
        byte[] mp4 = mp4(box("moov", concat(track("soun"), meta)));
        try (TikaInputStream tis = TikaInputStream.get(mp4)) {
            assertEquals("audio/mp4", detect(tis));
        }
    }

    /**
     * Brands that name a format of their own keep their own magic, and a
     * file without a reachable movie box is left to it as well.
     */
    @ParameterizedTest
    @CsvSource({"M4A , true", "heic, true", "isom, false"})
    public void testUnclaimedFiles(String brand, boolean hasMoov) throws Exception {
        byte[] mp4 = hasMoov ? mp4(brand, moov("soun")) : mp4(brand, mdat(64));
        try (TikaInputStream tis = TikaInputStream.get(mp4)) {
            assertEquals("application/octet-stream", detect(tis));
        }
    }

    /**
     * Every truncation of a well formed file, and random bytes behind a
     * valid header, must end the walk rather than the detection.
     */
    @Test
    public void testMalformedFilesAreHarmless() throws Exception {
        byte[] mp4 = mp4(box("free", new byte[8]), mdat(64), moov("soun"));
        for (int length = 0; length <= mp4.length; length++) {
            try (TikaInputStream tis = TikaInputStream.get(Arrays.copyOf(mp4, length))) {
                detect(tis);
            }
        }
        Random random = new Random(42);
        byte[] header = mp4();
        for (int i = 0; i < 200; i++) {
            byte[] noise = new byte[256];
            random.nextBytes(noise);
            System.arraycopy(header, 0, noise, 0, Math.min(header.length, noise.length));
            try (TikaInputStream tis = TikaInputStream.get(noise)) {
                detect(tis);
            }
        }
    }

    /**
     * A box declaring more than the file holds ends the walk.
     */
    @Test
    public void testOversizedBox() throws Exception {
        byte[] mp4 = mp4(box("mdat", new byte[8]));
        int mdat = mp4.length - 16;
        mp4[mdat] = 0x7F;
        mp4[mdat + 1] = (byte) 0xFF;
        mp4[mdat + 2] = (byte) 0xFF;
        mp4[mdat + 3] = (byte) 0xFF;
        try (TikaInputStream tis = TikaInputStream.get(mp4)) {
            assertEquals("application/octet-stream", detect(tis));
        }
    }

    private String detect(TikaInputStream tis) throws IOException {
        return detector.detect(tis, new Metadata(), new ParseContext()).toString();
    }

    private static byte[] mp4(byte[]... boxes) throws IOException {
        return mp4("isom", boxes);
    }

    private static byte[] mp4(String brand, byte[]... boxes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(box("ftyp", (brand + "    " + brand).getBytes(StandardCharsets.US_ASCII)));
        for (byte[] b : boxes) {
            out.write(b);
        }
        return out.toByteArray();
    }

    private static byte[] mdat(int size) throws IOException {
        return box("mdat", new byte[size]);
    }

    private static byte[] moov(String handler) throws IOException {
        return box("moov", track(handler));
    }

    /**
     * A track box holding the handler where a real one has it: trak, mdia,
     * hdlr.
     */
    private static byte[] track(String handler) throws IOException {
        return box("trak", box("mdia", hdlr(handler)));
    }

    private static byte[] hdlr(String handler) throws IOException {
        return box("hdlr",
                concat(new byte[8], handler.getBytes(StandardCharsets.US_ASCII), new byte[12]));
    }

    private static byte[] box(String type, byte[] payload) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int size = 8 + payload.length;
        out.write(new byte[]{(byte) (size >>> 24), (byte) (size >>> 16), (byte) (size >>> 8),
                (byte) size});
        out.write(type.getBytes(StandardCharsets.US_ASCII));
        out.write(payload);
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.write(p);
        }
        return out.toByteArray();
    }
}
