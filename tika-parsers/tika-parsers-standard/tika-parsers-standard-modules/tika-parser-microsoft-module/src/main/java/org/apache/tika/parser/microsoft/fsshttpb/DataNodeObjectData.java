package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.Arrays;
import java.util.UUID;

/**
 * Data Node Object data
 */
public class DataNodeObjectData {
    /**
     * Initializes a new instance of the DataNodeObjectData class.
     *
     * @param byteArray  A Byte array
     * @param startIndex Start position
     * @param length     The element length
     */
    public DataNodeObjectData(byte[] byteArray, int startIndex, int length) {
        this();
        this.ObjectData = Arrays.copyOfRange(byteArray, startIndex, length);

    }

    /**
     * Initializes a new instance of the DataNodeObjectData class.
     */
    DataNodeObjectData() {
        this.ExGuid = new ExGuid(SequenceNumberGenerator.GetCurrentSerialNumber(), UUID.randomUUID());
    }

    /**
     * Gets or sets the extended GUID of the data node object.
     */
    public ExGuid ExGuid;

    /**
     * Gets or sets the Data field for the Intermediate Node Object.
     */
    public byte[] ObjectData;
}