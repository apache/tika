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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.ArrayNumber;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.FixedBytesOfData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.IProperty;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.NoData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.PrtArrayOfPropertyValues;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.PrtFourBytesOfLengthFollowedByData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.PropertyID;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.PropertyType;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitConverter;

public class PropertySet implements IProperty {
    // a nesting level costs ~14 file bytes, so unbounded depth lets a small
    // crafted file drive a StackOverflowError that escapes catch(Exception)
    public static final int MAX_PROPERTY_NESTING = 100;

    public int cProperties;

    public PropertyID[] rgPrids;
    public List<IProperty> rgData;

    public int doDeserializeFromByteArray(byte[] byteArray, int startIndex) throws IOException {
        return doDeserializeFromByteArray(byteArray, startIndex, 0);
    }

    public int doDeserializeFromByteArray(byte[] byteArray, int startIndex, int depth)
            throws IOException {
        if (depth > MAX_PROPERTY_NESTING) {
            throw new IOException("PropertySet nesting exceeds " + MAX_PROPERTY_NESTING);
        }
        int index = startIndex;

        this.cProperties = BitConverter.toInt16(byteArray, startIndex) & 0xFFFF;
        index += 2;
        if ((long) this.cProperties * 4 > byteArray.length - index) {
            throw new IOException("PropertySet count " + this.cProperties + " exceeds the " +
                    (byteArray.length - index) + " bytes remaining");
        }
        this.rgPrids = new PropertyID[this.cProperties];
        for (int i = 0; i < this.cProperties; i++) {
            PropertyID propertyID = new PropertyID();
            propertyID.doDeserializeFromByteArray(byteArray, index);
            this.rgPrids[i] = propertyID;
            index += 4;
        }
        this.rgData = new ArrayList<>();
        for (PropertyID propertyID : this.rgPrids) {
            IProperty property = null;
            PropertyType propertyType = PropertyType.fromIntVal(propertyID.type);
            if (propertyType == null) {
                // the length of an undefined type is unknown, so nothing after it can be read
                throw new IOException("Undefined property type " + propertyID.type);
            }
            switch (propertyType) {
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
                    property = new FixedBytesOfData(1);
                    break;
                case TwoBytesOfData:
                    property = new FixedBytesOfData(2);
                    break;
                case FourBytesOfData:
                    property = new FixedBytesOfData(4);
                    break;
                case EightBytesOfData:
                    property = new FixedBytesOfData(8);
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
                int len;
                if (property instanceof PropertySet) {
                    len = ((PropertySet) property)
                            .doDeserializeFromByteArray(byteArray, index, depth + 1);
                } else if (property instanceof PrtArrayOfPropertyValues) {
                    len = ((PrtArrayOfPropertyValues) property)
                            .doDeserializeFromByteArray(byteArray, index, depth + 1);
                } else {
                    len = property.doDeserializeFromByteArray(byteArray, index);
                }
                this.rgData.add(property);
                index += len;
            }
        }

        return index - startIndex;
    }
}
