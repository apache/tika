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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.inference.MediaConfig;
import org.apache.tika.parser.inference.Modality;

/**
 * Cuts audio and video into the cells of the document's {@link MediaConfig} grid. Every
 * binding on a document sees the same cells, computed from the probed duration, so their
 * vectors join on the cell.
 */
interface MediaSegmenter {

    /** One cell of the grid, times in ms on the container's presentation timeline. */
    record Cell(int index, long startMs, long endMs) {
    }

    /** What the probe found: length and which channels exist. */
    record Probe(long durationMs, boolean hasAudio, boolean hasVideo) {
    }

    /** The parse's budget ran out under the tool; the caller stops cutting, it does not retry. */
    final class Timeout extends TikaException {
        Timeout(String message) {
            super(message);
        }
    }

    /** Whether the tool answers; a MEDIA binding stands by without it. */
    boolean available();

    Probe probe(Path media, ParseContext context) throws IOException, TikaException;

    /**
     * Writes one cell's audio or picture into {@code dir} and returns the file. Idempotent
     * per cell: an existing cut is returned, not redone, so a retried request re-reads it.
     */
    Path cut(Path media, Cell cell, Modality modality, Path dir, ParseContext context)
            throws IOException, TikaException;

    /**
     * The grid over a duration: fixed windows, each starting {@code seconds - overlap} after
     * the last, at most {@code cap} of them. A tail inside the previous cell is not a cell.
     */
    static List<Cell> cells(long durationMs, MediaConfig config, int cap) throws TikaException {
        long window = config.getSegment().getSeconds() * 1000L;
        long stride = window - config.getSegment().getOverlap() * 1000L;
        if (window <= 0 || stride <= 0 || stride > window) {
            throw new TikaException("media.segment needs seconds > overlap >= 0");
        }
        List<Cell> cells = new ArrayList<>();
        long start = 0;
        long lastEnd = -1;
        while (start < durationMs && lastEnd < durationMs && cells.size() < cap) {
            long end = Math.min(start + window, durationMs);
            cells.add(new Cell(cells.size(), start, end));
            lastEnd = end;
            start += stride;
        }
        return cells;
    }

    /** The cell's id on its document: the chunks every binding writes for it carry this. */
    static String correlator(String idPath, Cell cell) {
        return (idPath == null ? "" : idPath + ":") + "t:" + cell.startMs() + "-" + cell.endMs();
    }

    /** The media type of what {@link #cut} writes for a modality. */
    static String mimeType(Modality modality) {
        return modality == Modality.AUDIO ? "audio/ogg" : "video/mp4";
    }
}
