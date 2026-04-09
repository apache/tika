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
package org.apache.tika.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class StreamGobbler implements Runnable {

    // Maximum chars to buffer for a single line before truncating.
    // Prevents OOM from a process outputting gigabytes without a newline.
    private static final int MAX_LINE_LENGTH = 1_000_000;

    private final InputStream is;
    private final int maxBufferLength;
    List<String> lines = new ArrayList<>();
    long streamLength = 0;
    boolean isTruncated = false;

    public StreamGobbler(InputStream is, int maxBufferLength) {
        this.is = is;
        this.maxBufferLength = maxBufferLength;
    }


    @Override
    public void run() {
        try (Reader r = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line = readLineBounded(r);
            while (line != null) {
                if (maxBufferLength >= 0) {
                    if (streamLength + line.length() > maxBufferLength) {
                        int len = maxBufferLength - (int) streamLength;
                        if (len > 0) {
                            isTruncated = true;
                            String truncatedLine =
                                    line.substring(0, Math.min(line.length(), len));
                            lines.add(truncatedLine);
                        }
                    } else {
                        lines.add(line);
                    }
                }
                streamLength += line.length();
                line = readLineBounded(r);
            }
        } catch (IOException e) {
            return;
        }
    }

    /**
     * Reads a line from the reader, capping at {@link #MAX_LINE_LENGTH} chars.
     * Any remaining chars on the line are discarded. Returns null at EOF.
     */
    private String readLineBounded(Reader r) throws IOException {
        StringBuilder sb = new StringBuilder();
        boolean discarding = false;
        int ch;
        while ((ch = r.read()) != -1) {
            if (ch == '\n') {
                break;
            }
            if (ch == '\r') {
                // peek for \r\n
                r.mark(1);
                int next = r.read();
                if (next != '\n' && next != -1) {
                    r.reset();
                }
                break;
            }
            if (!discarding) {
                if (sb.length() < MAX_LINE_LENGTH) {
                    sb.append((char) ch);
                } else {
                    discarding = true;
                    isTruncated = true;
                }
            }
            // When discarding, we still consume chars until newline/EOF
            // to keep the stream position correct.
        }
        if (ch == -1 && sb.length() == 0) {
            return null;
        }
        return sb.toString();
    }

    public List<String> getLines() {
        return lines;
    }

    public long getStreamLength() {
        return streamLength;
    }

    public boolean getIsTruncated() {
        return isTruncated;
    }
}
