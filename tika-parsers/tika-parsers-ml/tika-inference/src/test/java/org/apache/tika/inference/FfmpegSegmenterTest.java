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
package org.apache.tika.inference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.inference.MediaConfig;
import org.apache.tika.parser.inference.Modality;
import org.apache.tika.utils.ProcessUtils;

/** The grid and the probe parser need no ffmpeg; the last test synthesizes a tagged mp3. */
public class FfmpegSegmenterTest {

    @TempDir
    Path tmp;

    @Test
    public void testCells() throws Exception {
        MediaConfig config = new MediaConfig();
        List<MediaSegmenter.Cell> cells = MediaSegmenter.cells(52_000, config, Integer.MAX_VALUE);
        assertEquals(2, cells.size(), "a tail inside the previous cell's overlap is not a cell");
        assertEquals(25_000, cells.get(1).startMs());
        assertEquals(52_000, cells.get(1).endMs());
        assertEquals(1, MediaSegmenter.cells(30_000, config, Integer.MAX_VALUE).size());
        assertEquals(1, MediaSegmenter.cells(1_000, config, Integer.MAX_VALUE).size());
        assertEquals(0, MediaSegmenter.cells(0, config, Integer.MAX_VALUE).size());
        // a container may claim any duration: the cap bounds the work, not the claim
        assertEquals(3, MediaSegmenter.cells(Long.MAX_VALUE / 2, config, 3).size());
        config.getSegment().setOverlap(-5);
        assertThrows(TikaException.class, () -> MediaSegmenter.cells(52_000, config, 10),
                "a negative overlap would leave gaps");
        config.getSegment().setOverlap(30);
        assertThrows(TikaException.class, () -> MediaSegmenter.cells(52_000, config, 10));
    }

    @Test
    public void testCorrelatorAndMime() {
        MediaSegmenter.Cell cell = new MediaSegmenter.Cell(0, 25_000, 55_000);
        assertEquals("t:25000-55000", MediaSegmenter.correlator(null, cell));
        assertEquals("/1:t:25000-55000", MediaSegmenter.correlator("/1", cell),
                "an embedded document's cells are its own");
        assertEquals("audio/ogg", MediaSegmenter.mimeType(Modality.AUDIO));
        assertEquals("video/mp4", MediaSegmenter.mimeType(Modality.VISUAL));
    }

    /** Cover art is a one-frame video stream; it is not a picture channel, wherever it sits. */
    @Test
    public void testProbeParse() throws Exception {
        MediaSegmenter.Probe p = FfmpegSegmenter.parse(
                "codec_type=audio\nDISPOSITION:attached_pic=0\n"
                + "codec_type=video\nDISPOSITION:attached_pic=1\nduration=5.5\n");
        assertTrue(p.hasAudio());
        assertFalse(p.hasVideo());
        assertEquals(5500, p.durationMs());
        p = FfmpegSegmenter.parse(
                "codec_type=video\nDISPOSITION:attached_pic=0\n"
                + "codec_type=audio\nDISPOSITION:attached_pic=0\n"
                + "codec_type=video\nDISPOSITION:attached_pic=1\nduration=70\n");
        assertTrue(p.hasVideo(), "the film is still a film with a poster attached");
        assertTrue(p.hasAudio());
        p = FfmpegSegmenter.parse("codec_type=video\nDISPOSITION:attached_pic=0\nduration=1\n");
        assertTrue(p.hasVideo());
        assertFalse(p.hasAudio());
        assertThrows(TikaException.class, () -> FfmpegSegmenter.parse("codec_type=audio\nduration=N/A\n"));
        assertThrows(TikaException.class, () -> FfmpegSegmenter.parse("codec_type=audio\n"));
    }

    @Test
    public void testCoverArtIsNotVideo() throws Exception {
        FfmpegSegmenter segmenter = new FfmpegSegmenter();
        assumeTrue(segmenter.available(), "ffmpeg not on the PATH");
        Path plain = tmp.resolve("plain.mp3");
        Path cover = tmp.resolve("cover.png");
        Path tagged = tmp.resolve("cover.mp3");
        assertEquals(0, ProcessUtils.execute(new ProcessBuilder("ffmpeg", "-loglevel", "error",
                "-y", "-f", "lavfi", "-i", "sine=frequency=440:duration=5", "-c:a", "libmp3lame",
                plain.toString()), 60_000, 10_000, 10_000).getExitValue());
        assertEquals(0, ProcessUtils.execute(new ProcessBuilder("ffmpeg", "-loglevel", "error",
                "-y", "-f", "lavfi", "-i", "testsrc=duration=1:size=64x64:rate=1", "-frames:v", "1",
                cover.toString()), 60_000, 10_000, 10_000).getExitValue());
        assertEquals(0, ProcessUtils.execute(new ProcessBuilder("ffmpeg", "-loglevel", "error",
                "-y", "-i", plain.toString(), "-i", cover.toString(), "-map", "0:a", "-map", "1:v",
                "-c:a", "copy", "-c:v", "mjpeg", "-disposition:v", "attached_pic",
                tagged.toString()), 60_000, 10_000, 10_000).getExitValue());
        MediaSegmenter.Probe probe = segmenter.probe(tagged, new ParseContext());
        assertTrue(probe.hasAudio());
        assertFalse(probe.hasVideo());
        assertTrue(probe.durationMs() >= 5000);
    }
}
