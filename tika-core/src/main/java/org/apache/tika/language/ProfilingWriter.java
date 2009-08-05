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
package org.apache.tika.language;

import java.io.IOException;
import java.io.Writer;

public class ProfilingWriter extends Writer {

    private final NGramProfile profile = new NGramProfile(
            "suspect",
            NGramProfile.DEFAULT_MIN_NGRAM_LENGTH,
            NGramProfile.DEFAULT_MAX_NGRAM_LENGTH);

    private final StringBuffer buffer = new StringBuffer("_");

    private void addWord() {
        if (buffer.length() > 1) {
            buffer.append("_");
            profile.add(buffer);
            buffer.setLength(1);
        }
    }

    public NGramProfile getProfile() {
        return profile;
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        for (int i = 0; i < len; i++) {
            char c = Character.toLowerCase(cbuf[off + i]);
            if (Character.isLetter(c)) {
                buffer.append(c);
            } else {
                addWord();
            }
        }
    }

    @Override
    public void close() throws IOException {
        addWord();
    }

    /**
     * Ignored.
     */
    @Override
    public void flush() {
    }

}
