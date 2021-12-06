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
 * This class is used to represent a JCID
 */
public class JCID {
    public int Index;
    public int IsBinary;
    public int IsPropertySet;
    public int IsGraphNode;
    public int IsFileData;
    public int IsReadOnly;
    public int Reserved;

    /**
     * This method is used to convert the element of JCID object into a byte List.
     *
     * @return Return the byte list which store the byte information of JCID
     */
    public List<Byte> SerializeToByteList() {
        BitWriter bitWriter = new BitWriter(4);
        bitWriter.AppendInit32(this.Index, 16);
        bitWriter.AppendInit32(this.IsBinary, 1);
        bitWriter.AppendInit32(this.IsPropertySet, 1);
        bitWriter.AppendInit32(this.IsGraphNode, 1);
        bitWriter.AppendInit32(this.IsFileData, 1);
        bitWriter.AppendInit32(this.IsReadOnly, 1);
        bitWriter.AppendInit32(this.Reserved, 11);

        return bitWriter.getByteList();
    }

    /**
     * This method is used to deserialize the JCID object from the specified byte array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return Return the length in byte of the JCID object.
     */
    public int DoDeserializeFromByteArray(byte[] byteArray, int startIndex) {
        BitReader bitReader = new BitReader(byteArray, startIndex);
        this.Index = bitReader.ReadInt32(16);
        this.IsBinary = bitReader.ReadInt32(1);
        this.IsPropertySet = bitReader.ReadInt32(1);
        this.IsGraphNode = bitReader.ReadInt32(1);
        this.IsFileData = bitReader.ReadInt32(1);
        this.IsReadOnly = bitReader.ReadInt32(1);
        this.Reserved = bitReader.ReadInt32(11);

        return 4;
    }
}
