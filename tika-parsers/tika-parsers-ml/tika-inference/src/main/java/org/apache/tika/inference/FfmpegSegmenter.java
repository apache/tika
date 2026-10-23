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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.inference.Modality;
import org.apache.tika.utils.FileProcessResult;
import org.apache.tika.utils.ProcessUtils;

/**
 * {@link MediaSegmenter} on ffmpeg and ffprobe from the PATH. Cut files are small on purpose:
 * mono Opus for audio, silent downscaled H.264 for video, since they travel base64 in JSON.
 */
final class FfmpegSegmenter implements MediaSegmenter {

    // per call; ProcessUtils clips both to what remains of the parse's total timeout
    private static final long PROBE_TIMEOUT_MS = 60_000;
    private static final long CUT_TIMEOUT_MS = 300_000;
    // ffprobe prints every stream before the format's duration: room for thousands
    private static final int PROBE_BUFFER = 1_000_000;
    private static final int CUT_BUFFER = 20_000;

    private static volatile Boolean available;

    /** Whether ffmpeg and ffprobe answer on the PATH; probed once per JVM, outside any parse budget. */
    @Override
    public boolean available() {
        Boolean known = available;
        if (known == null) {
            known = answers("ffmpeg") && answers("ffprobe");
            available = known;
        }
        return known;
    }

    private static boolean answers(String tool) {
        try {
            FileProcessResult r = ProcessUtils.execute(
                    new ProcessBuilder(tool, "-version"), 10_000, 1000, 1000);
            return r.getExitValue() == 0;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public Probe probe(Path media, ParseContext context) throws IOException, TikaException {
        ProcessBuilder pb = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries",
                "format=duration:stream=codec_type:stream_disposition=attached_pic",
                "-of", "default=nw=1", media.toAbsolutePath().toString());
        FileProcessResult r = ProcessUtils.execute(pb, context, PROBE_TIMEOUT_MS, PROBE_BUFFER,
                CUT_BUFFER);
        if (r.isTimeout()) {
            throw new Timeout("ffprobe ran out of the parse's time budget after "
                    + r.getProcessTimeMillis() + " ms");
        }
        if (r.getExitValue() != 0) {
            throw new TikaException("ffprobe failed: " + r.getStderr());
        }
        if (r.isStdoutTruncated()) {
            throw new TikaException("ffprobe output over " + PROBE_BUFFER + " chars");
        }
        return parse(r.getStdout());
    }

    /** One stream at a time: a cover-art stream is not a picture channel, whichever stream it is. */
    static Probe parse(String stdout) throws TikaException {
        double seconds = -1;
        boolean audio = false;
        boolean video = false;
        boolean streamIsVideo = false;
        boolean streamIsCover = false;
        for (String line : stdout.split("\\R")) {
            if (line.startsWith("codec_type=")) {
                video |= streamIsVideo && !streamIsCover;
                streamIsVideo = line.equals("codec_type=video");
                streamIsCover = false;
                audio |= line.equals("codec_type=audio");
            } else if (line.equals("DISPOSITION:attached_pic=1")) {
                streamIsCover = true;
            } else if (line.startsWith("duration=")) {
                try {
                    seconds = Double.parseDouble(line.substring("duration=".length()));
                } catch (NumberFormatException e) {
                    // "N/A" for streams without a container duration
                }
            }
        }
        video |= streamIsVideo && !streamIsCover;
        if (seconds <= 0 || Double.isNaN(seconds) || Double.isInfinite(seconds)) {
            throw new TikaException("ffprobe found no duration");
        }
        return new Probe(Math.round(seconds * 1000), audio, video);
    }

    @Override
    public Path cut(Path media, Cell cell, Modality modality, Path dir, ParseContext context)
            throws IOException, TikaException {
        boolean audio = modality == Modality.AUDIO;
        Path out = dir.resolve(cell.index() + (audio ? ".ogg" : ".mp4"));
        if (Files.exists(out)) {
            return out;
        }
        List<String> cmd = new ArrayList<>(List.of("ffmpeg", "-hide_banner", "-loglevel", "error",
                "-y", "-ss", ms(cell.startMs()), "-t", ms(cell.endMs() - cell.startMs()),
                "-i", media.toAbsolutePath().toString()));
        if (audio) {
            cmd.addAll(List.of("-vn", "-ac", "1", "-c:a", "libopus", "-b:a", "32k"));
        } else {
            cmd.addAll(List.of("-an", "-vf", "scale=-2:360", "-c:v", "libx264", "-preset",
                    "veryfast", "-crf", "28", "-movflags", "+faststart"));
        }
        cmd.add(out.toAbsolutePath().toString());
        FileProcessResult r = ProcessUtils.execute(new ProcessBuilder(cmd), context,
                CUT_TIMEOUT_MS, CUT_BUFFER, CUT_BUFFER);
        if (r.isTimeout() || r.getExitValue() != 0 || !Files.exists(out)) {
            // a partial file must not be taken for the cut on a retry
            Files.deleteIfExists(out);
        }
        if (r.isTimeout()) {
            throw new Timeout("ffmpeg ran out of the parse's time budget on segment "
                    + cell.index() + " after " + r.getProcessTimeMillis() + " ms");
        }
        if (r.getExitValue() != 0 || !Files.exists(out)) {
            throw new TikaException("ffmpeg failed on segment " + cell.index() + ": "
                    + r.getStderr());
        }
        return out;
    }

    private static String ms(long millis) {
        return String.format(Locale.ROOT, "%d.%03d", millis / 1000, millis % 1000);
    }
}
