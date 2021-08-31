package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/// <summary>
/// Specifies a storage manifest schema GUID.
/// </summary>
public class StorageManifestSchemaGUID extends StreamObject {
    /// <summary>
    /// Initializes a new instance of the StorageManifestSchemaGUID class.
    /// </summary>
    public StorageManifestSchemaGUID() {
        super(StreamObjectTypeHeaderStart.StorageManifestSchemaGUID);
        // this.GUID = DataElementExGuids.StorageManifestGUID;
    }

    /// <summary>
    /// Gets or sets the schema GUID.
    /// </summary>
    public UUID guid;

    /// <summary>
    /// Used to de-serialize the items.
    /// </summary>
    /// <param name="byteArray">Byte array</param>
    /// <param name="currentIndex">Start position</param>
    /// <param name="lengthOfItems">The length of the items</param>
    @Override
    protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems) {
        AtomicInteger index = new AtomicInteger(currentIndex.get());
        byte[] temp = Arrays.copyOf(byteArray, 16);
        this.guid = UUID.nameUUIDFromBytes(temp);
        index.addAndGet(16);

        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "StorageManifestSchemaGUID",
                    "Stream object over-parse error", null);
        }

        currentIndex.set(index.get());
    }

    /// <summary>
    /// Used to convert the element into a byte List.
    /// </summary>
    /// <param name="byteList">A Byte list</param>
    /// <returns>A constant value 16</returns>
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        byteList.addAll(ByteUtil.toListOfByte(this.guid.toString().getBytes()));
        return 16;
    }
}