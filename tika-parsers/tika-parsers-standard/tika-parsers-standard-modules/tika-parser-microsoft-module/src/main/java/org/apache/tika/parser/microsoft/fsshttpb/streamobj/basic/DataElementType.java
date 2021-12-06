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
package org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic;

import java.util.HashMap;
import java.util.Map;

/**
 * The enumeration of the data element type
 */
public enum DataElementType {

    /**
     * None data element type
     */
    None(0),

    /**
     * Storage Index Data Element
     */
    StorageIndexDataElementData(1),

    /**
     * Storage Manifest Data Element
     */
    StorageManifestDataElementData(2),

    /**
     * Cell Manifest Data Element
     */
    CellManifestDataElementData(3),

    /**
     * Revision Manifest Data Element
     */
    RevisionManifestDataElementData(4),

    /**
     * Object Group Data Element
     */
    ObjectGroupDataElementData(5),

    /**
     * Fragment Data Element
     */
    FragmentDataElementData(6),

    /**
     * Object Data BLOB Data Element
     */
    ObjectDataBLOBDataElementData(10);


    private final int intVal;

    static final Map<Integer, DataElementType> valToEnumMap = new HashMap<>();

    static {
        for (DataElementType val : values()) {
            valToEnumMap.put(val.getIntVal(), val);
        }
    }

    DataElementType(int intVal) {
        this.intVal = intVal;
    }

    public int getIntVal() {
        return intVal;
    }

    public static DataElementType fromIntVal(int intVal) {
        return valToEnumMap.get(intVal);
    }
}
