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
package org.apache.tika.detect.blender;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.blender.BlendCompression;

/**
 * Detects a compressed Blender file, which its gzip or zstd magic would otherwise make an
 * archive, by the "BLENDER" its content starts with. Uncompressed files are left to their
 * magic.
 */
@TikaComponent
public class BlenderDetector implements Detector {

    private static final long serialVersionUID = 1L;

    static final MediaType BLENDER = MediaType.application("x-blender");

    private static final byte[] MAGIC = "BLENDER".getBytes(StandardCharsets.US_ASCII);

    /**
     * How much of the stream is read to uncompress the magic: a zstd block holds up to
     * 128 KB, and Blender's first frame holds the file header with it.
     */
    static final int MAX_PREFIX_LENGTH = 136 * 1024;

    @Override
    public MediaType detect(TikaInputStream tis, Metadata metadata, ParseContext parseContext)
            throws IOException {
        if (tis == null) {
            return MediaType.OCTET_STREAM;
        }
        tis.mark(MAX_PREFIX_LENGTH);
        byte[] prefix;
        try {
            prefix = tis.readNBytes(MAX_PREFIX_LENGTH);
        } finally {
            tis.reset();
        }
        if (prefix.length < 4 || startsWithMagic(prefix)) {
            return MediaType.OCTET_STREAM;
        }
        try (InputStream in = BlendCompression.open(new ByteArrayInputStream(prefix))) {
            if (in != null && startsWithMagic(in.readNBytes(MAGIC.length))) {
                return BLENDER;
            }
        } catch (IOException e) {
            // not gzip or zstd after all, or the first frame is longer than the prefix
        }
        return MediaType.OCTET_STREAM;
    }

    private static boolean startsWithMagic(byte[] bytes) {
        return bytes.length >= MAGIC.length
                && Arrays.equals(bytes, 0, MAGIC.length, MAGIC, 0, MAGIC.length);
    }
}
