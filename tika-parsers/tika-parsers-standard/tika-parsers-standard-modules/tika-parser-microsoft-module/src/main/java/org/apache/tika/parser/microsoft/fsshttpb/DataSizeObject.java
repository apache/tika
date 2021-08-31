package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Data Size Object
 */
public class DataSizeObject extends StreamObject {
    /**
     * Initializes a new instance of the DataSizeObject class.
     */
    public DataSizeObject() {
        super(StreamObjectTypeHeaderStart.DataSizeObject);
    }

    /**
     * Gets or sets an unsigned 64-bit integer that specifies the size of the file data represented by this root node object.
     */
    public long DataSize;

    /**
     * Used to de-serialize the element.
     *
     * @param byteArray     A Byte array
     * @param currentIndex  Start position
     * @param lengthOfItems The length of the items
     */
    @Override
    protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems) {
        if (lengthOfItems != 8) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "DataSize", "Stream Object over-parse error",
                    null);
        }

        this.DataSize = LittleEndianBitConverter.ToUInt64(byteArray, currentIndex.get());
        currentIndex.addAndGet(8);
    }

    /**
     * Used to convert the element into a byte List.
     *
     * @param byteList A Byte list
     * @return A constant value 8
     */
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        ByteUtil.appendByteArrayToListOfByte(byteList, LittleEndianBitConverter.GetBytes(this.DataSize));
        return 8;
    }
}