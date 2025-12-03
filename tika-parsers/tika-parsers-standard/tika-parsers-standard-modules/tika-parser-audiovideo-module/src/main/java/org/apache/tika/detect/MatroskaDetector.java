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
package org.apache.tika.detect;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * Detector for Matroska (MKV and WEBM) files based on the EBML header.
 */
@TikaComponent
public class MatroskaDetector implements Detector {

    /** For serialization compatibility. */
    private static final long serialVersionUID = 1L;

    private static final MediaType MATROSKA =
            MediaType.application("x-matroska");

    private static final MediaType WEBM =
            MediaType.video("webm");

    private static final byte[] EBML_HEADER =
            new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};

    /**
     * Detects the media type of the input stream by inspecting EBML headers.
     *
     * @param input    the input stream
     * @param metadata the metadata to populate
     * @return detected MediaType (WEBM, Matroska, or OCTET_STREAM)
     * @throws IOException if an I/O error occurs
     */
    @Override
    public MediaType detect(InputStream input, Metadata metadata) throws IOException {
        if (input == null) {
            return MediaType.OCTET_STREAM;
        }
        input.mark(64);

        byte[] header = new byte[64];
        int bytesRead = -1;
        try {
            bytesRead = IOUtils.read(input, header, 0, 64);
        } finally {
            input.reset();
        }

        if (bytesRead < EBML_HEADER.length) {
            return MediaType.OCTET_STREAM;
        }

        for (int i = 0; i < EBML_HEADER.length; i++) {
            if (header[i] != EBML_HEADER[i]) {
                return MediaType.OCTET_STREAM;
            }
        }

        for (int i = 4; i < bytesRead - 4; i++) {
            if (header[i] == 'w'
                    && header[i + 1] == 'e'
                    && header[i + 2] == 'b'
                    && header[i + 3] == 'm') {
                return WEBM;
            }
            if (header[i] == 'm'
                    && header[i + 1] == 'a'
                    && header[i + 2] == 't'
                    && header[i + 3] == 'r') {
                return MATROSKA;
            }
        }

        return MediaType.OCTET_STREAM;
    }
}
