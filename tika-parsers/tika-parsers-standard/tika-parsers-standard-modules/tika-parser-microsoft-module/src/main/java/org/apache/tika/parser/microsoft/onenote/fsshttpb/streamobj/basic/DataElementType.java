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

public enum DataElementType {

    None(0),

    StorageIndexDataElementData(1),

    StorageManifestDataElementData(2),

    CellManifestDataElementData(3),

    RevisionManifestDataElementData(4),

    ObjectGroupDataElementData(5),

    FragmentDataElementData(6),

    ObjectDataBLOBDataElementData(10);


    static final Map<Integer, DataElementType> valToEnumMap = new HashMap<>();

    static {
        for (DataElementType val : values()) {
            valToEnumMap.put(val.getIntVal(), val);
        }
    }

    private final int intVal;

    DataElementType(int intVal) {
        this.intVal = intVal;
    }

    public static DataElementType fromIntVal(int intVal) {
        return valToEnumMap.get(intVal);
    }

    public int getIntVal() {
        return intVal;
    }
}
