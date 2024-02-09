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
package org.apache.tika.detect.gzip;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;

import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * This is designed to detect commonly gzipped file types such as warc.gz.
 * This is a first step.  We still need to implement tar.gz and svg.gz and ???
 */
public class GZipSpecializationDetector implements Detector {
    public static MediaType GZ = MediaType.application("gzip");
    public static MediaType WARC_GZ = MediaType.application("warc+gz");

    public static MediaType ARC_GZ = MediaType.application("arc+gz");

    @Override
    public MediaType detect(InputStream input, Metadata metadata) throws IOException {
        if (input == null) {
            return MediaType.OCTET_STREAM;
        }
        input.mark(2);
        byte[] firstTwo = new byte[2];
        try {
            IOUtils.readFully(input, firstTwo);
        } finally {
            input.reset();
        }
        int magic = ((firstTwo[1] & 0xff) << 8) | (firstTwo[0] & 0xff);
        if (GZIPInputStream.GZIP_MAGIC != magic) {
            return MediaType.OCTET_STREAM;
        }
        return detectSpecialization(input, metadata);
    }

    private MediaType detectSpecialization(InputStream input, Metadata metadata) throws IOException {

        int buffSize = 1024;
        UnsynchronizedByteArrayOutputStream gzippedBytes = new UnsynchronizedByteArrayOutputStream();
        try {
            IOUtils.copyRange(input, buffSize, gzippedBytes);
        } catch (IOException e) {
            //swallow
        } finally {
            input.reset();
        }
        UnsynchronizedByteArrayOutputStream bytes = new UnsynchronizedByteArrayOutputStream();
        try (InputStream is = new
                     GzipCompressorInputStream(new UnsynchronizedByteArrayInputStream(gzippedBytes.toByteArray()))) {
            int c = is.read();
            //read bytes one at a time to avoid premature EOF from buffering
            while (c > -1) {
                bytes.write(c);
                c = is.read();
            }
        } catch (IOException e) {
            //swallow
        }
        //TODO: something better than this
        String s = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        if (s.startsWith("WARC/")) {
            return WARC_GZ;
        } else if (s.startsWith("filedesc://")) {
            return ARC_GZ;
        }
        return GZ;
    }
}
