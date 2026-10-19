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
package org.apache.tika.parser.enricher;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;

/**
 * What the checks of one image share: its bytes, read once and left in the stream for the
 * engine, the dimensions its parser recorded, and anything a check chooses to {@linkplain
 * #memo memoize}, so two checks that need the decoded pixels decode them once.
 *
 * @since Apache Tika 4.2
 */
public final class ImageProbe {

    /** Images longer than this are not read for a check; the checks then admit them. */
    public static final int MAX_BYTES = 64 * 1024 * 1024;

    /** How much of the start of an image {@link #header()} reads: enough for a header. */
    public static final int HEADER_BYTES = 1024 * 1024;

    private final TikaInputStream tis;
    private final Metadata metadata;
    private final Map<String, Object> memo = new HashMap<>();
    private byte[] bytes;
    private boolean bytesRead;
    private byte[] header;
    private boolean headerComplete;

    public ImageProbe(TikaInputStream tis, Metadata metadata) {
        this.tis = tis;
        this.metadata = metadata;
    }

    /** The width the image's parser recorded ({@code tiff:ImageWidth}), or null. */
    public Integer recordedWidth() {
        return metadata.getInt(TIFF.IMAGE_WIDTH);
    }

    /** The height the image's parser recorded ({@code tiff:ImageLength}), or null. */
    public Integer recordedHeight() {
        return metadata.getInt(TIFF.IMAGE_LENGTH);
    }

    /**
     * The image's length in bytes without reading it, or -1 when the stream does not know it
     * or knows only a declared length (a container's or a header's), which may lie.
     */
    public long length() throws IOException {
        return tis.hasReliableLength() ? tis.getLength() : -1;
    }

    /**
     * The first {@link #HEADER_BYTES} of the image (all of it when it is shorter), read on first
     * use and left in the stream: enough for a check that needs only dimensions, so a large
     * image is not read whole for it. Treat the array as read-only.
     */
    public byte[] header() throws IOException {
        if (header == null) {
            tis.mark(HEADER_BYTES + 1);
            try {
                header = tis.readNBytes(HEADER_BYTES);
            } finally {
                tis.reset();
            }
            headerComplete = header.length < HEADER_BYTES;
        }
        return header;
    }

    /** Whether {@link #header()} is the whole image. */
    public boolean headerIsComplete() throws IOException {
        header();
        return headerComplete;
    }

    /**
     * The image's bytes, read on first use and left in the stream, or null when it is longer
     * than {@link #MAX_BYTES} or cannot be read. Treat the array as read-only.
     */
    public byte[] bytes() throws IOException {
        if (!bytesRead) {
            bytesRead = true;
            bytes = readBytes();
        }
        return bytes;
    }

    private byte[] readBytes() throws IOException {
        if (length() > MAX_BYTES) {
            return null;
        }
        if (headerIsComplete()) {
            return header;
        }
        tis.mark(MAX_BYTES + 1);
        try {
            byte[] read = tis.readNBytes(MAX_BYTES + 1);
            return read.length > MAX_BYTES ? null : read;
        } finally {
            tis.reset();
        }
    }

    /** A value one check computes and the next reuses; computed once per key. */
    @SuppressWarnings("unchecked")
    public <T> T memo(String key, Computation<T> computation) throws IOException {
        if (!memo.containsKey(key)) {
            memo.put(key, computation.compute());
        }
        return (T) memo.get(key);
    }

    /** A computation that may fail to read. */
    @FunctionalInterface
    public interface Computation<T> {
        T compute() throws IOException;
    }
}
