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
package org.apache.tika.parser.microsoft.fsshttpb.streamobj;

import java.util.HashMap;
import java.util.Map;

public enum StreamObjectTypeHeaderEnd {
    /**
     * Data Element
     */
    DataElement(0x01),

    /**
     * The Knowledge
     */
    Knowledge(0x10),

    /**
     * Cell Knowledge
     */
    CellKnowledge(0x14),

    /**
     * Data Element Package
     */
    DataElementPackage(0x15),

    /**
     * Object Group Declarations
     */
    ObjectGroupDeclarations(0x1D),

    /**
     * Object Group Data
     */
    ObjectGroupData(0x1E),

    /**
     * Intermediate Node End
     */
    IntermediateNodeEnd(0x1F), // Defined in MS-FSSHTTPD

    /**
     * Root Node End
     */
    RootNodeEnd(0x20), // Defined in MS-FSSHTTPD

    /**
     * Waterline Knowledge
     */
    WaterlineKnowledge(0x29),

    /**
     * Content Tag Knowledge
     */
    ContentTagKnowledge(0x2D),

    /**
     * The Request
     */
    Request(0x040),

    /**
     * Sub Response
     */
    SubResponse(0x041),

    /**
     * Sub Request
     */
    SubRequest(0x042),

    /**
     * Read Access Response
     */
    ReadAccessResponse(0x043),

    /**
     * Specialized Knowledge
     */
    SpecializedKnowledge(0x044),

    /**
     * Write Access Response
     */
    WriteAccessResponse(0x046),

    /**
     * Query Changes Filter
     */
    QueryChangesFilter(0x047),

    /**
     * The Error type
     */
    Error(0x04D),

    /**
     * Query Changes Request
     */
    QueryChangesRequest(0x051),

    /**
     * User Agent
     */
    UserAgent(0x05D),

    /**
     * The Response
     */
    Response(0x062),

    /**
     * Fragment Knowledge
     */
    FragmentKnowledge(0x06B),

    /**
     * Object Group Metadata Declarations, new added in MOSS2013.
     */
    ObjectGroupMetadataDeclarations(0x79),

    /**
     * Alternative Packaging
     */
    AlternativePackaging(0x7A),

    /**
     * Target PartitionId, new added in MOSS2013.
     */
    TargetPartitionId(0x083),

    /**
     * User Agent Client and Platform
     */
    UserAgentClientandPlatform(0x8B);

    private final int intVal;

    static final Map<Integer, StreamObjectTypeHeaderEnd> valToEnumMap = new HashMap<>();

    static {
        for (StreamObjectTypeHeaderEnd val : values()) {
            valToEnumMap.put(val.getIntVal(), val);
        }
    }

    StreamObjectTypeHeaderEnd(int intVal) {
        this.intVal = intVal;
    }

    public int getIntVal() {
        return intVal;
    }

    public static StreamObjectTypeHeaderEnd fromIntVal(int intVal) {
        return valToEnumMap.get(intVal);
    }
}