/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.microsoft.fsshttpb.streamobj;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tika.parser.microsoft.fsshttpb.exception.DataElementParseErrorException;

public class StorageIndexDataElementData extends DataElementData {
    /**
     * Initializes a new instance of the StorageIndexDataElementData class.
     */
    public StorageIndexDataElementData() {
        this.StorageIndexManifestMapping = new StorageIndexManifestMapping();
        this.StorageIndexCellMappingList = new ArrayList<>();
        this.StorageIndexRevisionMappingList = new ArrayList<>();
    }

    /**
     * Gets or sets the storage index manifest mappings (with manifest mapping extended GUID and serial number).
     */
    public StorageIndexManifestMapping StorageIndexManifestMapping;

    /**
     * Gets or sets  storage index manifest mappings.
     */
    public List<StorageIndexCellMapping> StorageIndexCellMappingList;

    /**
     * Gets or sets the list of storage index revision mappings (with revision and revision mapping extended GUIDs, and revision mapping serial number).
     */
    public List<StorageIndexRevisionMapping> StorageIndexRevisionMappingList;

    /**
     * Used to convert the element into a byte List.
     *
     * @return A Byte list
     */
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

    /**
     * Used to de-serialize the data element.
     *
     * @param byteArray  Byte array
     * @param startIndex Start position
     * @return The length of the element
     */
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