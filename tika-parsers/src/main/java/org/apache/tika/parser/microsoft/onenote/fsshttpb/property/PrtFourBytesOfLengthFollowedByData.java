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

package org.apache.tika.parser.microsoft.onenote.fsshttpb.property;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitConverter;

/**
 * This class is used to represent the prtFourBytesOfLengthFollowedByData.
 */
public class PrtFourBytesOfLengthFollowedByData implements IProperty {
    public int cb;

    public byte[] data;

    /**
     * This method is used to deserialize the prtFourBytesOfLengthFollowedByData from
     * the specified byte array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return Return the length in byte of the prtFourBytesOfLengthFollowedByData.
     */
    public int doDeserializeFromByteArray(byte[] byteArray, int startIndex) throws IOException {
        int index = startIndex;
        this.cb = (int) BitConverter.toUInt32(byteArray, startIndex);
        index += 4;
        this.data = Arrays.copyOfRange(byteArray, index, index + this.cb);
        index += this.cb;

        return index - startIndex;
    }

    /**
     * This method is used to convert the element of prtFourBytesOfLengthFollowedByData into a byte List.
     *
     * @return Return the byte list which store the byte information of prtFourBytesOfLengthFollowedByData.
     */
    public List<Byte> serializeToByteList() {
        List<Byte> byteList = new ArrayList<>();
        for (byte b : BitConverter.getBytes(this.cb)) {
            byteList.add(b);
        }
        for (byte b : this.data) {
            byteList.add(b);
        }
        return byteList;
    }
}
