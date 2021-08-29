package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class StorageManifestDataElementData extends DataElementData {
    /// <summary>
    /// Initializes a new instance of the StorageManifestDataElementData class.
    /// </summary>
    public StorageManifestDataElementData() {
        // Storage Manifest
        this.storageManifestSchemaGUID = new StorageManifestSchemaGUID();
        this.StorageManifestRootDeclareList = new ArrayList<>();
    }

    /// <summary>
    /// Gets or sets storage manifest schema GUID.
    /// </summary>
    public StorageManifestSchemaGUID storageManifestSchemaGUID;

    /// <summary>
    /// Gets or sets storage manifest root declare.
    /// </summary>
    public List<StorageManifestRootDeclare> StorageManifestRootDeclareList;

    /// <summary>
    /// Used to convert the element into a byte List.
    /// </summary>
    /// <returns>A Byte list</returns>
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

    /// <summary>
    /// Used to de-serialize data element.
    /// </summary>
    /// <param name="byteArray">Byte array</param>
    /// <param name="startIndex">Start position</param>
    /// <returns>The length of the array</returns>
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