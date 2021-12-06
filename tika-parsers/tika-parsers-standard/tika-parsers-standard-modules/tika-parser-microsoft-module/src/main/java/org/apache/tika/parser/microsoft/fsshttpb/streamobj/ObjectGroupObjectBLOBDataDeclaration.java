package org.apache.tika.parser.microsoft.fsshttpb.streamobj;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.BasicObject;
import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.Compact64bitInt;
import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.ExGuid;

/**
 * object data BLOB declaration
 */
public class ObjectGroupObjectBLOBDataDeclaration extends StreamObject {
    /**
     * Initializes a new instance of the ObjectGroupObjectBLOBDataDeclaration class.
     */
    public ObjectGroupObjectBLOBDataDeclaration() {
        super(StreamObjectTypeHeaderStart.ObjectGroupObjectBLOBDataDeclaration);
        this.ObjectExGUID = new ExGuid();
        this.ObjectDataBLOBExGUID = new ExGuid();
        this.ObjectPartitionID = new Compact64bitInt();
        this.ObjectDataSize = new Compact64bitInt();
        this.ObjectReferencesCount = new Compact64bitInt();
        this.CellReferencesCount = new Compact64bitInt();
    }

    /**
     * Gets or sets an extended GUID that specifies the object.
     */
    public ExGuid ObjectExGUID;

    /**
     * Gets or sets an extended GUID that specifies the object data BLOB.
     */
    public ExGuid ObjectDataBLOBExGUID;

    /**
     * Gets or sets a compact unsigned 64-bit integer that specifies the partition.
     */
    public Compact64bitInt ObjectPartitionID;

    /**
     * Gets or sets a compact unsigned 64-bit integer that specifies the size in bytes of the object.opaque binary data  for the declared object.
     * This MUST match the size of the binary item in the corresponding object data BLOB referenced by the Object Data BLOB reference for this object.
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

        this.ObjectExGUID = BasicObject.parse(byteArray, index, ExGuid.class);
        this.ObjectDataBLOBExGUID = BasicObject.parse(byteArray, index, ExGuid.class);
        this.ObjectPartitionID = BasicObject.parse(byteArray, index, Compact64bitInt.class);
        this.ObjectReferencesCount = BasicObject.parse(byteArray, index, Compact64bitInt.class);
        this.CellReferencesCount = BasicObject.parse(byteArray, index, Compact64bitInt.class);

        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "ObjectGroupObjectBLOBDataDeclaration",
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
        byteList.addAll(this.ObjectExGUID.SerializeToByteList());
        byteList.addAll(this.ObjectDataBLOBExGUID.SerializeToByteList());
        byteList.addAll(this.ObjectPartitionID.SerializeToByteList());
        byteList.addAll(this.ObjectDataSize.SerializeToByteList());
        byteList.addAll(this.ObjectReferencesCount.SerializeToByteList());
        byteList.addAll(this.CellReferencesCount.SerializeToByteList());
        return byteList.size() - itemsIndex;
    }
}