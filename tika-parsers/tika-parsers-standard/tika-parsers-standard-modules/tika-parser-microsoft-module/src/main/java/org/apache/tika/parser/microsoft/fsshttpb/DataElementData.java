package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;

/// <summary>
/// Base class of data element.
/// </summary>
public abstract class DataElementData implements IFSSHTTPBSerializable {
    /// <summary>
    /// De-serialize data element data from byte array.
    /// </summary>
    /// <param name="byteArray">The byte array.</param>
    /// <param name="startIndex">The position where to start.</param>
    /// <returns>The length of the item.</returns>
    public abstract int DeserializeDataElementDataFromByteArray(byte[] byteArray, int startIndex);

    /// <summary>
    /// Serialize item to byte list.
    /// </summary>
    /// <returns>The byte list.</returns>
    public abstract List<Byte> SerializeToByteList();
}