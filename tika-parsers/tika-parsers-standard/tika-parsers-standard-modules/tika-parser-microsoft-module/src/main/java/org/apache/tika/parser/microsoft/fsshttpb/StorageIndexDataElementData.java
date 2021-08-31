package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class StorageIndexDataElementData extends DataElementData {
    /// <summary>
    /// Initializes a new instance of the StorageIndexDataElementData class.
    /// </summary>
    public StorageIndexDataElementData() {
        this.StorageIndexManifestMapping = new StorageIndexManifestMapping();
        this.StorageIndexCellMappingList = new ArrayList<>();
        this.StorageIndexRevisionMappingList = new ArrayList<>();
    }

    /// <summary>
    /// Gets or sets the storage index manifest mappings (with manifest mapping extended GUID and serial number).
    /// </summary>
    public StorageIndexManifestMapping StorageIndexManifestMapping;

    /// <summary>
    /// Gets or sets  storage index manifest mappings.
    /// </summary>
    public List<StorageIndexCellMapping> StorageIndexCellMappingList;

    /// <summary>
    /// Gets or sets the list of storage index revision mappings (with revision and revision mapping extended GUIDs, and revision mapping serial number).
    /// </summary>
    public List<StorageIndexRevisionMapping> StorageIndexRevisionMappingList;

    /// <summary>
    /// Used to convert the element into a byte List.
    /// </summary>
    /// <returns>A Byte list</returns>
    @Override
    public List<Byte> SerializeToByteList() {
        List<Byte> byteList = new ArrayList<>();

        if (this.StorageIndexManifestMapping != null) {
            byteList.addAll(this.StorageIndexManifestMapping.SerializeToByteList());
        }

        if (this.StorageIndexCellMappingList != null) {
            for (StorageIndexCellMapping cellMapping : this.StorageIndexCellMappingList) {
                byteList.addAll(cellMapping.SerializeToByteList());
            }
        }

        // Storage Index Revision Mapping
        if (this.StorageIndexRevisionMappingList != null) {
            for (StorageIndexRevisionMapping revisionMapping : this.StorageIndexRevisionMappingList) {
                byteList.addAll(revisionMapping.SerializeToByteList());
            }
        }

        return byteList;
    }

    /// <summary>
    /// Used to de-serialize the data element.
    /// </summary>
    /// <param name="byteArray">Byte array</param>
    /// <param name="startIndex">Start position</param>
    /// <returns>The length of the element</returns>
    @Override
    public int DeserializeDataElementDataFromByteArray(byte[] byteArray, int startIndex) {
        AtomicInteger index = new AtomicInteger(startIndex);
        int headerLength = 0;
        AtomicReference<StreamObjectHeaderStart> header = new AtomicReference<>();
        boolean isStorageIndexManifestMappingExist = false;
        while ((headerLength = StreamObjectHeaderStart.TryParse(byteArray, index.get(), header)) != 0) {
            index.addAndGet(headerLength);
            if (header.get().type == StreamObjectTypeHeaderStart.StorageIndexManifestMapping) {
                if (isStorageIndexManifestMappingExist) {
                    throw new DataElementParseErrorException(index.get() - headerLength,
                            "Failed to parse StorageIndexDataElement, only can contain zero or one StorageIndexManifestMapping",
                            null);
                }

                this.StorageIndexManifestMapping =
                        (StorageIndexManifestMapping) StreamObject.ParseStreamObject(header.get(), byteArray, index);
                isStorageIndexManifestMappingExist = true;
            } else if (header.get().type == StreamObjectTypeHeaderStart.StorageIndexCellMapping) {
                this.StorageIndexCellMappingList.add(
                        (StorageIndexCellMapping) StreamObject.ParseStreamObject(header.get(), byteArray, index));
            } else if (header.get().type == StreamObjectTypeHeaderStart.StorageIndexRevisionMapping) {
                this.StorageIndexRevisionMappingList.add(
                        (StorageIndexRevisionMapping) StreamObject.ParseStreamObject(header.get(), byteArray, index));
            } else {
                throw new DataElementParseErrorException(index.get() - headerLength,
                        "Failed to parse StorageIndexDataElement, expect the inner object type StorageIndexCellMapping or StorageIndexRevisionMapping, but actual type value is " +
                                header.get().type, null);
            }
        }

        return index.get() - startIndex;
    }
}