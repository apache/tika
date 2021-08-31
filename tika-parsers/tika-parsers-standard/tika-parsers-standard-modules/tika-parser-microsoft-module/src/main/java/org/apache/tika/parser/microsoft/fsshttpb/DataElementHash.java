package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/// <summary>
/// Specifies an data element hash stream object.
/// </summary>
public class DataElementHash extends StreamObject {
    /// <summary>
    /// Initializes a new instance of the DataElementHash class.
    /// </summary>
    public DataElementHash() {
        super(StreamObjectTypeHeaderStart.DataElementHash);
    }

    /// <summary>
    /// Gets or sets a compact unsigned 64-bit integer that specifies the hash schema. This value MUST be 1, indicating Content Information Data Structure Version 1.0.
    /// </summary>
    public Compact64bitInt DataElementHashScheme;

    /// <summary>
    /// Gets or sets the data element hash data.
    /// </summary>
    public BinaryItem DataElementHashData;

    /// <summary>
    /// Used to de-serialize the element.
    /// </summary>
    /// <param name="byteArray">A Byte array</param>
    /// <param name="currentIndex">Start position</param>
    /// <param name="lengthOfItems">The length of the items</param>
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

    /// <summary>
    /// Used to convert the element into a byte List
    /// </summary>
    /// <param name="byteList">A Byte list</param>
    /// <returns>The number of elements actually contained in the list</returns>
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        int startPoint = byteList.size();
        byteList.addAll(this.DataElementHashScheme.SerializeToByteList());
        byteList.addAll(this.DataElementHashData.SerializeToByteList());

        return byteList.size() - startPoint;
    }
}