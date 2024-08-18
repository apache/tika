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

package org.apache.tika.parser.microsoft.onenote;


import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitConverter;
import org.apache.tika.utils.StringUtils;

public class GUID implements Comparable<GUID> {
    int[] guid;

    public GUID(int[] guid) {
        this.guid = guid;
    }

    /**
     * Converts a GUID of format: {AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE} (in bytes) to a GUID
     * object.
     *
     * @param guid The bytes that contains string in UTF-16 format of {AAAAAAAA-BBBB-CCCC-DDDD
     *             -EEEEEEEEEEEE}
     * @return GUID object parsed from guid bytes.
     */
    public static GUID fromCurlyBraceUTF16Bytes(byte[] guid) {
        int[] intGuid = new int[16];
        String utf16Str = new String(guid, StandardCharsets.UTF_16LE).replaceAll("\\{", "")
                .replaceAll("-", "").replaceAll("}", "");
        for (int i = 0; i < utf16Str.length(); i += 2) {
            intGuid[i / 2] =
                    Integer.parseUnsignedInt("" + utf16Str.charAt(i) + utf16Str.charAt(i + 1), 16);
        }
        return new GUID(intGuid);
    }

    public static int memcmp(int[] b1, int[] b2, int sz) {
        for (int i = 0; i < sz; i++) {
            if (b1[i] != b2[i]) {
                if ((b1[i] >= 0 && b2[i] >= 0) || (b1[i] < 0 && b2[i] < 0)) {
                    return b1[i] - b2[i];
                }
                if (b1[i] < 0 && b2[i] >= 0) {
                    return 1;
                }
                if (b2[i] < 0 && b1[i] >= 0) {
                    return -1;
                }
            }
        }
        return 0;
    }

    public static GUID nil() {
        return new GUID(new int[16]);
    }

    @Override
    public int compareTo(GUID o) {
        return memcmp(guid, o.guid, 16);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GUID guid1 = (GUID) o;
        return Arrays.equals(guid, guid1.guid);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(guid);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i = 0; i < 4; ++i) {
            sb.append(StringUtils.leftPad(Integer.toHexString(guid[i]), 2, '0'));
        }
        sb.append("-");
        for (int i = 4; i < 6; ++i) {
            sb.append(StringUtils.leftPad(Integer.toHexString(guid[i]), 2, '0'));
        }
        sb.append("-");
        for (int i = 6; i < 8; ++i) {
            sb.append(StringUtils.leftPad(Integer.toHexString(guid[i]), 2, '0'));
        }
        sb.append("-");
        for (int i = 8; i < 10; ++i) {
            sb.append(StringUtils.leftPad(Integer.toHexString(guid[i]), 2, '0'));
        }
        sb.append("-");
        for (int i = 10; i < 16; ++i) {
            sb.append(StringUtils.leftPad(Integer.toHexString(guid[i]), 2, '0'));
        }
        sb.append("}");
        return sb.toString().toUpperCase(Locale.US);
    }

    public int[] getGuid() {
        return guid;
    }

    public GUID setGuid(int[] guid) {
        this.guid = guid;
        return this;
    }

    public String getGuidString() {
        return Arrays.toString(guid);
    }

    public List<Byte> toByteArray() {
        List<Byte> byteList = new ArrayList<>();
        for (int nextInt : guid) {
            for (byte b : BitConverter.getBytes(nextInt)) {
                byteList.add(b);
            }
        }
        return byteList;
    }
}
