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
package org.apache.tika.parser.ntfs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

public class NTFSDetector implements Detector {

    private static final long serialVersionUID = 1L;

    // NTFS signature is "NTFS" at offset 0x03
    private static final int SIGNATURE_OFFSET = 3;
    private static final byte[] NTFS_SIGNATURE = "NTFS".getBytes(StandardCharsets.US_ASCII);
    // The buffer size should be enough to read the signature
    private static final int BUFFER_SIZE = SIGNATURE_OFFSET + NTFS_SIGNATURE.length;


    @Override
    public MediaType detect(InputStream input, Metadata metadata) throws IOException {
        if (input == null) {
            return MediaType.OCTET_STREAM;
        }

        input.mark(BUFFER_SIZE);
        try {
            return detectFromStream(input);
        } finally {
            input.reset();
        }
    }

    private MediaType detectFromStream(InputStream stream) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead = stream.read(buffer, 0, BUFFER_SIZE);

        if (bytesRead < BUFFER_SIZE) {
            // Not enough bytes to check the signature
            return MediaType.OCTET_STREAM;
        }

        byte[] signatureInStream = Arrays.copyOfRange(buffer, SIGNATURE_OFFSET, BUFFER_SIZE);

        if (Arrays.equals(NTFS_SIGNATURE, signatureInStream)) {
            return MediaType.application("x-ntfs-image");
        }

        return MediaType.OCTET_STREAM;
    }
}
