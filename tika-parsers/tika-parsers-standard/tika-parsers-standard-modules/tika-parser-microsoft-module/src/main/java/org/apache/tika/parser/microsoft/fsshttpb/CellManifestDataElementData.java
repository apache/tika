package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/// <summary>
/// Cell manifest data element
/// </summary>
public class CellManifestDataElementData extends DataElementData {
    /// <summary>
    /// Initializes a new instance of the CellManifestDataElementData class.
    /// </summary>
    public CellManifestDataElementData() {
        this.cellManifestCurrentRevision = new CellManifestCurrentRevision();
    }

    /// <summary>
    /// Gets or sets a Cell Manifest Current Revision.
    /// </summary>
    public CellManifestCurrentRevision cellManifestCurrentRevision;

    /// <summary>
    /// Used to return the length of this element.
    /// </summary>
    /// <param name="byteArray">A Byte array</param>
    /// <param name="startIndex">Start position</param>
    /// <returns>The element length</returns>
    @Override
    public int DeserializeDataElementDataFromByteArray(byte[] byteArray, int startIndex) {
        AtomicInteger index = new AtomicInteger(startIndex);
        this.cellManifestCurrentRevision = StreamObject.GetCurrent(byteArray, index, CellManifestCurrentRevision.class);
        return index.get() - startIndex;
    }

    /// <summary>
    /// Used to convert the element into a byte List.
    /// </summary>
    /// <returns>The Byte list</returns>
    @Override
    public List<Byte> SerializeToByteList() {
        return this.cellManifestCurrentRevision.SerializeToByteList();
    }
}
