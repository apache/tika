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

    public static LanguageProfile profile(String content) {
        ProfilingWriter writer = new ProfilingWriter();
        char[] ch = content.toCharArray();
        writer.write(ch, 0, ch.length);
        return writer.getProfile();
    }


    private final LanguageProfile profile = new LanguageProfile();

    private char[] buffer = new char[] { 0, 0, '_' };

    private int n = 1;

    public LanguageProfile getProfile() {
        return profile;
    }

    @Override
    public void write(char[] cbuf, int off, int len) {
        for (int i = 0; i < len; i++) {
            char c = Character.toLowerCase(cbuf[off + i]);
            if (Character.isLetter(c)) {
                addLetter(c);
            } else {
                addSeparator();
            }
        }
    }

    private void addLetter(char c) {
        System.arraycopy(buffer, 1, buffer, 0, buffer.length - 1);
        buffer[buffer.length - 1] = c;
        n++;
        if (n >= buffer.length) {
            profile.add(new String(buffer));
        }
    }

    private void addSeparator() {
        addLetter('_');
        n = 1;
    }

    @Override
    public void close() throws IOException {
        addSeparator();
    }

    /**
     * Ignored.
     */
    @Override
    public void flush() {
    }

}
