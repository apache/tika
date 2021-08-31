package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class StorageManifestDataElementData extends DataElementData {
    /**
     * Initializes a new instance of the StorageManifestDataElementData class.
     */
    public StorageManifestDataElementData() {
        // Storage Manifest
        this.storageManifestSchemaGUID = new StorageManifestSchemaGUID();
        this.StorageManifestRootDeclareList = new ArrayList<>();
    }

    /**
     * Gets or sets storage manifest schema GUID.
     */
    public StorageManifestSchemaGUID storageManifestSchemaGUID;

    /**
     * Gets or sets storage manifest root declare.
     */
    public List<StorageManifestRootDeclare> StorageManifestRootDeclareList;

    /**
     * Used to convert the element into a byte List.
     *
     * @return A Byte list
     */
    @Override
    public List<Byte> SerializeToByteList() {
        List<Byte> byteList = new ArrayList<Byte>();
        byteList.addAll(this.storageManifestSchemaGUID.SerializeToByteList());

        if (this.StorageManifestRootDeclareList != null) {
            for (StorageManifestRootDeclare storageManifestRootDeclare : this.StorageManifestRootDeclareList) {
                byteList.addAll(storageManifestRootDeclare.SerializeToByteList());
            }
        }

        return byteList;
    }

    /**
     * Used to de-serialize data element.
     *
     * @param byteArray  Byte array
     * @param startIndex Start position
     * @return The length of the array
     */
    @Override
    public int DeserializeDataElementDataFromByteArray(byte[] byteArray, int startIndex) {
        AtomicInteger index = new AtomicInteger(startIndex);

        this.storageManifestSchemaGUID = StreamObject.GetCurrent(byteArray, index, StorageManifestSchemaGUID.class);
        this.StorageManifestRootDeclareList = new ArrayList<>();

        AtomicReference<StreamObjectHeaderStart> header = new AtomicReference<>();
        int headerLength = 0;
        while ((headerLength = StreamObjectHeaderStart.TryParse(byteArray, index.get(), header)) != 0) {
            if (header.get().type == StreamObjectTypeHeaderStart.StorageManifestRootDeclare) {
                index.addAndGet(headerLength);
                this.StorageManifestRootDeclareList.add(
                        (StorageManifestRootDeclare) StreamObject.ParseStreamObject(header.get(), byteArray, index));
            } else {
                throw new DataElementParseErrorException(index.get(),
                        "Failed to parse StorageManifestDataElement, expect the inner object type StorageManifestRootDeclare, but actual type value is " +
                                header.get().type, null);
            }
        }

        return index.get() - startIndex;
    }
}