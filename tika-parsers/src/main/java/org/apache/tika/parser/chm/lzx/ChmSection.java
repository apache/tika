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
package org.apache.tika.parser.chm.lzx;

import java.math.BigInteger;
import java.util.Arrays;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.chm.core.ChmCommons;

public class ChmSection {
    private byte[] data;
    private int swath;// kiks
    private int total;// remains
    private int buffer;// val

    public ChmSection(byte[] data) throws TikaException {
        ChmCommons.assertByteArrayNotNull(data);
        setData(data);
    }

    /* Utilities */
    public byte[] reverseByteOrder(byte[] toBeReversed) throws TikaException {
        ChmCommons.assertByteArrayNotNull(toBeReversed);
        ChmCommons.reverse(toBeReversed);
        return toBeReversed;
    }

    public int checkBit(int i) {
        return ((getBuffer() & (1 << (getTotal() - i))) == 0) ? 0 : 1;
    }

    public int getSyncBits(int bit) {
        return getDesyncBits(bit, bit);
    }

    public int getDesyncBits(int bit, int removeBit) {
        while (getTotal() < 16) {
            setBuffer((getBuffer() << 16) + unmarshalUByte()
                    + (unmarshalUByte() << 8));
            setTotal(getTotal() + 16);
        }
        int tmp = (getBuffer() >>> (getTotal() - bit));
        setTotal(getTotal() - removeBit);
        setBuffer(getBuffer() - ((getBuffer() >>> getTotal()) << getTotal()));
        return tmp;
    }

    public int unmarshalUByte() {
        return (int) (getByte() & 255);
    }

    public byte getByte() {
        if (getSwath() < getData().length) {
            setSwath(getSwath() + 1);
            return getData()[getSwath() - 1];
        } else
            return 0;
    }

    public int getLeft() {
        return (getData().length - getSwath());
    }

    public byte[] getData() {
        return data;
    }

    public BigInteger getBigInteger(int i) {
        if (getData() == null)
            return BigInteger.ZERO;
        if (getData().length - getSwath() < i)
            i = getData().length - getSwath();
        byte[] tmp = new byte[i];
        for (int j = i - 1; j >= 0; j--) {
            tmp[i - j - 1] = getData()[getSwath() + j];
        }
        setSwath(getSwath() + i);
        return new BigInteger(tmp);
    }

    public byte[] stringToAsciiBytes(String s) {
        char[] c = s.toCharArray();
        byte[] byteval = new byte[c.length];
        for (int i = 0; i < c.length; i++)
            byteval[i] = (byte) c[i];
        return byteval;
    }

    public BigInteger unmarshalUlong() {
        return getBigInteger(8);
    }

    public long unmarshalUInt() {
        return getBigInteger(4).longValue();
    }

    public int unmarshalInt() {
        return getBigInteger(4).intValue();
    }

    public byte[] unmarshalBytes(int i) {
        if (i == 0)
            return new byte[1];
        byte[] t = new byte[i];
        for (int j = 0; j < i; j++)
            t[j] = getData()[j + getSwath()];
        setSwath(getSwath() + i);
        return t;
    }

    public BigInteger getEncint() {
        byte ob;
        BigInteger bi = BigInteger.ZERO;
        byte[] nb = new byte[1];
        while ((ob = this.getByte()) < 0) {
            nb[0] = (byte) ((ob & 0x7f));
            bi = bi.shiftLeft(7).add(new BigInteger(nb));
        }
        nb[0] = (byte) ((ob & 0x7f));
        bi = bi.shiftLeft(7).add(new BigInteger(nb));
        return bi;
    }

    public char unmarshalUtfChar() {
        byte ob;
        int i = 1;
        byte[] ba;
        ob = this.getByte();
        if (ob < 0) {
            i = 2;
            while ((ob << (24 + i)) < 0)
                i++;
        }
        ba = new byte[i];
        ba[0] = ob;
        int j = 1;
        while (j < i) {
            ba[j] = this.getByte();
            j++;
        }
        i = ba.length;
        if (i == 1)
            return (char) ba[0];
        else {
            int n;
            n = ba[0] & 15; // 00001111b, gets last 4 bits
            j = 1;
            while (j < i)
                n = (n << 6) + (ba[j++] & 63);// 00111111b,gets last 6 bits
            return (char) n;
        }
    }

    private void setData(byte[] data) {
        this.data = data;
    }

    public int getSwath() {
        return swath;
    }

    public void setSwath(int swath) {
        this.swath = swath;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    private int getBuffer() {
        return buffer;
    }

    private void setBuffer(int buffer) {
        this.buffer = buffer;
    }

    /**
     * @param args
     * @throws TikaException 
     */
    public static void main(String[] args) throws TikaException {
        byte[] array = { 4, 78, -67, 90, 1, -33 };
        ChmSection chmSection = new ChmSection(array);
        System.out.println("before " + Arrays.toString(array));
        System.out.println("after " + Arrays.toString(chmSection.reverseByteOrder(array)));
    }
}
