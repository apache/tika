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
package org.apache.tika.detect.ntfs;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.util.Arrays;

import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * Detector for identifying NTFS disk image files
 * based on boot sector signature.
 */
public class NTFSDetector implements Detector {

    @Serial
    private static final long serialVersionUID = 1L;

    /** NTFS Media Type. */
    private static final MediaType NTFS_MEDIA_TYPE =
            MediaType.application("x-ntfs");

    /** Number of bytes to read from the start of the file
     *  (NTFS boot sector size). */
    private static final int BYTE_COUNT = 512;

    /** Offset where the NTFS signature is expected in the boot sector. */
    private static final int SIGNATURE_OFFSET = 3;

    /** The NTFS signature bytes to detect. */
    private static final byte[] NTFS_SIGNATURE = new byte[]
            {'N', 'T', 'F', 'S', ' ', ' ', ' ', ' '};

    /**
     * Detects if the given input stream is an NTFS disk image.
     *
     * @param input    the input stream
     * @param metadata the metadata for the stream
     * @return the media type if detected, or application/octet-stream
     * @throws IOException on I/O error
     */
    @Override
    public MediaType detect(final InputStream input, final Metadata metadata)
            throws IOException {
        input.mark(BYTE_COUNT);
        // NTFS boot sector is within the first 512 bytes

        var buffer = new byte[BYTE_COUNT];
        var bytesRead = input.read(buffer);

        input.reset();

        if (bytesRead >= BYTE_COUNT && isNTFSBootSector(buffer)) {
            return NTFS_MEDIA_TYPE;
        }

        return MediaType.OCTET_STREAM; // fallback
    }

    /**
     * Checks whether the provided buffer contains the NTFS signature.
     *
     * @param buffer the byte buffer read from input
     * @return true if buffer matches NTFS signature, false otherwise
     */
    private boolean isNTFSBootSector(final byte[] buffer) {
        var endOfSignature = SIGNATURE_OFFSET + NTFS_SIGNATURE.length;
        return Arrays.equals(Arrays.copyOfRange(
                buffer, SIGNATURE_OFFSET, endOfSignature), NTFS_SIGNATURE);
    }
}
