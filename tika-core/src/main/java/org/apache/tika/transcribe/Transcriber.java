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

package org.apache.tika.transcribe;

import org.apache.tika.exception.TikaException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Interface for Transcriber services.
 *
 * @see <a href="https://issues.apache.org/jira/browse/TIKA-94">TIKA-94</a>
 * @since Tika 2.1
 */
public interface Transcriber {
    /**
     * Transcribe the given file.
     *
     * @param inputStream the source input stream.
     * @return The transcribed string result, NULL if the job failed.
     * @throws TikaException When there is an error transcribing.
     * @throws IOException   If an I/O exception of some sort has occurred.
     * @since 2.1
     */
    public String transcribe(InputStream inputStream) throws TikaException, IOException;

    /**
     * Transcribe the given the file and the source language.
     *
     * @param inputStream    the source input stream.
     * @param sourceLanguage The language code for the language used in the input media file.
     * @return The transcribed string result, NULL if the job failed.
     * @throws TikaException When there is an error transcribing.
     * @throws IOException   If an I/O exception of some sort has occurred.
     * @since 2.1
     */
    public String transcribe(InputStream inputStream, String sourceLanguage) throws TikaException, IOException;

    /**
     * @return true if this Transcriber is probably able to transcribe right now.
     * @since Tika 2.1
     */
    public boolean isAvailable();
}
