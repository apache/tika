package org.apache.tika.parser.microsoft.fsshttpb.util;

import java.util.ArrayList;
import java.util.List;

public final class ByteUtil {
    public static byte[] toByteArray(List<Byte> bytes) {
        byte[] res = new byte[bytes.size()];
        for (int i = 0; i < res.length; ++i) {
            res[i] = bytes.get(i);
        }
        return res;
    }

    public static List<Byte> toListOfByte(byte[] bytes) {
        List<Byte> listOfByte = new ArrayList<>();
        for (byte b : bytes) {
            listOfByte.add(b);
        }
        return listOfByte;
    }

    public static void appendByteArrayToListOfByte(List<Byte> byteList, byte[] byteArrayToAdd) {
        for (byte b : byteArrayToAdd) {
            byteList.add(b);
        }
    }
}
