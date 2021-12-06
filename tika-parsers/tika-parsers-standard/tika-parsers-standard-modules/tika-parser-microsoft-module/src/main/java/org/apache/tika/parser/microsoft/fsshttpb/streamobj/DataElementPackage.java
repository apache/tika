package org.apache.tika.parser.microsoft.fsshttpb.streamobj;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class DataElementPackage extends StreamObject {

    /**
     * Initializes a new instance of the DataElementHash class.
     */
    public DataElementPackage() {
        super(StreamObjectTypeHeaderStart.DataElementPackage);
    }

    /// <summary>
    /// Gets or sets an optional array of data elements or data elements from hashes that specifies the serialized file data elements. If the client doesn’t have any data elements, this MUST NOT be present.
    /// </summary>
    public List<DataElement> DataElements = new ArrayList<>();

    /// <summary>
    /// Gets or sets a reserved field that MUST be set to zero, and MUST be ignored.
    /// </summary>
    public byte reserved;

    /**
     * Used to de-serialize the element.
     *
     * @param byteArray     A Byte array
     * @param currentIndex  Start position
     * @param lengthOfItems The length of the items
     */
    @Override
    protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems) {
        if (lengthOfItems != 1) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "DataElementPackage", "Stream object over-parse error", null);
        }

        reserved = byteArray[currentIndex.getAndIncrement()];

        this.DataElements = new ArrayList<>();
        AtomicReference<DataElement> dataElement = new AtomicReference<>();
        while (StreamObject.TryGetCurrent(byteArray, currentIndex, dataElement, DataElement.class))
        {
            this.DataElements.add(dataElement.get());
        }
    }

    /**
     * Used to convert the element into a byte List
     *
     * @param byteList A Byte list
     * @return The number of elements actually contained in the list
     */
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        // Add the reserved byte
        byteList.add((byte)0);
        for (DataElement dataElement : DataElements) {
            byteList.addAll(dataElement.SerializeToByteList());
        }
        return 1;
    }
}
