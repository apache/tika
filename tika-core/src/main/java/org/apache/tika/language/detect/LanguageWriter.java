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
package org.apache.tika.language.detect;

import java.io.IOException;
import java.io.Writer;

/**
 * Writer that builds a language profile based on all the written content.
 *
 * @since Apache Tika 0.10
 */
public class LanguageWriter extends Writer {

    private final LanguageDetector detector;

    public LanguageWriter(LanguageDetector detector) {
        this.detector = detector;
        detector.reset();
    }

    /**
     * Returns the language detector used by this writer. Note that
     * the returned language detector gets updated whenever new characters
     * are written.
     *
     * @return language detector
     */
    public LanguageDetector getDetector() {
        return detector;
    }

    /**
     * Returns the detected language based on text written thus far.
     *
     * @return LanguageResult
     */
    public LanguageResult getLanguage() {
        return detector.detect();
    }

    @Override
    public void write(char[] cbuf, int off, int len) {
        detector.addText(cbuf, off, len);
    }

    /**
     * Ignored.
     */
    @Override
    public void close() throws IOException {
    }

    /**
     * Ignored.
     */
    @Override
    public void flush() {
    }

    public void reset() {
        detector.reset();
    }
}
