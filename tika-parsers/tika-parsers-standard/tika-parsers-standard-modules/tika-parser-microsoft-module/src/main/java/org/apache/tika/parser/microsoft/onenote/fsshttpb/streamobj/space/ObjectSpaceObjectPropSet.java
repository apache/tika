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

import java.util.ArrayList;
import java.util.List;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.PropertySet;

/**
 * This class is used to represent a ObjectSpaceObjectPropSet.
 */
public class ObjectSpaceObjectPropSet {
    public ObjectSpaceObjectStreamOfOIDs OIDs;
    public ObjectSpaceObjectStreamOfOSIDs OSIDs;
    public ObjectSpaceObjectStreamOfContextIDs ContextIDs;
    public PropertySet Body;
    public byte[] Padding;

    /**
     * This method is used to deserialize the ObjectSpaceObjectPropSet from the specified byte array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return Return the length in byte of the ObjectSpaceObjectPropSet.
     */
    public int doDeserializeFromByteArray(byte[] byteArray, int startIndex) {
        int index = startIndex;
        this.OIDs = new ObjectSpaceObjectStreamOfOIDs();
        int len = this.OIDs.doDeserializeFromByteArray(byteArray, index);
        index += len;
        if (this.OIDs.Header.OsidStreamNotPresent == 0) {
            this.OSIDs = new ObjectSpaceObjectStreamOfOSIDs();
            len = this.OSIDs.doDeserializeFromByteArray(byteArray, index);
            index += len;

            if (this.OSIDs.Header.ExtendedStreamsPresent == 1) {
                this.ContextIDs = new ObjectSpaceObjectStreamOfContextIDs();
                len = this.ContextIDs.doDeserializeFromByteArray(byteArray, index);
                index += len;
            }
        }
        this.Body = new PropertySet();
        len = this.Body.doDeserializeFromByteArray(byteArray, index);
        index += len;

        int paddingLength = 8 - (index - startIndex) % 8;
        if (paddingLength < 8) {
            this.Padding = new byte[paddingLength];
            index += paddingLength;
        }
        return index - startIndex;
    }

    /**
     * This method is used to convert the element of the ObjectSpaceObjectPropSet into a byte List.
     *
     * @return Return the byte list which store the byte information of the ObjectSpaceObjectPropSet.
     */
    public List<Byte> serializeToByteList() {
        List<Byte> byteList = new ArrayList<>();
        byteList.addAll(this.OIDs.serializeToByteList());
        byteList.addAll(this.OSIDs.serializeToByteList());
        byteList.addAll(this.ContextIDs.serializeToByteList());
        byteList.addAll(this.Body.serializeToByteList());
        for (byte b : this.Padding) {
            byteList.add(b);
        }
        return byteList;
    }
}
