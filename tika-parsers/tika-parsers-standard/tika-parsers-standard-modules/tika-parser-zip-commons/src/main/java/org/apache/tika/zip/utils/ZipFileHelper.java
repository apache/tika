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
package org.apache.tika.zip.utils;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;

import org.apache.commons.compress.archivers.zip.ZipFile;

import org.apache.tika.io.TikaInputStream;

public class ZipFileHelper {

    /**
     * Opens a {@link ZipFile} over {@link TikaInputStream#getSeekableByteChannel()}, so
     * in-memory content is read without spilling to disk. On success the returned
     * {@code ZipFile.close()} closes the channel it was built on; on failure the channel
     * is closed before the exception propagates.
     */
    public static ZipFile open(TikaInputStream tis, Charset charset) throws IOException {
        SeekableByteChannel channel = tis.getSeekableByteChannel();
        try {
            ZipFile.Builder builder = ZipFile.builder().setSeekableByteChannel(channel);
            if (charset != null) {
                builder.setCharset(charset);
            }
            return builder.get();
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    private ZipFileHelper() {
    }
}
