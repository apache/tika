package org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class BinaryItem extends BasicObject {
    /**
     * Initializes a new instance of the BinaryItem class.
     */
    public BinaryItem() {
        this.Length = new Compact64bitInt();
        this.Content = new ArrayList<>();
    }

    /**
     * Initializes a new instance of the BinaryItem class with the specified content.
     *
     * @param content Specify the binary content.
     */
    public BinaryItem(Collection<Byte> content) {
        this.Length = new Compact64bitInt();
        this.Content = new ArrayList<>();
        this.Content.addAll(content);
        this.Length.setDecodedValue(this.Content.size());
    }

    /**
     * Gets or sets a compact unsigned 64-bit integer that specifies the count of bytes of Content of the item.
     */
    public Compact64bitInt Length;

    /**
     * Gets or sets a byte stream that specifies the data for the item.
     */
    public List<Byte> Content;

    /**
     * This method is used to convert the element of BinaryItem basic object into a byte List.
     *
     * @return Return the byte list which store the byte information of BinaryItem.
     */
    @Override
    public List<Byte> SerializeToByteList() {
        this.Length.setDecodedValue(this.Content.size());

        List<Byte> result = new ArrayList<>();
        result.addAll(this.Length.SerializeToByteList());
        result.addAll(this.Content);

        return result;
    }

    /**
     * This method is used to de-serialize the BinaryItem basic object from the specified byte array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return Return the length in byte of the BinaryItem basic object.
     */
    @Override
    protected int DoDeserializeFromByteArray(byte[] byteArray, int startIndex) {
        AtomicInteger index = new AtomicInteger(startIndex);

        this.Length = BasicObject.parse(byteArray, index, Compact64bitInt.class);

        this.Content.clear();
        for (long i = 0; i < this.Length.getDecodedValue(); i++) {
            this.Content.add(byteArray[index.getAndIncrement()]);
        }

        return index.get() - startIndex;
    }
}