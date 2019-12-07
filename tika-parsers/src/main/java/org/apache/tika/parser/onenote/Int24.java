package org.apache.tika.parser.onenote;

public class Int24 {
    int[] val = new int[3];

    public Int24(int b1, int b2, int b3) {
        val[0] = b1;
        val[1] = b2;
        val[2] = b3;
    }

    public int value() {
        int le = val[2];
        le <<= 8;
        le |= val[1];
        le <<= 8;
        le |= val[0];
        return le;
    }
}
