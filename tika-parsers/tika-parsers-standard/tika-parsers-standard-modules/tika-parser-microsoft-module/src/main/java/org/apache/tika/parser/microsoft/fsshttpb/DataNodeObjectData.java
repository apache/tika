package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.Arrays;
import java.util.UUID;

/// <summary>
/// Data Node Object data.
/// </summary>
public class DataNodeObjectData {
    /// <summary>
    /// Initializes a new instance of the DataNodeObjectData class.
    /// </summary>
    /// <param name="byteArray">A Byte array</param>
    /// <param name="startIndex">Start position</param>
    /// <param name="length">The element length</param>
    public DataNodeObjectData(byte[] byteArray, int startIndex, int length) {
        this();
        this.ObjectData = Arrays.copyOfRange(byteArray, startIndex, length);

    }

    /// <summary>
    /// Initializes a new instance of the DataNodeObjectData class.
    /// </summary>
    DataNodeObjectData() {
        this.ExGuid = new ExGuid(SequenceNumberGenerator.GetCurrentSerialNumber(), UUID.randomUUID());
    }

    /// <summary>
    /// Gets or sets the extended GUID of the data node object.
    /// </summary>
    public ExGuid ExGuid;

    /// <summary>
    /// Gets or sets the Data field for the Intermediate Node Object.
    /// </summary>
    public byte[] ObjectData;
}