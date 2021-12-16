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
import java.util.List;
import java.util.UUID;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitReader;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitWriter;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.GuidUtil;

public class SerialNumber extends BasicObject {
    public int type;
    public UUID guid;
    public long value;

    /**
     * Initializes a new instance of the SerialNumber class with specified values.
     *
     * @param identifier Specify the Guid value of the serialNumber.
     * @param value      Specify the value of the serialNumber.
     */
    public SerialNumber(UUID identifier, long value) {
        this.guid = identifier;
        this.value = value;
    }

    /**
     * Initializes a new instance of the SerialNumber class, this is the copy constructor.
     *
     * @param sn Specify the serial number where copy from.
     */
    public SerialNumber(SerialNumber sn) {
        this.guid = sn.guid;
        this.value = sn.value;
    }

    /**
     * Initializes a new instance of the SerialNumber class, this is default contractor
     */
    public SerialNumber() {
    }

    /**
     * This method is used to convert the element of SerialNumber basic object into a byte List.
     *
     * @return Return the byte list which store the byte information of SerialNumber.
     */
    @Override
    public List<Byte> serializeToByteList() throws IOException {
        BitWriter bitWriter = null;
        if (this.guid.equals(GuidUtil.emptyGuid())) {
            bitWriter = new BitWriter(1);
            bitWriter.appendUInit32(0, 8);
        } else {
            bitWriter = new BitWriter(25);
            bitWriter.appendUInit32(128, 8);
            bitWriter.appendGUID(this.guid);
            bitWriter.appendUInt64(this.value, 64);
        }

        return bitWriter.getByteList();
    }

    /**
     * This method is used to deserialize the SerialNumber basic object from the specified byte array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return Return the length in byte of the SerialNumber basic object.
     */
    @Override
    protected int doDeserializeFromByteArray(byte[] byteArray,
                                             int startIndex)
            throws IOException // return the length consumed
    {
        BitReader bitField = new BitReader(byteArray, startIndex);
        int type = bitField.readInt32(8);

        if (type == 0) {
            this.guid = GuidUtil.emptyGuid();
            this.type = 0;

            return 1;
        } else if (type == 128) {
            this.guid = bitField.readGuid();
            this.value = bitField.readUInt64(64);
            this.type = 128;
            return 25;
        } else {
            throw new IllegalArgumentException(
                    "Failed to parse SerialNumber object, Expect the type value is either 0 or 128, " +
                            "but the actual value is " + this.type);
        }
    }
}
