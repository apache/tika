package org.apache.tika.parser.microsoft.fsshttpb.streamobj;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.BasicObject;
import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.ExGuid;
import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.SerialNumber;

/**
 * Specifies the storage index revision mappings (with revision and revision mapping extended GUIDs, and revision mapping serial number)
 */
public class StorageIndexRevisionMapping extends StreamObject {
    /**
     * Initializes a new instance of the StorageIndexRevisionMapping class.
     */
    public StorageIndexRevisionMapping() {
        super(StreamObjectTypeHeaderStart.StorageIndexRevisionMapping);
    }

    /**
     * Gets or sets the extended GUID of the revision.
     */
    public ExGuid RevisionExGuid;

    /**
     * Gets or sets the extended GUID of the revision mapping.
     */
    public ExGuid RevisionMappingExGuid;

    /**
     * Gets or sets the serial number of the revision mapping.
     */
    public SerialNumber RevisionMappingSerialNumber;

    /**
     * Used to de-serialize the items
     *
     * @param byteArray     A Byte array
     * @param currentIndex  Start position
     * @param lengthOfItems The length of the items
     */
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

    /**
     * Used to convert the element into a byte List.
     *
     * @param byteList A Byte list
     * @return The length of list
     */
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        int itemsIndex = byteList.size();
        byteList.addAll(this.RevisionExGuid.SerializeToByteList());
        byteList.addAll(this.RevisionMappingExGuid.SerializeToByteList());
        byteList.addAll(this.RevisionMappingSerialNumber.SerializeToByteList());
        return byteList.size() - itemsIndex;
    }
}