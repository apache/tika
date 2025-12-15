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
package org.apache.tika.digest;

import java.io.IOException;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * Interface for digester implementations.
 * See {@link InputStreamDigester} for an implementation
 * in tika-core, or CommonsDigester in tika-parser-digest-commons.
 */
public interface Digester {
    /**
     * Digests a TikaInputStream and sets the appropriate value(s) in the metadata.
     * The Digester is also responsible for marking and resetting the stream.
     * <p>
     * The given stream is guaranteed to support the
     * {@link TikaInputStream#markSupported() mark feature} and the detector
     * is expected to {@link TikaInputStream#mark(int) mark} the stream before
     * reading any bytes from it, and to {@link TikaInputStream#reset() reset}
     * the stream before returning. The stream must not be closed by the
     * detector.
     *
     * @param tis          TikaInputStream to digest
     * @param m            Metadata to set the values for
     * @param parseContext ParseContext
     * @throws IOException on I/O error
     */
    void digest(TikaInputStream tis, Metadata m, ParseContext parseContext) throws IOException;
}
