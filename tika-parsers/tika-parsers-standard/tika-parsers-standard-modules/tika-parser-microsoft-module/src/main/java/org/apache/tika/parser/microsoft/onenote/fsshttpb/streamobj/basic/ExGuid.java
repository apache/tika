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
import java.util.UUID;

import org.apache.poi.openxml4j.exceptions.InvalidOperationException;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitReader;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitWriter;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.GuidUtil;

public class ExGuid extends BasicObject {
    /**
     * Specify the extended GUID null type value.
     */
    public static final int ExtendedGUIDNullType = 0;

    /**
     * Specify the extended GUID 5 Bit int type value.
     */
    public static final int ExtendedGUID5BitUintType = 4;

    /**
     * Specify the extended GUID 10 Bit int type value.
     */
    public static final int ExtendedGUID10BitUintType = 32;

    /**
     * Specify the extended GUID 17 Bit int type value.
     */
    public static final int ExtendedGUID17BitUintType = 64;

    /**
     * Specify the extended GUID 32 Bit int type value.
     */
    public static final int ExtendedGUID32BitUintType = 128;
    public int type;
    public int value;
    public UUID guid;

    /**
     * Initializes a new instance of the ExGuid class with specified value.
     *
     * @param value      Specify the ExGUID Value.
     * @param identifier Specify the ExGUID GUID value.
     */
    public ExGuid(int value, UUID identifier) {
        this.value = value;
        this.guid = identifier;
    }

    /**
     * Initializes a new instance of the ExGuid class, this is the copy constructor.
     *
     * @param guid2 Specify the ExGuid instance where copies from.
     */
    public ExGuid(ExGuid guid2) {
        this.value = guid2.value;
        this.guid = guid2.guid;
        this.type = guid2.type;
    }

    /**
     * Initializes a new instance of the ExGuid class, this is a default constructor.
     */
    public ExGuid() {
        this.guid = GuidUtil.emptyGuid();
    }

    /**
     * This method is used to convert the element of ExGuid basic object into a byte List.
     *
     * @return Return the byte list which store the byte information of ExGuid.
     */
    @Override
    public List<Byte> SerializeToByteList() {
        BitWriter bitWriter = new BitWriter(21);

        if (this.guid.equals(GuidUtil.emptyGuid())) {
            bitWriter.AppendUInit32(0, 8);
        } else if (this.value >= 0x00 && this.value <= 0x1F) {
            bitWriter.AppendUInit32(ExtendedGUID5BitUintType, 3);
            bitWriter.AppendUInit32(this.value, 5);
            bitWriter.AppendGUID(this.guid);
        } else if (this.value >= 0x20 && this.value <= 0x3FF) {
            bitWriter.AppendUInit32(ExtendedGUID10BitUintType, 6);
            bitWriter.AppendUInit32(this.value, 10);
            bitWriter.AppendGUID(this.guid);
        } else if (this.value >= 0x400 && this.value <= 0x1FFFF) {
            bitWriter.AppendUInit32(ExtendedGUID17BitUintType, 7);
            bitWriter.AppendUInit32(this.value, 17);
            bitWriter.AppendGUID(this.guid);
        } else if (this.value >= 0x20000) {
            bitWriter.AppendUInit32(ExtendedGUID32BitUintType, 8);
            bitWriter.AppendUInit32(this.value, 32);
            bitWriter.AppendGUID(this.guid);
        }

        return bitWriter.getByteList();
    }

    /**
     * Override the Equals method.
     *
     * @param obj Specify the object.
     * @return Return true if equals, otherwise return false.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ExGuid) {
            ExGuid another = (ExGuid) obj;

            if (this.guid != null && another.guid != null) {
                return another.guid.equals(this.guid) && another.value == this.value;
            }
        }
        return false;
    }

    /**
     * Override the GetHashCode.
     *
     * @return Return the hash value.
     */
    @Override
    public int hashCode() {
        return this.guid.hashCode() + new Integer(this.value).hashCode();
    }

    /**
     * This method is used to deserialize the ExGuid basic object from the specified byte array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return Return the length in byte of the ExGuid basic object.
     */
    @Override
    protected int DoDeserializeFromByteArray(byte[] byteArray, int startIndex) {
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
            case 2:
                this.value = bitReader.ReadUInt32(5);
                this.guid = bitReader.ReadGuid();
                this.type = ExtendedGUID5BitUintType;
                return 17;

            case 5:
                this.value = bitReader.ReadUInt32(10);
                this.guid = bitReader.ReadGuid();
                this.type = ExtendedGUID10BitUintType;
                return 18;

            case 6:
                this.value = bitReader.ReadUInt32(17);
                this.guid = bitReader.ReadGuid();
                this.type = ExtendedGUID17BitUintType;
                return 19;

            case 7:
                this.value = bitReader.ReadUInt32(32);
                this.guid = bitReader.ReadGuid();
                this.type = ExtendedGUID32BitUintType;
                return 21;

            case 8:
                this.guid = GuidUtil.emptyGuid();
                this.type = ExtendedGUIDNullType;
                return 1;

            default:
                throw new InvalidOperationException(
                        "Failed to parse the ExGuid, the type value is unexpected");
        }
    }
}
