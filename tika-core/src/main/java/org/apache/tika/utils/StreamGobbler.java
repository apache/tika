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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class StreamGobbler implements Runnable {

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

        try (BufferedReader r =
                new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line = r.readLine();
            while (line != null) {
                if (maxBufferLength >= 0) {
                    if (streamLength + line.length() > maxBufferLength) {
                        int len = maxBufferLength - (int) streamLength;
                        if (len > 0) {
                            isTruncated = true;
                            String truncatedLine = line.substring(0, Math.min(line.length(), len));
                            lines.add(truncatedLine);
                        }
                    } else {
                        lines.add(line);
                    }
                }
                streamLength += line.length();
                line = r.readLine();
            }
        } catch (IOException e) {
            return;
        }
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
