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

import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.metadata.Metadata;

public class UniversalEncodingDetector implements EncodingDetector {

    private static final int BUFSIZE = 1024;

    private static final int LOOKAHEAD = 16 * BUFSIZE;

    public Charset detect(InputStream input, Metadata metadata)
            throws IOException {
        if (input == null) {
            return null;
        }

        input.mark(LOOKAHEAD);
        try {
            UniversalEncodingListener listener =
                    new UniversalEncodingListener(metadata);

            byte[] b = new byte[BUFSIZE];
            int n = 0;
            int m = input.read(b);
            while (m != -1 && n < LOOKAHEAD && !listener.isDone()) {
                n += m;
                listener.handleData(b, 0, m);
                m = input.read(b, 0, Math.min(b.length, LOOKAHEAD - n));
            }

            return listener.dataEnd();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) { // if juniversalchardet is not available
            return null;
        } finally {
            input.reset();
        }
    }

}
