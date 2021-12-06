package org.apache.tika.parser.microsoft.fsshttpb.streamobj;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.BasicObject;
import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.CellID;
import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.ExGuid;

/**
 * Specifies one or more storage manifest root declare.
 */
public class StorageManifestRootDeclare extends StreamObject {
    /**
     * Initializes a new instance of the StorageManifestRootDeclare class.
     */
    public StorageManifestRootDeclare() {
        super(StreamObjectTypeHeaderStart.StorageManifestRootDeclare);
    }

    /**
     * Gets or sets the root storage manifest.
     */
    public ExGuid RootExGUID;

    /**
     * Gets or sets the cell identifier.
     */
    public CellID cellID;

    /**
     * Used to de-serialize the items.
     *
     * @param byteArray     Byte array
     * @param currentIndex  Start position
     * @param lengthOfItems The length of items
     */
    @Override
    protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems) {
        AtomicInteger index = new AtomicInteger(currentIndex.get());
        this.RootExGUID = BasicObject.parse(byteArray, index, ExGuid.class);
        this.cellID = BasicObject.parse(byteArray, index, CellID.class);

        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "StorageManifestRootDeclare",
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
        byteList.addAll(this.RootExGUID.SerializeToByteList());
        byteList.addAll(this.cellID.SerializeToByteList());
        return byteList.size() - itemsIndex;
    }
}