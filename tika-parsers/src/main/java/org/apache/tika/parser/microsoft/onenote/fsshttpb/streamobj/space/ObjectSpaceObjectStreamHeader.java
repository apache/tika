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

import java.io.IOException;
import java.util.List;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitReader;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitWriter;

public class ObjectSpaceObjectStreamHeader {
    public long count;
    public int reserved;
    public int extendedStreamsPresent;
    public int osidStreamNotPresent;

    /**
     * This method is used to convert the element of ObjectSpaceObjectStreamHeader into a byte List.
     *
     * @return Return the byte list which store the byte information of ObjectSpaceObjectStreamHeader
     */
    public List<Byte> serializeToByteList() throws IOException {
        BitWriter bitWriter = new BitWriter(4);
        bitWriter.appendUInit32((int) this.count, 24);
        bitWriter.appendInit32(this.reserved, 6);
        bitWriter.appendInit32(this.extendedStreamsPresent, 1);
        bitWriter.appendInit32(this.osidStreamNotPresent, 1);

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
    public int doDeserializeFromByteArray(byte[] byteArray, int startIndex) throws IOException {
        BitReader bitReader = new BitReader(byteArray, startIndex);
        this.count = bitReader.readUInt32(24);
        this.reserved = bitReader.readInt32(6);
        this.extendedStreamsPresent = bitReader.readInt32(1);
        this.osidStreamNotPresent = bitReader.readInt32(1);
        return 4;
    }
}
