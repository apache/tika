package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class DataHashObject extends StreamObject {
    /// <summary>
    /// Initializes a new instance of the DataHashObject class.
    /// </summary>
    public DataHashObject() {
        super(StreamObjectTypeHeaderStart.DataHashObject);
        this.Data = new BinaryItem();
    }

    /// <summary>
    ///  Gets or sets a binary item as specified in [MS-FSSHTTPB] section 2.2.1.3 that specifies a value that is unique to the file data represented by this root node object.
    ///  The value of this item depends on the file chunking algorithm used, as specified in section 2.4.
    /// </summary>
    public BinaryItem Data;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DataHashObject that = (DataHashObject) o;
        return Objects.equals(Data, that.Data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Data);
    }

    @Override
    public String toString() {
        return "DataHashObject{" +
                "Data=" + Data +
                ", streamObjectHeaderEnd=" + streamObjectHeaderEnd +
                '}';
    }

    /// <summary>
    /// Used to de-serialize the element.
    /// </summary>
    /// <param name="byteArray">A Byte array</param>
    /// <param name="currentIndex">Start position</param>
    /// <param name="lengthOfItems">The length of the items</param>
    @Override
    protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems) {
        AtomicInteger index = new AtomicInteger(currentIndex.get());

        this.Data = BasicObject.parse(byteArray, index, BinaryItem.class);

        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "Signature", "Stream Object over-parse error",
                    null);
        }

        currentIndex.set(index.get());
    }

    /// <summary>
    /// Used to convert the element into a byte List.
    /// </summary>
    /// <param name="byteList">A Byte list</param>
    /// <returns>The number of elements</returns>
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        int length = byteList.size();
        byteList.addAll(this.Data.SerializeToByteList());
        return byteList.size() - length;
    }
}