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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.io.BoundedInputStream;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Utility methods for content detection.
 */
public class DetectUtils {

    /**
     * Creates a TikaInputStream suitable for detection-only purposes by reading
     * up to {@code maxLength} bytes from the input stream into a byte array.
     * <p>
     * If the input stream contains more bytes than {@code maxLength}, the resulting
     * metadata will have {@link TikaCoreProperties#TRUNCATED_CONTENT_FOR_DETECTION}
     * set to {@code true}, signaling to detectors that they are working with
     * truncated content and should adjust their behavior accordingly.
     * <p>
     * This is useful when you want to perform detection on a limited portion of
     * a large file without spooling the entire file to disk.
     *
     * @param stream    the input stream to read from (will NOT be closed)
     * @param maxLength the maximum number of bytes to read
     * @param metadata  the metadata object where truncation flag will be set if applicable
     * @return a TikaInputStream backed by the buffered bytes
     * @throws IOException if an I/O error occurs
     */
    public static TikaInputStream getStreamForDetectionOnly(InputStream stream, int maxLength,
                                                             Metadata metadata) throws IOException {
        BoundedInputStream bounded = new BoundedInputStream(maxLength + 1, stream);

        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.min(maxLength, 8192));
        byte[] buffer = new byte[4096];
        int bytesRead;
        int totalRead = 0;

        while (totalRead < maxLength && (bytesRead = bounded.read(buffer, 0,
                Math.min(buffer.length, maxLength - totalRead))) != -1) {
            baos.write(buffer, 0, bytesRead);
            totalRead += bytesRead;
        }

        // Check if there's more data available (meaning we truncated)
        boolean truncated = bounded.read() != -1;

        byte[] bytes = baos.toByteArray();
        metadata.set(TikaCoreProperties.DETECTION_CONTENT_LENGTH, bytes.length);

        if (truncated) {
            metadata.set(TikaCoreProperties.TRUNCATED_CONTENT_FOR_DETECTION, true);
        }

        return TikaInputStream.get(bytes);
    }

    /**
     * Creates a TikaInputStream suitable for detection-only purposes by reading
     * up to {@code maxLength} bytes from the input stream into a byte array.
     * <p>
     * This overload creates a new Metadata object internally. If you need to check
     * whether the content was truncated, use
     * {@link #getStreamForDetectionOnly(InputStream, int, Metadata)} instead.
     *
     * @param stream    the input stream to read from (will NOT be closed)
     * @param maxLength the maximum number of bytes to read
     * @return a TikaInputStream backed by the buffered bytes
     * @throws IOException if an I/O error occurs
     */
    public static TikaInputStream getStreamForDetectionOnly(InputStream stream, int maxLength)
            throws IOException {
        return getStreamForDetectionOnly(stream, maxLength, new Metadata());
    }

    /**
     * Checks if the given metadata indicates that the content was truncated for detection.
     *
     * @param metadata the metadata to check
     * @return true if the content was truncated, false otherwise
     */
    public static boolean isContentTruncatedForDetection(Metadata metadata) {
        String value = metadata.get(TikaCoreProperties.TRUNCATED_CONTENT_FOR_DETECTION);
        return Boolean.parseBoolean(value);
    }

    /**
     * Gets the number of bytes buffered for detection.
     *
     * @param metadata the metadata to check
     * @return the number of bytes buffered, or -1 if not set
     */
    public static int getDetectionContentLength(Metadata metadata) {
        Integer value = metadata.getInt(TikaCoreProperties.DETECTION_CONTENT_LENGTH);
        return value != null ? value : -1;
    }
}
