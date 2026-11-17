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

public enum StreamObjectTypeHeaderStart {
    /** Used in ResponseError. */
    ErrorStringSupplementalInfo(0x4E),

    DataElement(0x01),

    ObjectDataBLOB(0x02),

    WaterlineKnowledgeEntry(0x04),

    ObjectGroupObjectBLOBDataDeclaration(0x05),

    DataElementHash(0x06),

    StorageManifestRootDeclare(0x07),

    RevisionManifestRootDeclare(0x0A),

    CellManifestCurrentRevision(0x0B),

    StorageManifestSchemaGUID(0x0C),

    StorageIndexRevisionMapping(0x0D),

    StorageIndexCellMapping(0x0E),

    CellKnowledgeRange(0x0F),

    Knowledge(0x10),

    StorageIndexManifestMapping(0x11),

    CellKnowledge(0x14),

    DataElementPackage(0x15),

    ObjectGroupObjectData(0x16),

    CellKnowledgeEntry(0x17),

    ObjectGroupObjectDeclare(0x18),

    RevisionManifestObjectGroupReferences(0x19),

    RevisionManifest(0x1A),

    ObjectGroupObjectDataBLOBReference(0x1C),

    ObjectGroupDeclarations(0x1D),

    ObjectGroupData(0x1E),

    /**
     * Intermediate Node Object
     */
    LeafNodeObject(0x1F), // Defined in MS-FSSHTTPD

    /**
     * Root Node Object
     */
    IntermediateNodeObject(0x20), // Defined in MS-FSSHTTPD

    SignatureObject(0x21), // Defined in MS-FSSHTTPD

    DataSizeObject(0x22), // Defined in MS-FSSHTTPD

    DataHashObject(0x2F), // Defined in MS-FSSHTTPD

    WaterlineKnowledge(0x29),

    ContentTagKnowledge(0x2D),

    ContentTagKnowledgeEntry(0x2E),

    QueryChangesVersioning(0x30),
    Request(0x040),

    FsshttpbSubResponse(0x041),

    SubRequest(0x042),

    ReadAccessResponse(0x043),

    SpecializedKnowledge(0x044),

    PutChangesResponseSerialNumberReassignAll(0x045),

    WriteAccessResponse(0x046),

    QueryChangesFilter(0x047),

    Win32Error(0x049),

    ProtocolError(0x04B),

    ResponseError(0x04D),

    UserAgentversion(0x04F),

    QueryChangesFilterSchemaSpecific(0x050),

    QueryChangesRequest(0x051),

    HRESULTError(0x052),

    PutChangesResponseSerialNumberReassign(0x053),

    QueryChangesFilterDataElementIDs(0x054),

    UserAgentGUID(0x055),

    QueryChangesFilterDataElementType(0x057),

    QueryChangesDataConstraint(0x059),

    PutChangesRequest(0x05A),

    QueryChangesRequestArguments(0x05B),

    QueryChangesFilterCellID(0x05C),

    UserAgent(0x05D),

    QueryChangesResponse(0x05F),

    QueryChangesFilterHierarchy(0x060),

    FsshttpbResponse(0x062),

    QueryDataElementRequest(0x065),

    CellError(0x066),

    QueryChangesFilterFlags(0x068),

    DataElementFragment(0x06A),

    FragmentKnowledge(0x06B),

    FragmentKnowledgeEntry(0x06C),

    ObjectGroupMetadataDeclarations(0x79),

    ObjectGroupMetadata(0x78),

    AlternativePackaging(0x7A),

    AllocateExtendedGUIDRangeRequest(0x080),

    AllocateExtendedGUIDRangeResponse(0x081),

    RequestHashOptions(0x088),

    TargetPartitionId(0x083),

    PutChangesResponse(0x087),

    DiagnosticRequestOptionOutput(0x089),

    DiagnosticRequestOptionInput(0x08A),

    AdditionalFlags(0x86),

    PutChangesLockId(0x85),

    VersionTokenKnowledge(0x8C),

    CellRoundtripOptions(0x8D),

    FileHash(0x8E);

    static final Map<Integer, StreamObjectTypeHeaderStart> valToEnumMap = new HashMap<>();

    static {
        for (StreamObjectTypeHeaderStart streamObjectTypeHeaderStart : values()) {
            valToEnumMap.put(streamObjectTypeHeaderStart.getIntVal(), streamObjectTypeHeaderStart);
        }
    }

    private final int intVal;

    StreamObjectTypeHeaderStart(int intVal) {
        this.intVal = intVal;
    }

    public static StreamObjectTypeHeaderStart fromIntVal(int intVal) {
        return valToEnumMap.get(intVal);
    }

    public int getIntVal() {
        return intVal;
    }
}
