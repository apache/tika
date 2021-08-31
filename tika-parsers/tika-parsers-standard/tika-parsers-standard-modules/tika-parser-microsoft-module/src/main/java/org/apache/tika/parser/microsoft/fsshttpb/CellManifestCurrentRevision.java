package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class CellManifestCurrentRevision extends StreamObject {
    /**
     * Initializes a new instance of the CellManifestCurrentRevision class.
     */
    public CellManifestCurrentRevision() {
        super(StreamObjectTypeHeaderStart.CellManifestCurrentRevision);
    }

    /**
     * Gets or sets a 16-bit stream object header that specifies a cell manifest current revision.
     */
    public ExGuid cellManifestCurrentRevisionExGuid;

    /**
     * Used to de-serialize the element.
     *
     * @param byteArray     A Byte array
     * @param currentIndex  Start position
     * @param lengthOfItems The length of the items
     */
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

    /**
     * Used to convert the element into a byte List.
     *
     * @param byteList A Byte list
     * @return The number of elements actually contained in the list.
     */
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        List<Byte> tmpList = this.cellManifestCurrentRevisionExGuid.SerializeToByteList();
        byteList.addAll(tmpList);
        return tmpList.size();
    }
}