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
package org.apache.tika.parser.blender;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * Blender saves a file as it is, gzip-compressed (up to 2.9x) or zstd-compressed (from 3.0),
 * whichever the "Compress" option asks for. zstd needs zstd-jni on the classpath, as it does
 * for the {@code CompressorParser}.
 */
public final class BlendCompression {

    private static final boolean ZSTD_AVAILABLE = zstdAvailable();

    private BlendCompression() {
    }

    /**
     * The uncompressed content of a blend file, or {@code null} for zstd without zstd-jni.
     * The stream is wrapped, not closed, when it is compressed.
     */
    public static InputStream open(InputStream stream) throws IOException {
        InputStream in = stream.markSupported() ? stream : new BufferedInputStream(stream);
        in.mark(4);
        byte[] head = in.readNBytes(4);
        in.reset();
        if (head.length >= 2 && (head[0] & 0xff) == 0x1f && (head[1] & 0xff) == 0x8b) {
            return new GZIPInputStream(in);
        }
        if (head.length == 4 && (head[0] & 0xff) == 0x28 && (head[1] & 0xff) == 0xb5
                && (head[2] & 0xff) == 0x2f && (head[3] & 0xff) == 0xfd) {
            return ZSTD_AVAILABLE ? Zstd.open(in) : null;
        }
        return in;
    }

    /**
     * Holds the only reference to zstd-jni: verifying a method that returns its stream loads
     * the class, so the method lives in a class that is loaded only when zstd-jni is there.
     */
    private static final class Zstd {
        static InputStream open(InputStream in) throws IOException {
            return new com.github.luben.zstd.ZstdInputStream(in);
        }
    }

    private static boolean zstdAvailable() {
        try {
            Class.forName("com.github.luben.zstd.ZstdInputStream");
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
