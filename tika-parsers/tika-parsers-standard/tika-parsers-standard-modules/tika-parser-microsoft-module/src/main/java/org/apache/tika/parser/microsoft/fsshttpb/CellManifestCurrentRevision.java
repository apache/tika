package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class CellManifestCurrentRevision extends StreamObject {
    /// <summary>
    /// Initializes a new instance of the CellManifestCurrentRevision class.
    /// </summary>
    public CellManifestCurrentRevision() {
        super(StreamObjectTypeHeaderStart.CellManifestCurrentRevision);
    }

    /// <summary>
    /// Gets or sets a 16-bit stream object header that specifies a cell manifest current revision.
    /// </summary>
    public ExGuid cellManifestCurrentRevisionExGuid;

    /// <summary>
    /// Used to de-serialize the element.
    /// </summary>
    /// <param name="byteArray">A Byte array</param>
    /// <param name="currentIndex">Start position</param>
    /// <param name="lengthOfItems">The length of the items</param>
    @Override
    protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems) {
        AtomicInteger index = new AtomicInteger(currentIndex.get());
        this.cellManifestCurrentRevisionExGuid = BasicObject.parse(byteArray, index, ExGuid.class);

        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "CellManifestCurrentRevision",
                    "Stream object over-parse error", null);
        }

        currentIndex.set(index.get());
    }

    /// <summary>
    /// Used to convert the element into a byte List.
    /// </summary>
    /// <param name="byteList">A Byte list</param>
    /// <returns>The number of elements actually contained in the list.</returns>
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        List<Byte> tmpList = this.cellManifestCurrentRevisionExGuid.SerializeToByteList();
        byteList.addAll(tmpList);
        return tmpList.size();
    }
}