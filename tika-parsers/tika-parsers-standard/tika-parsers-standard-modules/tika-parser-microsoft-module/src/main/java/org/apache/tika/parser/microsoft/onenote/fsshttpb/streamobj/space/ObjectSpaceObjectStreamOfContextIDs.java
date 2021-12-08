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
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.CompactID;

/**
 * This class is used to represent a ObjectSpaceObjectStreamOfContextIDs.
 */
public class ObjectSpaceObjectStreamOfContextIDs {
    public ObjectSpaceObjectStreamHeader header;
    public CompactID[] body;

    /**
     * This method is used to convert the element of ObjectSpaceObjectStreamOfContextIDs object into a byte List.
     *
     * @return Return the byte list which store the byte information of ObjectSpaceObjectStreamOfContextIDs
     */
    public List<Byte> serializeToByteList() throws IOException {
        List<Byte> byteList = new ArrayList<>();
        byteList.addAll(this.header.serializeToByteList());
        for (CompactID compactID : this.body) {
            byteList.addAll(compactID.serializeToByteList());
        }

        return byteList;
    }

    /**
     * This method is used to deserialize the ObjectSpaceObjectStreamOfContextIDs object
     * from the specified byte array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return Return the length in byte of the ObjectSpaceObjectStreamOfContextIDs object.
     */
    public int doDeserializeFromByteArray(byte[] byteArray, int startIndex) throws IOException {
        int index = startIndex;
        this.header = new ObjectSpaceObjectStreamHeader();
        int headerCount = this.header.doDeserializeFromByteArray(byteArray, index);
        index += headerCount;

        this.body = new CompactID[(int) this.header.count];
        for (int i = 0; i < this.header.count; i++) {
            CompactID compactID = new CompactID();
            int count = compactID.doDeserializeFromByteArray(byteArray, startIndex);
            this.body[i] = compactID;
            index += count;
        }

        return index - startIndex;
    }
}
