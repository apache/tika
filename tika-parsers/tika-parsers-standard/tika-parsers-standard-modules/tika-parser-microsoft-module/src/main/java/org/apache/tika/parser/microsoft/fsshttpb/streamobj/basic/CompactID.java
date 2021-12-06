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
package org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic;

import java.util.List;

import org.apache.tika.parser.microsoft.fsshttpb.util.BitReader;
import org.apache.tika.parser.microsoft.fsshttpb.util.BitWriter;

/**
 * This class is used to represent the CompactID structrue.
 */
public class CompactID {
    public long N;
    public long GuidIndex;

    /**
     * This method is used to convert the element of CompactID object into a byte List.
     *
     * @return Return the byte list which store the byte information of CompactID.
     */
    public List<Byte> SerializeToByteList() {
        BitWriter bitWriter = new BitWriter(4);
        bitWriter.AppendUInit32((int) this.N, 8);
        bitWriter.AppendUInit32((int) this.GuidIndex, 24);

        return bitWriter.getByteList();
    }

    /**
     * This method is used to deserialize the CompactID object from the specified byte array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return Return the length in byte of the CompactID object.
     */
    public int DoDeserializeFromByteArray(byte[] byteArray, int startIndex) {
        BitReader bitReader = new BitReader(byteArray, startIndex);
        this.N = bitReader.ReadUInt32(8);
        this.GuidIndex = bitReader.ReadUInt32(24);
        return 4;
    }
}