package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/// <summary>
/// object data BLOB reference
/// </summary>
public class ObjectGroupObjectDataBLOBReference extends StreamObject {
    /// <summary>
    /// Initializes a new instance of the ObjectGroupObjectDataBLOBReference class.
    /// </summary>
    public ObjectGroupObjectDataBLOBReference() {
        super(StreamObjectTypeHeaderStart.ObjectGroupObjectDataBLOBReference);
        this.ObjectExtendedGUIDArray = new ExGUIDArray();
        this.cellIDArray = new CellIDArray();
        this.BLOBExtendedGUID = new ExGuid();
    }

    /// <summary>
    /// Gets or sets an extended GUID array that specifies the object references.
    /// </summary>
    public ExGUIDArray ObjectExtendedGUIDArray;

    /// <summary>
    /// Gets or sets a cell ID array that specifies the cell references.
    /// </summary>
    public CellIDArray cellIDArray;

    /// <summary>
    /// Gets or sets an extended GUID that specifies the object data BLOB.
    /// </summary>
    public ExGuid BLOBExtendedGUID;

    /// <summary>
    /// Used to de-serialize the element.
    /// </summary>
    /// <param name="byteArray">A Byte array</param>
    /// <param name="currentIndex">Start position</param>
    /// <param name="lengthOfItems">The length of the items</param>
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

    /// <summary>
    /// Used to convert the element into a byte List.
    /// </summary>
    /// <param name="byteList">A Byte list</param>
    /// <returns>The number of the elements</returns>
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        int itemsIndex = byteList.size();
        byteList.addAll(this.ObjectExtendedGUIDArray.SerializeToByteList());
        byteList.addAll(cellIDArray.SerializeToByteList());
        byteList.addAll(this.BLOBExtendedGUID.SerializeToByteList());
        return byteList.size() - itemsIndex;
    }
}
