package org.apache.tika.parser.microsoft.fsshttpb;

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
