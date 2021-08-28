package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.HashMap;
import java.util.Map;

/// <summary>
/// The enumeration of the stream object type header start
/// </summary>
public enum StreamObjectTypeHeaderStart {
    /// <summary>
    /// ErrorStringSupplementalInfo type in the ResponseError
    /// </summary>
    ErrorStringSupplementalInfo(0x4E),

    /// <summary>
    /// Data Element
    /// </summary>
    DataElement(0x01),

    /// <summary>
    /// Object Data BLOB
    /// </summary>
    ObjectDataBLOB(0x02),

    /// <summary>
    /// Waterline Knowledge Entry
    /// </summary>
    WaterlineKnowledgeEntry(0x04),

    /// <summary>
    /// Object Group Object BLOB Data Declaration
    /// </summary>
    ObjectGroupObjectBLOBDataDeclaration(0x05),

    /// <summary>
    /// Data Element Hash
    /// </summary>
    DataElementHash(0x06),

    /// <summary>
    /// Storage Manifest Root Declare
    /// </summary>
    StorageManifestRootDeclare(0x07),

    /// <summary>
    /// Revision Manifest Root Declare
    /// </summary>
    RevisionManifestRootDeclare(0x0A),

    /// <summary>
    /// Cell Manifest Current Revision
    /// </summary>
    CellManifestCurrentRevision(0x0B),

    /// <summary>
    /// Storage Manifest Schema GUID
    /// </summary>
    StorageManifestSchemaGUID(0x0C),

    /// <summary>
    /// Storage Index Revision Mapping
    /// </summary>
    StorageIndexRevisionMapping(0x0D),

    /// <summary>
    /// Storage Index Cell Mapping
    /// </summary>
    StorageIndexCellMapping(0x0E),

    /// <summary>
    /// Cell Knowledge Range
    /// </summary>
    CellKnowledgeRange(0x0F),

    /// <summary>
    /// The Knowledge
    /// </summary>
    Knowledge(0x10),

    /// <summary>
    /// Storage Index Manifest Mapping
    /// </summary>
    StorageIndexManifestMapping(0x11),

    /// <summary>
    /// Cell Knowledge
    /// </summary>
    CellKnowledge(0x14),

    /// <summary>
    /// Data Element Package
    /// </summary>
    DataElementPackage(0x15),

    /// <summary>
    /// Object Group Object Data
    /// </summary>
    ObjectGroupObjectData(0x16),

    /// <summary>
    /// Cell Knowledge Entry
    /// </summary>
    CellKnowledgeEntry(0x17),

    /// <summary>
    /// Object Group Object Declare
    /// </summary>
    ObjectGroupObjectDeclare(0x18),

    /// <summary>
    /// Revision Manifest Object Group References
    /// </summary>
    RevisionManifestObjectGroupReferences(0x19),

    /// <summary>
    /// Revision Manifest
    /// </summary>
    RevisionManifest(0x1A),

    /// <summary>
    /// Object Group Object Data BLOB Reference
    /// </summary>
    ObjectGroupObjectDataBLOBReference(0x1C),

    /// <summary>
    /// Object Group Declarations
    /// </summary>
    ObjectGroupDeclarations(0x1D),

    /// <summary>
    /// Object Group Data
    /// </summary>
    ObjectGroupData(0x1E),

    /// <summary>
    /// Intermediate Node Object
    /// </summary>
    LeafNodeObject(0x1F), // Defined in MS-FSSHTTPD

    /// <summary>
    /// Root Node Object
    /// </summary>
    IntermediateNodeObject(0x20), // Defined in MS-FSSHTTPD

    /// <summary>
    /// Signature Object
    /// </summary>
    SignatureObject(0x21), // Defined in MS-FSSHTTPD

    /// <summary>
    /// Data Size Object
    /// </summary>
    DataSizeObject(0x22), // Defined in MS-FSSHTTPD

    /// <summary>
    /// Data Hash Object
    /// </summary>
    DataHashObject(0x2F), // Defined in MS-FSSHTTPD

    /// <summary>
    /// Waterline Knowledge
    /// </summary>
    WaterlineKnowledge(0x29),

    /// <summary>
    /// Content Tag Knowledge
    /// </summary>
    ContentTagKnowledge(0x2D),

    /// <summary>
    /// Content Tag Knowledge Entry
    /// </summary>
    ContentTagKnowledgeEntry(0x2E),

    /// <summary>
    /// Query Changes Versioning
    /// </summary>
    QueryChangesVersioning(0x30),
    /// <summary>
    /// The Request
    /// </summary>
    Request(0x040),

    /// <summary>
    /// FSSHTTPB Sub Response
    /// </summary>
    FsshttpbSubResponse(0x041),

    /// <summary>
    /// Sub Request
    /// </summary>
    SubRequest(0x042),

    /// <summary>
    /// Read Access Response
    /// </summary>
    ReadAccessResponse(0x043),

    /// <summary>
    /// Specialized Knowledge
    /// </summary>
    SpecializedKnowledge(0x044),

    /// <summary>
    /// PutChanges Response SerialNumber ReassignAll
    /// </summary>
    PutChangesResponseSerialNumberReassignAll(0x045),

    /// <summary>
    /// Write Access Response
    /// </summary>
    WriteAccessResponse(0x046),

    /// <summary>
    /// Query Changes Filter
    /// </summary>
    QueryChangesFilter(0x047),

    /// <summary>
    /// Win32 Error
    /// </summary>
    Win32Error(0x049),

    /// <summary>
    /// Protocol Error
    /// </summary>
    ProtocolError(0x04B),

    /// <summary>
    /// Response Error
    /// </summary>
    ResponseError(0x04D),

    /// <summary>
    /// User Agent version
    /// </summary>
    UserAgentversion(0x04F),

    /// <summary>
    /// QueryChanges Filter Schema Specific
    /// </summary>
    QueryChangesFilterSchemaSpecific(0x050),

    /// <summary>
    /// QueryChanges Request
    /// </summary>
    QueryChangesRequest(0x051),

    /// <summary>
    /// HRESULT Error
    /// </summary>
    HRESULTError(0x052),

    /// <summary>
    /// PutChanges Response SerialNumberReassign
    /// </summary>
    PutChangesResponseSerialNumberReassign(0x053),

    /// <summary>
    /// QueryChanges Filter DataElement IDs
    /// </summary>
    QueryChangesFilterDataElementIDs(0x054),

    /// <summary>
    /// User Agent GUID
    /// </summary>
    UserAgentGUID(0x055),

    /// <summary>
    /// QueryChanges Filter Data Element Type
    /// </summary>
    QueryChangesFilterDataElementType(0x057),

    /// <summary>
    /// Query Changes Data Constraint
    /// </summary>
    QueryChangesDataConstraint(0x059),

    /// <summary>
    /// PutChanges Request
    /// </summary>
    PutChangesRequest(0x05A),

    /// <summary>
    /// Query Changes Request Arguments
    /// </summary>
    QueryChangesRequestArguments(0x05B),

    /// <summary>
    /// Query Changes Filter Cell ID
    /// </summary>
    QueryChangesFilterCellID(0x05C),

    /// <summary>
    /// User Agent
    /// </summary>
    UserAgent(0x05D),

    /// <summary>
    /// Query Changes Response
    /// </summary>
    QueryChangesResponse(0x05F),

    /// <summary>
    /// Query Changes Filter Hierarchy
    /// </summary>
    QueryChangesFilterHierarchy(0x060),

    /// <summary>
    /// The Response
    /// </summary>
    FsshttpbResponse(0x062),

    /// <summary>
    /// Query Data Element Request
    /// </summary>
    QueryDataElementRequest(0x065),

    /// <summary>
    /// Cell Error
    /// </summary>
    CellError(0x066),

    /// <summary>
    /// Query Changes Filter Flags
    /// </summary>
    QueryChangesFilterFlags(0x068),

    /// <summary>
    /// Data Element Fragment
    /// </summary>
    DataElementFragment(0x06A),

    /// <summary>
    /// Fragment Knowledge
    /// </summary>
    FragmentKnowledge(0x06B),

    /// <summary>
    /// Fragment Knowledge Entry
    /// </summary>
    FragmentKnowledgeEntry(0x06C),

    /// <summary>
    /// Object Group Metadata Declarations
    /// </summary>
    ObjectGroupMetadataDeclarations(0x79),

    /// <summary>
    /// Object Group Metadata
    /// </summary>
    ObjectGroupMetadata(0x78),

    /// <summary>
    /// Allocate Extended GUID Range Request
    /// </summary>
    AllocateExtendedGUIDRangeRequest(0x080),

    /// <summary>
    /// Allocate Extended GUID Range Response
    /// </summary>
    AllocateExtendedGUIDRangeResponse(0x081),

    /// <summary>
    /// Request Hash Options
    /// </summary>
    RequestHashOptions(0x088),

    /// <summary>
    /// Target Partition Id
    /// </summary>
    TargetPartitionId(0x083),

    /// <summary>
    /// Put Changes Response
    /// </summary>
    PutChangesResponse(0x087),

    /// <summary>
    /// Diagnostic Request Option Output
    /// </summary>
    DiagnosticRequestOptionOutput(0x089),

    /// <summary>
    /// Diagnostic Request Option Input
    /// </summary>
    DiagnosticRequestOptionInput(0x08A),

    /// <summary>
    /// Additional Flags
    /// </summary>
    AdditionalFlags(0x86),

    /// <summary>
    /// Put changes lock id
    /// </summary>
    PutChangesLockId(0x85),

    /// <summary>
    /// Version Token Knowledge
    /// </summary>
    VersionTokenKnowledge(0x8C),

    /// <summary>
    /// Cell Roundtrip Options
    /// </summary>
    CellRoundtripOptions(0x8D),

    /// <summary>
    /// File Hash
    /// </summary>
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