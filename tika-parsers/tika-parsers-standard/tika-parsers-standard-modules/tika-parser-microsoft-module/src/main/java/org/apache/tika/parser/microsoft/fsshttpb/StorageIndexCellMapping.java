package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Specifies the storage index cell mappings (with cell identifier, cell mapping extended GUID, and cell mapping serial number)
 */
public class StorageIndexCellMapping extends StreamObject {
    /**
     * Initializes a new instance of the StorageIndexCellMapping class.
     */
    public StorageIndexCellMapping() {
        super(StreamObjectTypeHeaderStart.StorageIndexCellMapping);
    }

    /**
     * Gets or sets the cell identifier.
     */
    public CellID CellID;

    /**
     * Gets or sets the extended GUID of the cell mapping.
     */
    public ExGuid CellMappingExGuid;

    /**
     * Gets or sets the serial number of the cell mapping.
     */
    public SerialNumber CellMappingSerialNumber;

    /**
     * Used to de-serialize the items.
     *
     * @param byteArray     A Byte array
     * @param currentIndex  Start position
     * @param lengthOfItems The length of the items
     */
    @Override
    protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems) {
        AtomicInteger index = new AtomicInteger(currentIndex.get());
        this.CellID = BasicObject.parse(byteArray, index, CellID.class);
        this.CellMappingExGuid = BasicObject.parse(byteArray, index, ExGuid.class);
        this.CellMappingSerialNumber = BasicObject.parse(byteArray, index, SerialNumber.class);

        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "StorageIndexCellMapping",
                    "Stream object over-parse error", null);
        }

        currentIndex.set(index.get());
    }

    /**
     * Used to convert the element into a byte List.
     *
     * @param byteList A Byte list
     * @return The length of list
     */
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        int itemsIndex = byteList.size();
        byteList.addAll(this.CellID.SerializeToByteList());
        byteList.addAll(this.CellMappingExGuid.SerializeToByteList());
        byteList.addAll(this.CellMappingSerialNumber.SerializeToByteList());
        return byteList.size() - itemsIndex;
    }
}