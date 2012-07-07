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
import java.nio.charset.Charset;

import org.apache.tika.metadata.Metadata;

/**
 * Character encoding detector. Implementations of this interface use
 * various heuristics to detect the character encoding of a text document
 * based on given input metadata or the first few bytes of the document stream.
 *
 * @since Apache Tika 0.4
 */
public interface EncodingDetector {

    /**
     * Detects the character encoding of the given text document, or
     * <code>null</code> if the encoding of the document can not be detected.
     * <p>
     * If the document input stream is not available, then the first
     * argument may be <code>null</code>. Otherwise the detector may
     * read bytes from the start of the stream to help in encoding detection.
     * The given stream is guaranteed to support the
     * {@link InputStream#markSupported() mark feature} and the detector
     * is expected to {@link InputStream#mark(int) mark} the stream before
     * reading any bytes from it, and to {@link InputStream#reset() reset}
     * the stream before returning. The stream must not be closed by the
     * detector.
     * <p>
     * The given input metadata is only read, not modified, by the detector.
     *
     * @param input text document input stream, or <code>null</code>
     * @param metadata input metadata for the document
     * @return detected character encoding, or <code>null</code>
     * @throws IOException if the document input stream could not be read
     */
    Charset detect(InputStream input, Metadata metadata) throws IOException;

}
