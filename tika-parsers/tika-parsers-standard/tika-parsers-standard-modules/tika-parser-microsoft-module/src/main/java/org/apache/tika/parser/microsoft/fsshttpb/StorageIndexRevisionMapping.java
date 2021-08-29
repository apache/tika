package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/// <summary>
/// Specifies the storage index revision mappings (with revision and revision mapping extended GUIDs, and revision mapping serial number).
/// </summary>
public class StorageIndexRevisionMapping extends StreamObject {
    /// <summary>
    /// Initializes a new instance of the StorageIndexRevisionMapping class.
    /// </summary>
    public StorageIndexRevisionMapping() {
        super(StreamObjectTypeHeaderStart.StorageIndexRevisionMapping);
    }

    /// <summary>
    /// Gets or sets the extended GUID of the revision.
    /// </summary>
    public ExGuid RevisionExGuid;

    /// <summary>
    /// Gets or sets the extended GUID of the revision mapping.
    /// </summary>
    public ExGuid RevisionMappingExGuid;

    /// <summary>
    /// Gets or sets the serial number of the revision mapping.
    /// </summary>
    public SerialNumber RevisionMappingSerialNumber;

    /// <summary>
    /// Used to de-serialize the items
    /// </summary>
    /// <param name="byteArray">A Byte array</param>
    /// <param name="currentIndex">Start position</param>
    /// <param name="lengthOfItems">The length of the items</param>
    @Override
    protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems) {
        AtomicInteger index = new AtomicInteger(currentIndex.get());
        this.RevisionExGuid = BasicObject.parse(byteArray, index, ExGuid.class);
        this.RevisionMappingExGuid = BasicObject.parse(byteArray, index, ExGuid.class);
        this.RevisionMappingSerialNumber = BasicObject.parse(byteArray, index, SerialNumber.class);

        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "StorageIndexRevisionMapping",
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
        byteList.addAll(this.RevisionExGuid.SerializeToByteList());
        byteList.addAll(this.RevisionMappingExGuid.SerializeToByteList());
        byteList.addAll(this.RevisionMappingSerialNumber.SerializeToByteList());
        return byteList.size() - itemsIndex;
    }
}