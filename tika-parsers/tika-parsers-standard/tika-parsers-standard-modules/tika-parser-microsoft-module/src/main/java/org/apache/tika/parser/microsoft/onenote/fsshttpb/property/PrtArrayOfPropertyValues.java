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

import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.PropertySet;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.PropertyID;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitConverter;


/**
 * The class is used to represent the prtArrayOfPropertyValues .
 */
public class PrtArrayOfPropertyValues implements IProperty {
    public int CProperties;
    public PropertyID Prid;
    public PropertySet[] Data;

    /**
     * This method is used to deserialize the prtArrayOfPropertyValues from the specified byte array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return
     */
    public int DoDeserializeFromByteArray(byte[] byteArray, int startIndex) {
        int index = startIndex;
        this.CProperties = BitConverter.toInt32(byteArray, index);
        index += 4;
        this.Prid = new PropertyID();
        int len = this.Prid.DoDeserializeFromByteArray(byteArray, index);
        index += len;
        this.Data = new PropertySet[this.CProperties];
        for (int i = 0; i < this.CProperties; i++) {
            this.Data[i] = new PropertySet();
            int length = this.Data[i].DoDeserializeFromByteArray(byteArray, index);
            index += length;
        }

        return index - startIndex;
    }

    /**
     * This method is used to convert the element of the prtArrayOfPropertyValues into a byte List.
     *
     * @return Return the byte list which store the byte information of the prtArrayOfPropertyValues.
     */
    public List<Byte> SerializeToByteList() {
        List<Byte> byteList = new ArrayList<>();
        for (byte b : BitConverter.getBytes(this.CProperties)) {
            byteList.add(b);
        }
        byteList.addAll(this.Prid.SerializeToByteList());
        for (PropertySet ps : this.Data) {
            byteList.addAll(ps.SerializeToByteList());
        }
        return byteList;
    }
}
