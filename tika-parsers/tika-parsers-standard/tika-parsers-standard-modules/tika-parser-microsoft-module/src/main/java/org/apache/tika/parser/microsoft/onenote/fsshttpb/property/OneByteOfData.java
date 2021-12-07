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

import java.util.ArrayList;
import java.util.List;

/**
 * This class is used to represent the property contains 1 byte of data in the PropertySet.rgData stream field.
 */
public class OneByteOfData implements IProperty {
    public byte data;

    /**
     * This method is used to deserialize the OneByteOfData from the specified byte array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return
     */
    public int doDeserializeFromByteArray(byte[] byteArray, int startIndex) {
        this.data = byteArray[startIndex];
        return 1;
    }

    /**
     * This method is used to convert the element of OneByteOfData into a byte List.
     *
     * @return Return the byte list which store the byte information of OneByteOfData.
     */
    public List<Byte> serializeToByteList() {
        return new ArrayList<>(this.data);
    }
}
