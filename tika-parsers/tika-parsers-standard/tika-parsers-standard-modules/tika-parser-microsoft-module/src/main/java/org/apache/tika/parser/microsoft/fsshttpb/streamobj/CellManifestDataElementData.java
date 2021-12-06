package org.apache.tika.parser.microsoft.fsshttpb.streamobj;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cell manifest data element
 */
public class CellManifestDataElementData extends DataElementData {
    /**
     * Initializes a new instance of the CellManifestDataElementData class.
     */
    public CellManifestDataElementData() {
        this.cellManifestCurrentRevision = new CellManifestCurrentRevision();
    }

    /**
     * Gets or sets a Cell Manifest Current Revision.
     */
    public CellManifestCurrentRevision cellManifestCurrentRevision;

    /**
     * Used to return the length of this element.
     *
     * @param byteArray  A Byte array
     * @param startIndex Start position
     * @return The element length
     */
    @Override
    public int DeserializeDataElementDataFromByteArray(byte[] byteArray, int startIndex) {
        AtomicInteger index = new AtomicInteger(startIndex);
        this.cellManifestCurrentRevision = StreamObject.GetCurrent(byteArray, index, CellManifestCurrentRevision.class);
        return index.get() - startIndex;
    }

    /**
     * Used to convert the element into a byte List.
     *
     * @return The Byte list
     */
    @Override
    public List<Byte> SerializeToByteList() {
        return this.cellManifestCurrentRevision.SerializeToByteList();
    }
}
