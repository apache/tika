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
package org.apache.tika.parser.microsoft.onenote.fsshttpb.util;

import java.util.UUID;

public class GuidUtil {
    private static final UUID EMPTY = new UUID(0, 0);

    public static UUID emptyGuid() {
        return EMPTY;
    }

    /** Reads a GUID stored in Microsoft byte order: the first three fields little-endian. */
    public static UUID fromMicrosoftBytes(byte[] b, int offset) {
        long high = ((long) le(b, offset, 4) << 32) | ((long) le(b, offset + 4, 2) << 16) |
                le(b, offset + 6, 2);
        long low = 0;
        for (int i = 8; i < 16; i++) {
            low = (low << 8) | (b[offset + i] & 0xff);
        }
        return new UUID(high, low);
    }

    private static long le(byte[] b, int offset, int n) {
        long v = 0;
        for (int i = n - 1; i >= 0; i--) {
            v = (v << 8) | (b[offset + i] & 0xff);
        }
        return v;
    }
}
