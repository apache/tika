package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.HashMap;
import java.util.Map;

public enum StreamObjectTypeHeaderEnd {
    /// <summary>
    /// Data Element
    /// </summary>
    DataElement(0x01),

    /// <summary>
    /// The Knowledge
    /// </summary>
    Knowledge(0x10),

    /// <summary>
    /// Cell Knowledge
    /// </summary>
    CellKnowledge(0x14),

    /// <summary>
    /// Data Element Package
    /// </summary>
    DataElementPackage(0x15),

    /// <summary>
    /// Object Group Declarations
    /// </summary>
    ObjectGroupDeclarations(0x1D),

    /// <summary>
    /// Object Group Data
    /// </summary>
    ObjectGroupData(0x1E),

    /// <summary>
    /// Intermediate Node End
    /// </summary>
    IntermediateNodeEnd(0x1F), // Defined in MS-FSSHTTPD

    /// <summary>
    /// Root Node End
    /// </summary>
    RootNodeEnd(0x20), // Defined in MS-FSSHTTPD

    /// <summary>
    /// Waterline Knowledge
    /// </summary>
    WaterlineKnowledge(0x29),

    /// <summary>
    /// Content Tag Knowledge
    /// </summary>
    ContentTagKnowledge(0x2D),

    /// <summary>
    /// The Request
    /// </summary>
    Request(0x040),

    /// <summary>
    /// Sub Response
    /// </summary>
    SubResponse(0x041),

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
    /// Write Access Response
    /// </summary>
    WriteAccessResponse(0x046),

    /// <summary>
    /// Query Changes Filter
    /// </summary>
    QueryChangesFilter(0x047),

    /// <summary>
    /// The Error type
    /// </summary>
    Error(0x04D),

    /// <summary>
    /// Query Changes Request
    /// </summary>
    QueryChangesRequest(0x051),

    /// <summary>
    /// User Agent
    /// </summary>
    UserAgent(0x05D),

    /// <summary>
    /// The Response
    /// </summary>
    Response(0x062),

    /// <summary>
    /// Fragment Knowledge
    /// </summary>
    FragmentKnowledge(0x06B),

    /// <summary>
    /// Object Group Metadata Declarations, new added in MOSS2013.
    /// </summary>
    ObjectGroupMetadataDeclarations(0x79),

    /// <summary>
    /// Target PartitionId, new added in MOSS2013.
    /// </summary>
    TargetPartitionId(0x083),

    /// <summary>
    /// User Agent Client and Platform
    /// </summary>
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