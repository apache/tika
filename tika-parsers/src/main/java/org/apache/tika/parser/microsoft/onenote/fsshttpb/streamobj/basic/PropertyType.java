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

import java.util.HashMap;
import java.util.Map;

public enum PropertyType {

    /**
     * The property contains no data.
     */
    NoData(0x1),
    /**
     * The property is a Boolean value specified by boolValue.
     */
    Bool(0x2),
    /**
     * The property contains 1 byte of data in the PropertySet.rgData stream field.
     */
    OneByteOfData(0x3),
    /**
     * The property contains 2 bytes of data in the PropertySet.rgData stream field.
     */
    TwoBytesOfData(0x4),
    /**
     * The property contains 4 bytes of data in the PropertySet.rgData stream field.
     */
    FourBytesOfData(0x5),
    /**
     * The property contains 8 bytes of data in the PropertySet.rgData stream field.
     */
    EightBytesOfData(0x6),
    /**
     * The property contains a prtFourBytesOfLengthFollowedByData in the PropertySet.rgData stream field.
     */
    FourBytesOfLengthFollowedByData(0x7),
    /**
     * The property contains one CompactID in the ObjectSpaceObjectPropSet.OIDs.body stream field.
     */
    ObjectID(0x8),
    /**
     * The property contains an array of CompactID structures in the ObjectSpaceObjectPropSet.OSIDs.body stream field.
     */
    ArrayOfObjectIDs(0x9),
    /**
     * The property contains one CompactID structure in the ObjectSpaceObjectPropSet.OSIDs.body stream field.
     */
    ObjectSpaceID(0xA),
    /**
     * The property contains an array of CompactID structures in the ObjectSpaceObjectPropSet.OSIDs.body stream field.
     */
    ArrayOfObjectSpaceIDs(0xB),
    /**
     * The property contains one CompactID in the ObjectSpaceObjectPropSet.ContextIDs.body stream field.
     */
    ContextID(0xC),
    /**
     * The property contains an array of CompactID structures in the ObjectSpaceObjectPropSet.ContextIDs.body
     * stream field.
     */
    ArrayOfContextIDs(0xD),
    /**
     * The property contains a prtArrayOfPropertyValues structure in the PropertySet.rgData stream field.
     */
    ArrayOfPropertyValues(0x10),
    /**
     * The property contains a child PropertySet structure in the PropertySet.rgData stream field
     * of the parent PropertySet.
     */
    PropertySet(0x11);

    static final Map<Integer, PropertyType> valToEnumMap = new HashMap<>();

    static {
        for (PropertyType val : values()) {
            valToEnumMap.put(val.getIntVal(), val);
        }
    }

    private final int intVal;

    PropertyType(int intVal) {
        this.intVal = intVal;
    }

    public static PropertyType fromIntVal(int intVal) {
        return valToEnumMap.get(intVal);
    }

    public int getIntVal() {
        return intVal;
    }

}
