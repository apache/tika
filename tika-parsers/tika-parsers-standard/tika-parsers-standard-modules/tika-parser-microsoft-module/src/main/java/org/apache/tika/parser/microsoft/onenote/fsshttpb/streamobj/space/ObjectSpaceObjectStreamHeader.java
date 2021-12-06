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

package org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.space;

import java.util.List;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitReader;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitWriter;

public class ObjectSpaceObjectStreamHeader {
    public long Count;
    public int Reserved;
    public int ExtendedStreamsPresent;
    public int OsidStreamNotPresent;

    /**
     * This method is used to convert the element of ObjectSpaceObjectStreamHeader into a byte List.
     *
     * @return Return the byte list which store the byte information of ObjectSpaceObjectStreamHeader
     */
    public List<Byte> SerializeToByteList() {
        BitWriter bitWriter = new BitWriter(4);
        bitWriter.AppendUInit32((int) this.Count, 24);
        bitWriter.AppendInit32(this.Reserved, 6);
        bitWriter.AppendInit32(this.ExtendedStreamsPresent, 1);
        bitWriter.AppendInit32(this.OsidStreamNotPresent, 1);

        return bitWriter.getByteList();
    }

    /**
     * This method is used to deserialize the ObjectSpaceObjectStreamHeader object from
     * the specified byte array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return Return the length in byte of the ObjectSpaceObjectStreamHeader object.
     */
    public int DoDeserializeFromByteArray(byte[] byteArray, int startIndex) {
        BitReader bitReader = new BitReader(byteArray, startIndex);
        this.Count = bitReader.ReadUInt32(24);
        this.Reserved = bitReader.ReadInt32(6);
        this.ExtendedStreamsPresent = bitReader.ReadInt32(1);
        this.OsidStreamNotPresent = bitReader.ReadInt32(1);
        return 4;
    }
}
