package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Specifies an data element hash stream object
 */
public class DataElementHash extends StreamObject {
    /**
     * Initializes a new instance of the DataElementHash class.
     */
    public DataElementHash() {
        super(StreamObjectTypeHeaderStart.DataElementHash);
    }

    /**
     * Gets or sets a compact unsigned 64-bit integer that specifies the hash schema. This value MUST be 1, indicating Content Information Data Structure Version 1.0.
     */
    public Compact64bitInt DataElementHashScheme;

    /**
     * Gets or sets the data element hash data.
     */
    public BinaryItem DataElementHashData;

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
        this.DataElementHashScheme = BasicObject.parse(byteArray, index, Compact64bitInt.class);
        this.DataElementHashData = BasicObject.parse(byteArray, index, BinaryItem.class);

        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "DataElementHash",
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
        int startPoint = byteList.size();
        byteList.addAll(this.DataElementHashScheme.SerializeToByteList());
        byteList.addAll(this.DataElementHashData.SerializeToByteList());

        return byteList.size() - startPoint;
    }
}