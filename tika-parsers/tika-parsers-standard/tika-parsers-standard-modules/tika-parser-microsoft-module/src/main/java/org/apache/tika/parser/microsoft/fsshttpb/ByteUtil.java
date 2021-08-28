package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;

public final class ByteUtil {
    public static byte[] toByteArray(List<Byte> bytes) {
        byte[] res = new byte[bytes.size()];
        for (int i = 0; i < res.length; ++i) {
            res[i] = bytes.get(i);
        }
        return res;
    }
}
