package org.apache.tika.parser.microsoft.fsshttpb.streamobj;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.BasicObject;
import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.Compact64bitInt;

/**
 * Specifies an object group metadata
 */
public class ObjectGroupMetadata extends StreamObject {
    /**
     * Initializes a new instance of the ObjectGroupMetadata class.
     */
    public ObjectGroupMetadata() {
        super(StreamObjectTypeHeaderStart.ObjectGroupMetadata);
    }

    /**
     * Gets or sets a compact unsigned 64-bit integer that specifies the expected change frequency of the object.
     * This value MUST be:
     * 0, if the change frequency is not known.
     * 1, if the object is known to change frequently.
     * 2, if the object is known to change infrequently.
     * 3, if the object is known to change independently of any other objects.
     */
    public Compact64bitInt ObjectChangeFrequency;

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
        this.ObjectChangeFrequency = BasicObject.parse(byteArray, index, Compact64bitInt.class);

        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "ObjectGroupMetadata",
                    "Stream object over-parse error", null);
        }

        currentIndex.set(index.get());
    }

    /**
     * Used to convert the element into a byte List
     *
     * @param byteList A Byte list
     * @return The number of elements actually contained in the list
     */
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        List<Byte> tmpList = this.ObjectChangeFrequency.SerializeToByteList();
        byteList.addAll(tmpList);
        return tmpList.size();
    }
}