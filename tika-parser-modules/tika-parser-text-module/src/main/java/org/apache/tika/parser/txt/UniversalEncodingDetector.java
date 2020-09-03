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
package org.apache.tika.parser.txt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.apache.tika.config.Field;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.metadata.Metadata;

public class UniversalEncodingDetector implements EncodingDetector {

    private static final int BUFSIZE = 1024;

    private static final int DEFAULT_MARK_LIMIT = 16 * BUFSIZE;

    private int markLimit = DEFAULT_MARK_LIMIT;

    public Charset detect(InputStream input, Metadata metadata)
            throws IOException {
        if (input == null) {
            return null;
        }

        input.mark(markLimit);
        try {
            UniversalEncodingListener listener =
                    new UniversalEncodingListener(metadata);

            byte[] b = new byte[BUFSIZE];
            int n = 0;
            int m = input.read(b);
            while (m != -1 && n < markLimit && !listener.isDone()) {
                n += m;
                listener.handleData(b, 0, m);
                m = input.read(b, 0, Math.min(b.length,markLimit - n));
            }

            return listener.dataEnd();
        } catch (LinkageError e) {
            return null; // juniversalchardet is not available
        } finally {
            input.reset();
        }
    }

    /**
     * How far into the stream to read for charset detection.
     * Default is 8192.
     *
     * @param markLimit
     */
    @Field
    public void setMarkLimit(int markLimit) {
        this.markLimit = markLimit;
    }

    public int getMarkLimit() {
        return markLimit;
    }
}
