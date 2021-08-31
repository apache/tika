package org.apache.tika.parser.microsoft.fsshttpb;
/**
 * object data
 */

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ObjectGroupObjectData extends StreamObject {
    /**
     * Initializes a new instance of the ObjectGroupObjectData class.
     */
    public ObjectGroupObjectData() {
        super(StreamObjectTypeHeaderStart.ObjectGroupObjectData);
        this.ObjectExGUIDArray = new ExGUIDArray();
        this.cellIDArray = new CellIDArray();
        this.Data = new BinaryItem();
    }

    /**
     * Gets or sets an extended GUID array that specifies the object group.
     */
    public ExGUIDArray ObjectExGUIDArray;

    /**
     * Gets or sets a cell ID array that specifies the object group.
     */
    public CellIDArray cellIDArray;

    /**
     * Gets or sets a byte stream that specifies the binary data which is opaque to this protocol.
     */
    public BinaryItem Data;

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
        this.ObjectExGUIDArray = BasicObject.parse(byteArray, index, ExGUIDArray.class);
        this.cellIDArray = BasicObject.parse(byteArray, index, CellIDArray.class);
        this.Data = BasicObject.parse(byteArray, index, BinaryItem.class);

        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "ObjectGroupObjectData",
                    "Stream object over-parse error", null);
        }

        currentIndex.set(index.get());
    }

    /**
     * Used to convert the element into a byte List
     *
     * @param byteList A Byte list
     * @return The number of the element
     */
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        int itemsIndex = byteList.size();
        byteList.addAll(this.ObjectExGUIDArray.SerializeToByteList());
        byteList.addAll(this.cellIDArray.SerializeToByteList());
        byteList.addAll(this.Data.SerializeToByteList());
        return byteList.size() - itemsIndex;
    }
}