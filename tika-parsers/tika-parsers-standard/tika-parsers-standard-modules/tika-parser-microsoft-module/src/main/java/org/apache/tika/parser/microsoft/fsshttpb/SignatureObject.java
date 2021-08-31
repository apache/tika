package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Signature Object
 */
public class SignatureObject extends StreamObject {
    /**
     * Initializes a new instance of the SignatureObject class.
     */
    public SignatureObject() {
        super(StreamObjectTypeHeaderStart.SignatureObject);
        this.SignatureData = new BinaryItem();
    }

    /**
     * Gets or sets a binary item as specified in [MS-FSSHTTPB] section 2.2.1.3 that specifies a value that is unique to the file data represented by this root node object.
     * The value of this item depends on the file chunking algorithm used, as specified in section 2.4.
     */
    public BinaryItem SignatureData;

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

        this.SignatureData = BasicObject.parse(byteArray, index, BinaryItem.class);

        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "Signature", "Stream Object over-parse error",
                    null);
        }

        currentIndex.set(index.get());
    }

    /**
     * Used to convert the element into a byte List.
     *
     * @param byteList A Byte list
     * @return The number of elements
     */
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        int length = byteList.size();
        byteList.addAll(this.SignatureData.SerializeToByteList());
        return byteList.size() - length;
    }
}