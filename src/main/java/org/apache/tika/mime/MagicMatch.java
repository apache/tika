/**
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
package org.apache.tika.mime;

// JDK imports
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;

// Jakarta Commons Codec imports
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

/**
 * Defines a magic match.
 * 
 * 
 */
class MagicMatch implements Clause {

    private final static Hex HEX_CODEC = new Hex();

    private int offsetStart;

    private int offsetEnd;

    private String type;

    private BigInteger mask;

    private BigInteger value;

    private int length;

    MagicMatch(int offsetStart, int offsetEnd, String type, String mask,
            String value) throws MimeTypeException {

        this.offsetStart = offsetStart;
        this.offsetEnd = offsetEnd;
        this.type = type;
        try {
            byte[] decoded = decodeValue(type, value);
            this.length = decoded.length;
            this.value = new BigInteger(decoded);
            if (mask != null) {
                this.mask = new BigInteger(decodeValue(type, mask));
                this.value = this.value.and(this.mask);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new MimeTypeException(e);
        }
    }

    private byte[] decodeValue(String type, String value)
            throws DecoderException {

        // Preliminary check
        if ((value == null) || (type == null)) {
            return null;
        }

        byte[] decoded = null;
        String tmpVal = null;
        int radix = 8;

        // hex
        if (value.startsWith("0x")) {
            tmpVal = value.substring(2);
            radix = 16;
        } else {
            tmpVal = value;
            radix = 8;
        }

        if (type.equals("string")) {
            decoded = decodeString(value);

        } else if (type.equals("byte")) {
            decoded = tmpVal.getBytes();

        } else if (type.equals("host16") || type.equals("little16")) {
            int i = Integer.parseInt(tmpVal, radix);
            decoded = new byte[] { (byte) (i >> 8), (byte) (i & 0x00FF) };

        } else if (type.equals("big16")) {
            int i = Integer.parseInt(tmpVal, radix);
            decoded = new byte[] { (byte) (i >> 8), (byte) (i & 0x00FF) };

        } else if (type.equals("host32") || type.equals("little32")) {
            long i = Long.parseLong(tmpVal, radix);
            decoded = new byte[] { (byte) ((i & 0x000000FF)),
                    (byte) ((i & 0x0000FF00) >> 8),
                    (byte) ((i & 0x00FF0000) >> 16),
                    (byte) ((i & 0xFF000000) >> 24) };

        } else if (type.equals("big32")) {
            long i = Long.parseLong(tmpVal, radix);
            decoded = new byte[] { (byte) ((i & 0xFF000000) >> 24),
                    (byte) ((i & 0x00FF0000) >> 16),
                    (byte) ((i & 0x0000FF00) >> 8), (byte) ((i & 0x000000FF)) };
        }
        return decoded;
    }

    private byte[] decodeString(String value) throws DecoderException {

        if (value.startsWith("0x")) {
            return HEX_CODEC.decode(value.substring(2).getBytes());
        }

        try {
            ByteArrayOutputStream decoded = new ByteArrayOutputStream();

            for (int i = 0; i < value.length(); i++) {
                if (value.charAt(i) == '\\') {
                    if (value.charAt(i + 1) == '\\') {
                        decoded.write('\\');
                        i++;
                    } else if (value.charAt(i + 1) == 'x') {
                        decoded.write(HEX_CODEC.decode(value.substring(i + 2,
                                i + 4).getBytes()));
                        i += 3;
                    } else {
                        int j = i + 1;
                        while ((j < i + 4) && (j < value.length())
                                && (Character.isDigit(value.charAt(j)))) {
                            j++;
                        }
                        decoded.write(Short.decode(
                                "0" + value.substring(i + 1, j)).byteValue());
                        i = j - 1;
                    }
                } else {
                    decoded.write(value.charAt(i));
                }
            }
            return decoded.toByteArray();
        } catch (Exception e) {
            throw new DecoderException(e.toString() + " for " + value);
        }
    }

    public boolean eval(byte[] data) {
        for (int i = offsetStart; i <= offsetEnd; i++) {
            if (data.length < (this.length + i)) {
                // Not enough data...
                return false;
            }
            byte[] array = new byte[this.length];
            System.arraycopy(data, i, array, 0, this.length);
            BigInteger content = new BigInteger(array);
            // System.out.println("Evaluating " + content);
            if (mask != null) {
                content = content.and(mask);
            }
            if (value.equals(content)) {
                return true;
            }
        }
        return false;
    }

    public int size() {
        return length;
    }

    public String toString() {
        return "[" + offsetStart + ":" + offsetEnd
            + "(" + type + ")-" + mask + "#" + value + "]";
    }

}
