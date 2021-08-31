package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class StorageIndexManifestMapping extends StreamObject {
    /**
     * Initializes a new instance of the StorageIndexManifestMapping class.
     */
    public StorageIndexManifestMapping() {
        super(StreamObjectTypeHeaderStart.StorageIndexManifestMapping);
    }

    /**
     * Gets or sets the extended GUID of the manifest mapping.
     */
    public ExGuid ManifestMappingExGuid;

    /**
     * Gets or sets the serial number of the manifest mapping.
     */
    public SerialNumber ManifestMappingSerialNumber;

    /**
     * Used to Deserialize the items.
     *
     * @param byteArray     Byte array
     * @param currentIndex  Start position
     * @param lengthOfItems The length of the items
     */
    @Override
    protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems) {
        AtomicInteger index = new AtomicInteger(currentIndex.get());
        this.ManifestMappingExGuid = BasicObject.parse(byteArray, index, ExGuid.class);
        this.ManifestMappingSerialNumber = BasicObject.parse(byteArray, index, SerialNumber.class);

        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "StorageIndexManifestMapping",
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
        byteList.addAll(this.ManifestMappingExGuid.SerializeToByteList());
        byteList.addAll(this.ManifestMappingSerialNumber.SerializeToByteList());
        return byteList.size() - itemsIndex;
    }
}