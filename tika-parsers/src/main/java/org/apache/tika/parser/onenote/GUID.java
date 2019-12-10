package org.apache.tika.parser.onenote;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class GUID implements Comparable<GUID> {
    int[] guid;

    /**
     * Converts a GUID of format: {AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE} (in bytes) to a GUID object.
     *
     * @param guid The bytes that contain string in UTF-16 format of {AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE}
     * @return GUID object parsed from guid bytes.
     */
    public static GUID fromCurlyBraceUTF16Bytes(byte[] guid) {
        int[] intGuid = new int[16];
        String utf16Str = new String(guid, StandardCharsets.UTF_16LE).replaceAll("\\{", "")
          .replaceAll("-", "").replaceAll("}", "");
        for (int i = 0; i < utf16Str.length(); i += 2) {
            intGuid[i / 2] = Integer.parseUnsignedInt("" + utf16Str.charAt(i) + utf16Str.charAt(i + 1), 16);
        }
        return new GUID(intGuid);
    }

    @Override
    public int compareTo(GUID o) {
        return memcmp(guid, o.guid, 16);
    }

    public GUID(int[] guid) {
        this.guid = guid;
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

    public static int memcmp(int b1[], int b2[], int sz) {
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
        return sb.toString().toUpperCase();
    }

    public static GUID nil() {
        return new GUID(new int[16]);
    }

    @JsonIgnore
    public int[] getGuid() {
        return guid;
    }

    public GUID setGuid(int[] guid) {
        this.guid = guid;
        return this;
    }

    public String getGuidString() {
        return guid.toString();
    }
}
