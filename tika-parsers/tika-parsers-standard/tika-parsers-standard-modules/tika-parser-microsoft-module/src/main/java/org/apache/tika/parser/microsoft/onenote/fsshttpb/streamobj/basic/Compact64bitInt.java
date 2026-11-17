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
package org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic;

import java.io.IOException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitReader;

/**
 * Compact unsigned 64-bit integer: 1-9 bytes; the low-order zero bits of byte 0 give the width.
 */
public class Compact64bitInt extends BasicObject {
    public static final int CompactUintNullType = 0;

    public static final int CompactUint7bitType = 1;

    public static final int CompactUint14bitType = 2;

    public static final int CompactUint21bitType = 4;

    public static final int CompactUint28bitType = 8;

    public static final int CompactUint35bitType = 16;

    public static final int CompactUint42bitType = 32;

    public static final int CompactUint49bitType = 64;

    public static final int CompactUint64bitType = 128;
    private int type;
    private long decodedValue;

    public Compact64bitInt(long decodedValue) {
        this.decodedValue = decodedValue;
    }

    public Compact64bitInt() {
        this.decodedValue = 0;
    }

    @Override
    protected int doDeserializeFromByteArray(byte[] byteArray,
                                             int startIndex)
            throws IOException, TikaException // return the length consumed
    {
        BitReader bitReader = new BitReader(byteArray, startIndex);
        int numberOfContinousZeroBit = 0;
        while (numberOfContinousZeroBit < 8 && bitReader.moveNext()) {
            if (!bitReader.getCurrent()) {
                numberOfContinousZeroBit++;
            } else {
                break;
            }
        }

        switch (numberOfContinousZeroBit) {
            case 0:
                this.decodedValue = bitReader.readUInt64(7);
                this.type = CompactUint7bitType;
                return 1;

            case 1:
                this.decodedValue = bitReader.readUInt64(14);
                this.type = CompactUint14bitType;
                return 2;

            case 2:
                this.decodedValue = bitReader.readUInt64(21);
                this.type = CompactUint21bitType;
                return 3;

            case 3:
                this.decodedValue = bitReader.readUInt64(28);
                this.type = CompactUint28bitType;
                return 4;

            case 4:
                this.decodedValue = bitReader.readUInt64(35);
                this.type = CompactUint35bitType;
                return 5;

            case 5:
                this.decodedValue = bitReader.readUInt64(42);
                this.type = CompactUint42bitType;
                return 6;

            case 6:
                this.decodedValue = bitReader.readUInt64(49);
                this.type = CompactUint49bitType;
                return 7;

            case 7:
                this.decodedValue = bitReader.readUInt64(64);
                this.type = CompactUint64bitType;
                return 9;

            case 8:
                this.decodedValue = 0;
                this.type = CompactUintNullType;
                return 1;

            default:
                throw new TikaException(
                        "Failed to parse the Compact64bitInt, the type value is unexpected");
        }
    }

    public int getType() {
        return type;
    }

    public Compact64bitInt setType(int type) {
        this.type = type;
        return this;
    }

    public long getDecodedValue() {
        return decodedValue;
    }

    public Compact64bitInt setDecodedValue(long decodedValue) {
        this.decodedValue = decodedValue;
        return this;
    }
}
