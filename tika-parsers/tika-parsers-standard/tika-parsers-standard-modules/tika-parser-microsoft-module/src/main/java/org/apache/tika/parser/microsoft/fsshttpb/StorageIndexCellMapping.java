package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/// <summary>
/// Specifies the storage index cell mappings (with cell identifier, cell mapping extended GUID, and cell mapping serial number).
/// </summary>
public class StorageIndexCellMapping extends StreamObject {
    /// <summary>
    /// Initializes a new instance of the StorageIndexCellMapping class.
    /// </summary>
    public StorageIndexCellMapping() {
        super(StreamObjectTypeHeaderStart.StorageIndexCellMapping);
    }

    /// <summary>
    /// Gets or sets the cell identifier.
    /// </summary>
    public CellID CellID;

    /// <summary>
    /// Gets or sets the extended GUID of the cell mapping.
    /// </summary>
    public ExGuid CellMappingExGuid;

    /// <summary>
    /// Gets or sets the serial number of the cell mapping.
    /// </summary>
    public SerialNumber CellMappingSerialNumber;

    /// <summary>
    /// Used to de-serialize the items.
    /// </summary>
    /// <param name="byteArray">A Byte array</param>
    /// <param name="currentIndex">Start position</param>
    /// <param name="lengthOfItems">The length of the items</param>
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

    /// <summary>
    /// Used to convert the element into a byte List.
    /// </summary>
    /// <param name="byteList">A Byte list</param>
    /// <returns>The length of list</returns>
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        int itemsIndex = byteList.size();
        byteList.addAll(this.CellID.SerializeToByteList());
        byteList.addAll(this.CellMappingExGuid.SerializeToByteList());
        byteList.addAll(this.CellMappingSerialNumber.SerializeToByteList());
        return byteList.size() - itemsIndex;
    }
}