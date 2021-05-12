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
package org.apache.tika.detect.zip;

import org.apache.commons.compress.compressors.CompressorStreamFactory;

import org.apache.tika.mime.MediaType;

public class CompressorConstants {

    public static final MediaType BROTLI = MediaType.application("x-brotli");
    public static final MediaType LZ4_BLOCK = MediaType.application("x-lz4-block");
    public static final MediaType SNAPPY_RAW = MediaType.application("x-snappy-raw");


    public static final MediaType BZIP = MediaType.application("x-bzip");
    public static final MediaType BZIP2 = MediaType.application("x-bzip2");
    public static final MediaType GZIP = MediaType.application("gzip");
    public static final MediaType GZIP_ALT = MediaType.application("x-gzip");
    public static final MediaType COMPRESS = MediaType.application("x-compress");
    public static final MediaType XZ = MediaType.application("x-xz");
    public static final MediaType PACK = MediaType.application("x-java-pack200");
    public static final MediaType SNAPPY_FRAMED = MediaType.application("x-snappy");
    public static final MediaType ZLIB = MediaType.application("zlib");
    public static final MediaType LZMA = MediaType.application("x-lzma");
    public static final MediaType LZ4_FRAMED = MediaType.application("x-lz4");
    public static final MediaType ZSTD = MediaType.application("zstd");
    public static final MediaType DEFLATE64 = MediaType.application("deflate64");

    public static MediaType getMediaType(String name) {
        if (CompressorStreamFactory.BROTLI.equals(name)) {
            return BROTLI;
        } else if (CompressorStreamFactory.LZ4_BLOCK.equals(name)) {
            return LZ4_BLOCK;
        } else if (CompressorStreamFactory.LZ4_FRAMED.equals(name)) {
            return LZ4_FRAMED;
        } else if (CompressorStreamFactory.BZIP2.equals(name)) {
            return BZIP2;
        } else if (CompressorStreamFactory.GZIP.equals(name)) {
            return GZIP;
        } else if (CompressorStreamFactory.XZ.equals(name)) {
            return XZ;
        } else if (CompressorStreamFactory.DEFLATE.equals(name)) {
            return ZLIB;
        } else if (CompressorStreamFactory.Z.equals(name)) {
            return COMPRESS;
        } else if (CompressorStreamFactory.PACK200.equals(name)) {
            return PACK;
        } else if (CompressorStreamFactory.SNAPPY_FRAMED.equals(name)) {
            return SNAPPY_FRAMED;
        } else if (CompressorStreamFactory.SNAPPY_RAW.equals(name)) {
            return SNAPPY_RAW;
        } else if (CompressorStreamFactory.LZMA.equals(name)) {
            return LZMA;
        } else if (CompressorStreamFactory.ZSTANDARD.equals(name)) {
            return ZSTD;
        } else if (CompressorStreamFactory.DEFLATE64.equals(name)) {
            return DEFLATE64;
        } else {
            return MediaType.OCTET_STREAM;
        }

    }
}
