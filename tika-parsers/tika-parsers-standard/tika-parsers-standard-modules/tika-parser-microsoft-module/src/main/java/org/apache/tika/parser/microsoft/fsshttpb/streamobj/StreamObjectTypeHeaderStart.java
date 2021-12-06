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

/**
 * The enumeration of the stream object type header start
 */
public enum StreamObjectTypeHeaderStart {
    /**
     * ErrorStringSupplementalInfo type in the ResponseError
     */
    ErrorStringSupplementalInfo(0x4E),

    /**
     * Data Element
     */
    DataElement(0x01),

    /**
     * Object Data BLOB
     */
    ObjectDataBLOB(0x02),

    /**
     * Waterline Knowledge Entry
     */
    WaterlineKnowledgeEntry(0x04),

    /**
     * Object Group Object BLOB Data Declaration
     */
    ObjectGroupObjectBLOBDataDeclaration(0x05),

    /**
     * Data Element Hash
     */
    DataElementHash(0x06),

    /**
     * Storage Manifest Root Declare
     */
    StorageManifestRootDeclare(0x07),

    /**
     * Revision Manifest Root Declare
     */
    RevisionManifestRootDeclare(0x0A),

    /**
     * Cell Manifest Current Revision
     */
    CellManifestCurrentRevision(0x0B),

    /**
     * Storage Manifest Schema GUID
     */
    StorageManifestSchemaGUID(0x0C),

    /**
     * Storage Index Revision Mapping
     */
    StorageIndexRevisionMapping(0x0D),

    /**
     * Storage Index Cell Mapping
     */
    StorageIndexCellMapping(0x0E),

    /**
     * Cell Knowledge Range
     */
    CellKnowledgeRange(0x0F),

    /**
     * The Knowledge
     */
    Knowledge(0x10),

    /**
     * Storage Index Manifest Mapping
     */
    StorageIndexManifestMapping(0x11),

    /**
     * Cell Knowledge
     */
    CellKnowledge(0x14),

    /**
     * Data Element Package
     */
    DataElementPackage(0x15),

    /**
     * Object Group Object Data
     */
    ObjectGroupObjectData(0x16),

    /**
     * Cell Knowledge Entry
     */
    CellKnowledgeEntry(0x17),

    /**
     * Object Group Object Declare
     */
    ObjectGroupObjectDeclare(0x18),

    /**
     * Revision Manifest Object Group References
     */
    RevisionManifestObjectGroupReferences(0x19),

    /**
     * Revision Manifest
     */
    RevisionManifest(0x1A),

    /**
     * Object Group Object Data BLOB Reference
     */
    ObjectGroupObjectDataBLOBReference(0x1C),

    /**
     * Object Group Declarations
     */
    ObjectGroupDeclarations(0x1D),

    /**
     * Object Group Data
     */
    ObjectGroupData(0x1E),

    /**
     * Intermediate Node Object
     */
    LeafNodeObject(0x1F), // Defined in MS-FSSHTTPD

    /**
     * Root Node Object
     */
    IntermediateNodeObject(0x20), // Defined in MS-FSSHTTPD

    /**
     * Signature Object
     */
    SignatureObject(0x21), // Defined in MS-FSSHTTPD

    /**
     * Data Size Object
     */
    DataSizeObject(0x22), // Defined in MS-FSSHTTPD

    /**
     * Data Hash Object
     */
    DataHashObject(0x2F), // Defined in MS-FSSHTTPD

    /**
     * Waterline Knowledge
     */
    WaterlineKnowledge(0x29),

    /**
     * Content Tag Knowledge
     */
    ContentTagKnowledge(0x2D),

    /**
     * Content Tag Knowledge Entry
     */
    ContentTagKnowledgeEntry(0x2E),

    /**
     * Query Changes Versioning
     */
    QueryChangesVersioning(0x30),
    /**
     * The Request
     */
    Request(0x040),

    /**
     * FSSHTTPB Sub Response
     */
    FsshttpbSubResponse(0x041),

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
     * PutChanges Response SerialNumber ReassignAll
     */
    PutChangesResponseSerialNumberReassignAll(0x045),

    /**
     * Write Access Response
     */
    WriteAccessResponse(0x046),

    /**
     * Query Changes Filter
     */
    QueryChangesFilter(0x047),

    /**
     * Win32 Error
     */
    Win32Error(0x049),

    /**
     * Protocol Error
     */
    ProtocolError(0x04B),

    /**
     * Response Error
     */
    ResponseError(0x04D),

    /**
     * User Agent version
     */
    UserAgentversion(0x04F),

    /**
     * QueryChanges Filter Schema Specific
     */
    QueryChangesFilterSchemaSpecific(0x050),

    /**
     * QueryChanges Request
     */
    QueryChangesRequest(0x051),

    /**
     * HRESULT Error
     */
    HRESULTError(0x052),

    /**
     * PutChanges Response SerialNumberReassign
     */
    PutChangesResponseSerialNumberReassign(0x053),

    /**
     * QueryChanges Filter DataElement IDs
     */
    QueryChangesFilterDataElementIDs(0x054),

    /**
     * User Agent GUID
     */
    UserAgentGUID(0x055),

    /**
     * QueryChanges Filter Data Element Type
     */
    QueryChangesFilterDataElementType(0x057),

    /**
     * Query Changes Data Constraint
     */
    QueryChangesDataConstraint(0x059),

    /**
     * PutChanges Request
     */
    PutChangesRequest(0x05A),

    /**
     * Query Changes Request Arguments
     */
    QueryChangesRequestArguments(0x05B),

    /**
     * Query Changes Filter Cell ID
     */
    QueryChangesFilterCellID(0x05C),

    /**
     * User Agent
     */
    UserAgent(0x05D),

    /**
     * Query Changes Response
     */
    QueryChangesResponse(0x05F),

    /**
     * Query Changes Filter Hierarchy
     */
    QueryChangesFilterHierarchy(0x060),

    /**
     * The Response
     */
    FsshttpbResponse(0x062),

    /**
     * Query Data Element Request
     */
    QueryDataElementRequest(0x065),

    /**
     * Cell Error
     */
    CellError(0x066),

    /**
     * Query Changes Filter Flags
     */
    QueryChangesFilterFlags(0x068),

    /**
     * Data Element Fragment
     */
    DataElementFragment(0x06A),

    /**
     * Fragment Knowledge
     */
    FragmentKnowledge(0x06B),

    /**
     * Fragment Knowledge Entry
     */
    FragmentKnowledgeEntry(0x06C),

    /**
     * Object Group Metadata Declarations
     */
    ObjectGroupMetadataDeclarations(0x79),

    /**
     * Object Group Metadata
     */
    ObjectGroupMetadata(0x78),

    /**
     * Alternative Packaging
     */
    AlternativePackaging(0x7A),

    /**
     * Allocate Extended GUID Range Request
     */
    AllocateExtendedGUIDRangeRequest(0x080),

    /**
     * Allocate Extended GUID Range Response
     */
    AllocateExtendedGUIDRangeResponse(0x081),

    /**
     * Request Hash Options
     */
    RequestHashOptions(0x088),

    /**
     * Target Partition Id
     */
    TargetPartitionId(0x083),

    /**
     * Put Changes Response
     */
    PutChangesResponse(0x087),

    /**
     * Diagnostic Request Option Output
     */
    DiagnosticRequestOptionOutput(0x089),

    /**
     * Diagnostic Request Option Input
     */
    DiagnosticRequestOptionInput(0x08A),

    /**
     * Additional Flags
     */
    AdditionalFlags(0x86),

    /**
     * Put changes lock id
     */
    PutChangesLockId(0x85),

    /**
     * Version Token Knowledge
     */
    VersionTokenKnowledge(0x8C),

    /**
     * Cell Roundtrip Options
     */
    CellRoundtripOptions(0x8D),

    /**
     * File Hash
     */
    FileHash(0x8E);

    private final int intVal;

    static final Map<Integer, StreamObjectTypeHeaderStart> valToEnumMap = new HashMap<>();

    static {
        for (StreamObjectTypeHeaderStart streamObjectTypeHeaderStart : values()) {
            valToEnumMap.put(streamObjectTypeHeaderStart.getIntVal(), streamObjectTypeHeaderStart);
        }
    }

    StreamObjectTypeHeaderStart(int intVal) {
        this.intVal = intVal;
    }

    public int getIntVal() {
        return intVal;
    }

    public static StreamObjectTypeHeaderStart fromIntVal(int intVal) {
        return valToEnumMap.get(intVal);
    }
}