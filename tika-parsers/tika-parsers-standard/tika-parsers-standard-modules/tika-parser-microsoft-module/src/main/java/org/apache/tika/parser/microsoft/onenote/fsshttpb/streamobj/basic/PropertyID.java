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

import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitConverter;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitReader;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitWriter;

/**
 * This class is used to represent a PropertyID.
 */
public class PropertyID {
    public int Id;
    public int Type;
    public int BoolValue;
    public int Value;

    /**
     * This method is used to convert the element of PropertyID object into a byte List.
     *
     * @return Return the byte list which store the byte information of PropertyID.
     */
    public List<Byte> serializeToByteList() {
        BitWriter bitWriter = new BitWriter(4);
        bitWriter.appendUInit32(this.Id, 26);
        bitWriter.appendUInit32(this.Type, 5);
        bitWriter.appendInit32(this.BoolValue, 1);

        return bitWriter.getByteList();
    }

    /**
     * This method is used to deserialize the PropertyID object from the specified byte array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return Return the length in byte of the PropertyID object.
     */
    public int doDeserializeFromByteArray(byte[] byteArray, int startIndex) {
        BitReader bitReader = new BitReader(byteArray, startIndex);
        this.Id = bitReader.readUInt32(26);
        this.Type = bitReader.readUInt32(5);
        this.BoolValue = bitReader.readInt32(1);
        this.Value = BitConverter.toInt32(byteArray, startIndex);
        return 4;
    }
}
