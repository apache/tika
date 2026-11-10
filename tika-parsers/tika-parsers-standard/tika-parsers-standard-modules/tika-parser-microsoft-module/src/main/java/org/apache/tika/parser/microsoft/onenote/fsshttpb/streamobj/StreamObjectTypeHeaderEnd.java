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

import java.util.HashMap;
import java.util.Map;

public enum StreamObjectTypeHeaderEnd {
    DataElement(0x01),

    Knowledge(0x10),

    CellKnowledge(0x14),

    DataElementPackage(0x15),

    ObjectGroupDeclarations(0x1D),

    ObjectGroupData(0x1E),

    IntermediateNodeEnd(0x1F), // Defined in MS-FSSHTTPD

    RootNodeEnd(0x20), // Defined in MS-FSSHTTPD

    WaterlineKnowledge(0x29),

    ContentTagKnowledge(0x2D),

    Request(0x040),

    SubResponse(0x041),

    SubRequest(0x042),

    ReadAccessResponse(0x043),

    SpecializedKnowledge(0x044),

    WriteAccessResponse(0x046),

    QueryChangesFilter(0x047),

    Error(0x04D),

    QueryChangesRequest(0x051),

    UserAgent(0x05D),

    Response(0x062),

    FragmentKnowledge(0x06B),

    /** Added in MOSS2013. */
    ObjectGroupMetadataDeclarations(0x79),

    AlternativePackaging(0x7A),

    /** Added in MOSS2013. */
    TargetPartitionId(0x083),

    UserAgentClientandPlatform(0x8B);

    static final Map<Integer, StreamObjectTypeHeaderEnd> valToEnumMap = new HashMap<>();

    static {
        for (StreamObjectTypeHeaderEnd val : values()) {
            valToEnumMap.put(val.getIntVal(), val);
        }
    }

    private final int intVal;

    StreamObjectTypeHeaderEnd(int intVal) {
        this.intVal = intVal;
    }

    public static StreamObjectTypeHeaderEnd fromIntVal(int intVal) {
        return valToEnumMap.get(intVal);
    }

    public int getIntVal() {
        return intVal;
    }
}
