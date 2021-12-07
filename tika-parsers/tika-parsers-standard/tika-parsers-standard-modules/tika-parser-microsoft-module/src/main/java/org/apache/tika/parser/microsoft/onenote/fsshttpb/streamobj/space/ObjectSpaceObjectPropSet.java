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

import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.PropertySet;

/**
 * This class is used to represent a ObjectSpaceObjectPropSet.
 */
public class ObjectSpaceObjectPropSet {
    public ObjectSpaceObjectStreamOfOIDs oids;
    public ObjectSpaceObjectStreamOfOSIDs osids;
    public ObjectSpaceObjectStreamOfContextIDs contextIDs;
    public PropertySet body;
    public byte[] padding;

    /**
     * This method is used to deserialize the ObjectSpaceObjectPropSet from the specified byte array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return Return the length in byte of the ObjectSpaceObjectPropSet.
     */
    public int doDeserializeFromByteArray(byte[] byteArray, int startIndex) throws IOException {
        int index = startIndex;
        this.oids = new ObjectSpaceObjectStreamOfOIDs();
        int len = this.oids.doDeserializeFromByteArray(byteArray, index);
        index += len;
        if (this.oids.header.osidStreamNotPresent == 0) {
            this.osids = new ObjectSpaceObjectStreamOfOSIDs();
            len = this.osids.doDeserializeFromByteArray(byteArray, index);
            index += len;

            if (this.osids.header.extendedStreamsPresent == 1) {
                this.contextIDs = new ObjectSpaceObjectStreamOfContextIDs();
                len = this.contextIDs.doDeserializeFromByteArray(byteArray, index);
                index += len;
            }
        }
        this.body = new PropertySet();
        len = this.body.doDeserializeFromByteArray(byteArray, index);
        index += len;

        int paddingLength = 8 - (index - startIndex) % 8;
        if (paddingLength < 8) {
            this.padding = new byte[paddingLength];
            index += paddingLength;
        }
        return index - startIndex;
    }

    /**
     * This method is used to convert the element of the ObjectSpaceObjectPropSet into a byte List.
     *
     * @return Return the byte list which store the byte information of the ObjectSpaceObjectPropSet.
     */
    public List<Byte> serializeToByteList() throws IOException {
        List<Byte> byteList = new ArrayList<>();
        byteList.addAll(this.oids.serializeToByteList());
        byteList.addAll(this.osids.serializeToByteList());
        byteList.addAll(this.contextIDs.serializeToByteList());
        byteList.addAll(this.body.serializeToByteList());
        for (byte b : this.padding) {
            byteList.add(b);
        }
        return byteList;
    }
}
