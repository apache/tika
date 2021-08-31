package org.apache.tika.parser.microsoft.fsshttpb;
/**
 * object declaration
 */

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ObjectGroupObjectDeclare extends StreamObject {
    /**
     * Initializes a new instance of the ObjectGroupObjectDeclare class.
     */
    public ObjectGroupObjectDeclare() {
        super(StreamObjectTypeHeaderStart.ObjectGroupObjectDeclare);
        this.ObjectExtendedGUID = new ExGuid();
        this.ObjectPartitionID = new Compact64bitInt();
        this.ObjectDataSize = new Compact64bitInt();
        this.ObjectReferencesCount = new Compact64bitInt();
        this.CellReferencesCount = new Compact64bitInt();

        this.ObjectPartitionID.setDecodedValue(1);
        this.ObjectReferencesCount.setDecodedValue(1);
        this.CellReferencesCount.setDecodedValue(0);
    }

    /**
     * Gets or sets an extended GUID that specifies the data element hash.
     */
    public ExGuid ObjectExtendedGUID;

    /**
     * Gets or sets a compact unsigned 64-bit integer that specifies the partition.
     */
    public Compact64bitInt ObjectPartitionID;

    /**
     * Gets or sets a compact unsigned 64-bit integer that specifies the size in bytes of the object.binary data opaque
     * to this protocol for the declared object.
     * This MUST match the size of the binary item in the corresponding object data for this object.
     */
    public Compact64bitInt ObjectDataSize;

    /**
     * Gets or sets a compact unsigned 64-bit integer that specifies the number of object references.
     */
    public Compact64bitInt ObjectReferencesCount;

    /**
     * Gets or sets a compact unsigned 64-bit integer that specifies the number of cell references.
     */
    public Compact64bitInt CellReferencesCount;

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

        this.ObjectExtendedGUID = BasicObject.parse(byteArray, index, ExGuid.class);
        this.ObjectPartitionID = BasicObject.parse(byteArray, index, Compact64bitInt.class);
        this.ObjectDataSize = BasicObject.parse(byteArray, index, Compact64bitInt.class);
        this.ObjectReferencesCount = BasicObject.parse(byteArray, index, Compact64bitInt.class);
        this.CellReferencesCount = BasicObject.parse(byteArray, index, Compact64bitInt.class);

        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "ObjectGroupObjectDeclare",
                    "Stream object over-parse error", null);
        }

        currentIndex.set(index.get());
    }

    /**
     * Used to convert the element into a byte List.
     *
     * @param byteList A Byte list
     * @return The number of the element
     */
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        int itemsIndex = byteList.size();
        byteList.addAll(this.ObjectExtendedGUID.SerializeToByteList());
        byteList.addAll(this.ObjectPartitionID.SerializeToByteList());
        byteList.addAll(this.ObjectDataSize.SerializeToByteList());
        byteList.addAll(this.ObjectReferencesCount.SerializeToByteList());
        byteList.addAll(this.CellReferencesCount.SerializeToByteList());
        return byteList.size() - itemsIndex;
    }
}