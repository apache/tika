package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Object Metadata Declaration
 */
public class ObjectGroupMetadataDeclarations extends StreamObject {
    /**
     * Initializes a new instance of the ObjectGroupMetadataDeclarations class.
     */
    public ObjectGroupMetadataDeclarations() {
        super(StreamObjectTypeHeaderStart.ObjectGroupMetadataDeclarations);
        this.ObjectGroupMetadataList = new ArrayList<>();
    }

    /**
     * Gets or sets a list of Object Metadata.
     */
    public List<ObjectGroupMetadata> ObjectGroupMetadataList;

    /**
     * Used to convert the element into a byte List
     *
     * @param byteList A Byte list
     * @return A constant value 0
     */
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        if (this.ObjectGroupMetadataList != null) {
            for (ObjectGroupMetadata objectGroupMetadata : this.ObjectGroupMetadataList) {
                byteList.addAll(objectGroupMetadata.SerializeToByteList());
            }
        }

        return 0;
    }

    /**
     * Used to de-serialize the element.
     *
     * @param byteArray     A Byte array
     * @param currentIndex  Start position
     * @param lengthOfItems The length of the items
     */
    @Override
    protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems) {
        if (lengthOfItems != 0) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "ObjectGroupMetadataDeclarations",
                    "Stream object over-parse error", null);
        }

        AtomicInteger index = new AtomicInteger(currentIndex.get());
        int headerLength;
        AtomicReference<StreamObjectHeaderStart> header = new AtomicReference<>();
        this.ObjectGroupMetadataList = new ArrayList<>();

        while ((headerLength = StreamObjectHeaderStart.TryParse(byteArray, index.get(), header)) != 0) {
            index.addAndGet(headerLength);
            if (header.get().type == StreamObjectTypeHeaderStart.ObjectGroupMetadata) {
                this.ObjectGroupMetadataList.add(
                        (ObjectGroupMetadata) StreamObject.ParseStreamObject(header.get(), byteArray, index));
            } else {
                throw new StreamObjectParseErrorException(index.get(), "ObjectGroupDeclarations",
                        "Failed to parse ObjectGroupMetadataDeclarations, expect the inner object type ObjectGroupMetadata, but actual type value is " +
                                header.get().type, null);
            }
        }

        currentIndex.set(index.get());
    }
}