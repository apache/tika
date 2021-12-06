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

import java.util.List;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitReader;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitWriter;

/**
 * A 9-byte encoding of values in the range 0x0002000000000000 through 0xFFFFFFFFFFFFFFFF
 */
public class Compact64bitInt extends BasicObject {
    /**
     * Specify the type value for compact uint zero type value.
     */
    public static final int CompactUintNullType = 0;

    /**
     * Specify the type value for compact uint 7 bits type value.
     */
    public static final int CompactUint7bitType = 1;

    /**
     * Specify the type value for compact uint 14 bits type value.
     */
    public static final int CompactUint14bitType = 2;

    /**
     * Specify the type value for compact uint 21 bits type value.
     */
    public static final int CompactUint21bitType = 4;

    /**
     * Specify the type value for compact uint 28 bits type value.
     */
    public static final int CompactUint28bitType = 8;

    /**
     * Specify the type value for compact uint 35 bits type value.
     */
    public static final int CompactUint35bitType = 16;

    /**
     * Specify the type value for compact uint 42 bits type value.
     */
    public static final int CompactUint42bitType = 32;

    /**
     * Specify the type value for compact uint 49 bits type value.
     */
    public static final int CompactUint49bitType = 64;

    /**
     * Specify the type value for compact uint 64 bits type value.
     */
    public static final int CompactUint64bitType = 128;
    private int type;
    private long decodedValue;

    /**
     * Initializes a new instance of the Compact64bitInt class with specified value.
     *
     * @param decodedValue Decoded value
     */
    public Compact64bitInt(long decodedValue) {
        this.decodedValue = decodedValue;
    }
    /**
     * Initializes a new instance of the Compact64bitInt class, this is the default constructor.
     */
    public Compact64bitInt() {
        this.decodedValue = 0;
    }

    /**
     * This method is used to convert the element of Compact64bitInt basic object into a byte List.
     *
     * @return Return the byte list which store the byte information of Compact64bitInt.
     */
    @Override
    public List<Byte> SerializeToByteList() {
        BitWriter bitWriter = new BitWriter(9);

        if (this.decodedValue == 0) {
            bitWriter.AppendUInt64(0, 8);
        } else if (this.decodedValue >= 0x01 && this.decodedValue <= 0x7F) {
            bitWriter.AppendUInt64(CompactUint7bitType, 1);
            bitWriter.AppendUInt64(this.decodedValue, 7);
        } else if (this.decodedValue >= 0x0080 && this.decodedValue <= 0x3FFF) {
            bitWriter.AppendUInt64(CompactUint14bitType, 2);
            bitWriter.AppendUInt64(this.decodedValue, 14);
        } else if (this.decodedValue >= 0x004000 && this.decodedValue <= 0x1FFFFF) {
            bitWriter.AppendUInt64(CompactUint21bitType, 3);
            bitWriter.AppendUInt64(this.decodedValue, 21);
        } else if (this.decodedValue >= 0x0200000 && this.decodedValue <= 0xFFFFFFF) {
            bitWriter.AppendUInt64(CompactUint28bitType, 4);
            bitWriter.AppendUInt64(this.decodedValue, 28);
        } else if (this.decodedValue >= 0x010000000 && this.decodedValue <= 0x7FFFFFFFFL) {
            bitWriter.AppendUInt64(CompactUint35bitType, 5);
            bitWriter.AppendUInt64(this.decodedValue, 35);
        } else if (this.decodedValue >= 0x00800000000L && this.decodedValue <= 0x3FFFFFFFFFFL) {
            bitWriter.AppendUInt64(CompactUint42bitType, 6);
            bitWriter.AppendUInt64(this.decodedValue, 42);
        } else if (this.decodedValue >= 0x0040000000000L && this.decodedValue <= 0x1FFFFFFFFFFFFL) {
            bitWriter.AppendUInt64(CompactUint49bitType, 7);
            bitWriter.AppendUInt64(this.decodedValue, 49);
        } else if (this.decodedValue >= 0x0002000000000000L) {
            bitWriter.AppendUInt64(CompactUint64bitType, 8);
            bitWriter.AppendUInt64(this.decodedValue, 64);
        }
        return bitWriter.getByteList();
    }

    /**
     * This method is used to deserialize the Compact64bitInt basic object from the specified byte
     * array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return Return the length in byte of the Compact64bitInt basic object.
     */
    @Override
    protected int DoDeserializeFromByteArray(byte[] byteArray,
                                             int startIndex) // return the length consumed
    {
        BitReader bitReader = new BitReader(byteArray, startIndex);
        int numberOfContinousZeroBit = 0;
        while (numberOfContinousZeroBit < 8 && bitReader.MoveNext()) {
            if (!bitReader.getCurrent()) {
                numberOfContinousZeroBit++;
            } else {
                break;
            }
        }

        switch (numberOfContinousZeroBit) {
            case 0:
                this.decodedValue = bitReader.ReadUInt64(7);
                this.type = CompactUint7bitType;
                return 1;

            case 1:
                this.decodedValue = bitReader.ReadUInt64(14);
                this.type = CompactUint14bitType;
                return 2;

            case 2:
                this.decodedValue = bitReader.ReadUInt64(21);
                this.type = CompactUint21bitType;
                return 3;

            case 3:
                this.decodedValue = bitReader.ReadUInt64(28);
                this.type = CompactUint28bitType;
                return 4;

            case 4:
                this.decodedValue = bitReader.ReadUInt64(35);
                this.type = CompactUint35bitType;
                return 5;

            case 5:
                this.decodedValue = bitReader.ReadUInt64(42);
                this.type = CompactUint42bitType;
                return 6;

            case 6:
                this.decodedValue = bitReader.ReadUInt64(49);
                this.type = CompactUint49bitType;
                return 7;

            case 7:
                this.decodedValue = bitReader.ReadUInt64(64);
                this.type = CompactUint64bitType;
                return 9;

            case 8:
                this.decodedValue = 0;
                this.type = CompactUintNullType;
                return 1;

            default:
                throw new RuntimeException(
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
