/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.io;

import java.io.IOException;
import java.io.InputStream;

public class IOUtils {

    public static long skip(final InputStream input, final long toSkip, byte[] buffer)
            throws IOException {
        if (toSkip < 0) {
            throw new IllegalArgumentException(
                    "Skip count must be non-negative, actual: " + toSkip);
        }
        /*
         * N.B. no need to synchronize this because: - we don't care if the
         * buffer is created multiple times (the data is ignored) - we always use the same size
         * buffer, so if it it is recreated it
         * will still be OK (if the buffer size were variable, we would need to synch. to ensure
         * some other thread did not create a smaller one)
         */
        long remain = toSkip;
        while (remain > 0) {
            // See https://issues.apache.org/jira/browse/IO-203 for why we use read() rather than delegating to skip()
            final long n = input.read(buffer, 0, (int) Math.min(remain, buffer.length));
            if (n < 0) { // EOF
                break;
            }
            remain -= n;
        }
        return toSkip - remain;
    }
}
