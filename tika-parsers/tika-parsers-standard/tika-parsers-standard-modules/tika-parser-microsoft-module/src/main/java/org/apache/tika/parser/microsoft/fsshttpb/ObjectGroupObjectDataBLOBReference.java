package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * object data BLOB reference
 */
public class ObjectGroupObjectDataBLOBReference extends StreamObject {
    /**
     * Initializes a new instance of the ObjectGroupObjectDataBLOBReference class.
     */
    public ObjectGroupObjectDataBLOBReference() {
        super(StreamObjectTypeHeaderStart.ObjectGroupObjectDataBLOBReference);
        this.ObjectExtendedGUIDArray = new ExGUIDArray();
        this.cellIDArray = new CellIDArray();
        this.BLOBExtendedGUID = new ExGuid();
    }

    /**
     * Gets or sets an extended GUID array that specifies the object references.
     */
    public ExGUIDArray ObjectExtendedGUIDArray;

    /**
     * Gets or sets a cell ID array that specifies the cell references.
     */
    public CellIDArray cellIDArray;

    /**
     * Gets or sets an extended GUID that specifies the object data BLOB.
     */
    public ExGuid BLOBExtendedGUID;

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
        this.ObjectExtendedGUIDArray = BasicObject.parse(byteArray, index, ExGUIDArray.class);
        this.cellIDArray = BasicObject.parse(byteArray, index, CellIDArray.class);
        this.BLOBExtendedGUID = BasicObject.parse(byteArray, index, ExGuid.class);

        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "ObjectGroupObjectDataBLOBReference",
                    "Stream object over-parse error", null);
        }

        currentIndex.set(index.get());
    }

    /**
     * Used to convert the element into a byte List.
     *
     * @param byteList A Byte list
     * @return The number of the elements
     */
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        int itemsIndex = byteList.size();
        byteList.addAll(this.ObjectExtendedGUIDArray.SerializeToByteList());
        byteList.addAll(cellIDArray.SerializeToByteList());
        byteList.addAll(this.BLOBExtendedGUID.SerializeToByteList());
        return byteList.size() - itemsIndex;
    }
}
