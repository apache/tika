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

package org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj;

import java.util.ArrayList;
import java.util.List;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.ArrayNumber;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.EightBytesOfData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.FourBytesOfData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.IProperty;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.NoData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.OneByteOfData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.PrtArrayOfPropertyValues;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.PrtFourBytesOfLengthFollowedByData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.TwoBytesOfData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.PropertyID;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.PropertyType;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitConverter;

/**
 * This class is used to represent a PropertySet.
 */
public class PropertySet implements IProperty {
    public int CProperties;

    public PropertyID[] RgPrids;
    public List<IProperty> RgData;

    /**
     * This method is used to convert the element of PropertySet into a byte List.
     *
     * @return Return the byte list which store the byte information of PropertySet.
     */
    public List<Byte> SerializeToByteList() {
        List<Byte> byteList = new ArrayList<>();
        for (byte b : BitConverter.getBytes(this.CProperties)) {
            byteList.add(b);
        }

        for (PropertyID propertyId : this.RgPrids) {
            byteList.addAll(propertyId.SerializeToByteList());
        }

        for (IProperty property : this.RgData) {
            byteList.addAll(property.SerializeToByteList());
        }

        return byteList;
    }

    /**
     * This method is used to deserialize the PropertySet from the specified byte array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return Return the length in byte of the PropertySet.
     */
    public int DoDeserializeFromByteArray(byte[] byteArray, int startIndex) {
        int index = startIndex;

        this.CProperties = BitConverter.toInt16(byteArray, startIndex);
        index += 2;
        this.RgPrids = new PropertyID[this.CProperties];
        for (int i = 0; i < this.CProperties; i++) {
            PropertyID propertyID = new PropertyID();
            propertyID.DoDeserializeFromByteArray(byteArray, index);
            this.RgPrids[i] = propertyID;
            index += 4;
        }
        this.RgData = new ArrayList<>();
        for (PropertyID propertyID : this.RgPrids) {
            IProperty property = null;
            switch (PropertyType.fromIntVal(propertyID.Type)) {
                case NoData:
                case Bool:
                case ObjectID:
                case ContextID:
                case ObjectSpaceID:
                    property = new NoData();
                    break;
                case ArrayOfObjectIDs:
                case ArrayOfObjectSpaceIDs:
                case ArrayOfContextIDs:
                    property = new ArrayNumber();
                    break;
                case OneByteOfData:
                    property = new OneByteOfData();
                    break;
                case TwoBytesOfData:
                    property = new TwoBytesOfData();
                    break;
                case FourBytesOfData:
                    property = new FourBytesOfData();
                    break;
                case EightBytesOfData:
                    property = new EightBytesOfData();
                    break;
                case FourBytesOfLengthFollowedByData:
                    property = new PrtFourBytesOfLengthFollowedByData();
                    break;
                case ArrayOfPropertyValues:
                    property = new PrtArrayOfPropertyValues();
                    break;
                case PropertySet:
                    property = new PropertySet();
                    break;
                default:
                    break;
            }
            if (property != null) {
                int len = property.DoDeserializeFromByteArray(byteArray, index);
                this.RgData.add(property);
                index += len;
            }
        }

        return index - startIndex;
    }
}
