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
package org.apache.tika.detect;

import java.util.ArrayList;

class BerValue {
    static BerValue[] next(byte[] input) {
        return next(input, 0, input.length);
    }

    static BerValue[] next(byte[] input, int from, int to) {
        final ArrayList<BerValue> list = new ArrayList<>();
        BerValue e;
        while ((e = next(input, from, to, true)) != null) {
            list.add(e);
            // increase the offset by current total length
            from += e.header + e.length + (e.isIndefinite() ? 2 : 0);
        }
        // if there are trailing bytes the input is not a valid DER
        if (from == to) {
            // convert the list to array
            return list.toArray(new BerValue[list.size()]);
        } else {
            return null;
        }
    }

    static BerValue next(byte[] input, int from, int to, boolean recurse) {
        if (to > from + 1 && input.length >= to) {
            final int type = input[from] & 0xff;
            final int tagClass = type >> 6;
            final int tag = type & 0x1f;
            // standard tags only
            if (tag != 31) {
                int length = input[from + 1] & 0xff;
                int header = 2;
                if ((length & 0x80) != 0) {
                    final int bytes = length & 0x7f;
                    if (bytes == 0) {
                        // indefinite form
                        if (recurse) {
                            length = 0;
                            int off = from + header;
                            BerValue bv;
                            while (!((bv = next(input, off + length, to, recurse)) == null || bv.isEOC())) {
                                length += bv.header + bv.length + (bv.isIndefinite() ? 2 : 0);
                            }
                            if (bv != null) {
                                return new BerValue(tagClass, tag, header, from, length, true);
                            }
                        } else {
                            return new BerValue(tagClass, tag, header, from, -1, true);
                        }
                    } else if (bytes < 4) {
                        // definite long form.
                        // accept 4 bytes (integer) length at most.
                        header += bytes;
                        if ((from + header) <= to) {
                            length = input[from + 2] & 0xff;
                            for (int i = 3; i < header; i++) {
                                length = length << 8 | (input[from + i] & 0xff);
                            }
                            return new BerValue(tagClass, tag, header, from, length, false);
                        }
                    }
                } else {
                    // definite short form
                    return new BerValue(tagClass, tag, header, from, length, false);
                }
            }
        }
        return null;
    }

    private int tagClass;
    private int tag;
    private int header;
    private int offset;
    private int length;
    private boolean indef;

    private BerValue(int tagClass, int tag, int header, int offset, int length, boolean indef) {
        this.tagClass = tagClass;
        this.tag = tag;
        this.header = header;
        this.offset = offset;
        this.length = length;
        this.indef = indef;
    }

    public int getHeader() {
        return header;
    }

    public int getLength() {
        return length;
    }

    public int getOffset() {
        return offset;
    }

    boolean isContext(int tag) {
        return tagClass == 2 && this.tag == tag;
    }

    boolean isEOC() {
        return tagClass == 0 && tag == 0;
    }

    public boolean isIndefinite() {
        return indef;
    }

    boolean isInteger() {
        return tagClass == 0 && tag == 2;
    }

    boolean isOID() {
        return tagClass == 0 && tag == 6;
    }

    boolean isSequence() {
        return tagClass == 0 && tag == 16;
    }

    boolean isSet() {
        return tagClass == 0 && tag == 17;
    }
}
