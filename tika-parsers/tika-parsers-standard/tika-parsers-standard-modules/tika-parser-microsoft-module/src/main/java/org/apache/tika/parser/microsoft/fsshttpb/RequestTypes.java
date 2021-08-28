package org.apache.tika.parser.microsoft.fsshttpb;

/**
 * The enumeration of request type.
 */
public enum RequestTypes {
    /**
     * Query access.
     */
    QueryAccess(1),

    /**
     * Query changes.
     */
    QueryChanges(2),

    /**
     * Query knowledge.
     */
    QueryKnowledge(3),

    /**
     * Put changes.
     */
    PutChanges(5),

    /**
     * Query raw storage.
     */
    QueryRawStorage(6),

    /**
     * Put raw storage.
     */
    PutRawStorage(7),

    /**
     * Query diagnostic store info.
     */
    QueryDiagnosticStoreInfo(8),

    /**
     * Allocate extended Guid range .
     */
    AllocateExtendedGuidRange(11);

    private final int intVal;

    RequestTypes(int intVal) {
        this.intVal = intVal;
    }

    public int getIntVal() {
        return intVal;
    }
}
    