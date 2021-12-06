package org.apache.tika.parser.microsoft.fsshttpb.streamobj;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.parser.microsoft.fsshttpb.util.ByteUtil;

/**
 * Specifies a storage manifest schema GUID
 */
public class StorageManifestSchemaGUID extends StreamObject {
    /**
     * Initializes a new instance of the StorageManifestSchemaGUID class.
     */
    public StorageManifestSchemaGUID() {
        super(StreamObjectTypeHeaderStart.StorageManifestSchemaGUID);
        // this.GUID = DataElementExGuids.StorageManifestGUID;
    }

    /**
     * Gets or sets the schema GUID.
     */
    public UUID guid;

    /**
     * Used to de-serialize the items.
     *
     * @param byteArray     Byte array
     * @param currentIndex  Start position
     * @param lengthOfItems The length of the items
     */
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

    /**
     * Used to convert the element into a byte List.
     *
     * @param byteList A Byte list
     * @return A constant value 16
     */
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        byteList.addAll(ByteUtil.toListOfByte(this.guid.toString().getBytes()));
        return 16;
    }
}