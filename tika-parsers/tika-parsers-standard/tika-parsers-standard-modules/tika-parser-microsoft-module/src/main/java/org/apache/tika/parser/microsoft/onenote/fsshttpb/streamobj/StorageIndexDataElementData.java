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

package org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.exception.DataElementParseErrorException;

public class StorageIndexDataElementData extends DataElementData {
    public StorageIndexManifestMapping storageIndexManifestMapping;
    public List<StorageIndexCellMapping> storageIndexCellMappingList;
    public List<StorageIndexRevisionMapping> storageIndexRevisionMappingList;

    /**
     * Initializes a new instance of the StorageIndexDataElementData class.
     */
    public StorageIndexDataElementData() {
        this.storageIndexManifestMapping = new StorageIndexManifestMapping();
        this.storageIndexCellMappingList = new ArrayList<>();
        this.storageIndexRevisionMappingList = new ArrayList<>();
    }

    /**
     * Used to convert the element into a byte List.
     *
     * @return A Byte list
     */
    @Override
    public List<Byte> serializeToByteList() throws TikaException, IOException {
        List<Byte> byteList = new ArrayList<>();

        if (this.storageIndexManifestMapping != null) {
            byteList.addAll(this.storageIndexManifestMapping.serializeToByteList());
        }

        if (this.storageIndexCellMappingList != null) {
            for (StorageIndexCellMapping cellMapping : this.storageIndexCellMappingList) {
                byteList.addAll(cellMapping.serializeToByteList());
            }
        }

        // Storage Index Revision Mapping
        if (this.storageIndexRevisionMappingList != null) {
            for (StorageIndexRevisionMapping revisionMapping : this.storageIndexRevisionMappingList) {
                byteList.addAll(revisionMapping.serializeToByteList());
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
    public int deserializeDataElementDataFromByteArray(byte[] byteArray, int startIndex)
            throws TikaException, IOException {
        AtomicInteger index = new AtomicInteger(startIndex);
        int headerLength = 0;
        AtomicReference<StreamObjectHeaderStart> header = new AtomicReference<>();
        boolean isStorageIndexManifestMappingExist = false;
        while ((headerLength = StreamObjectHeaderStart.tryParse(byteArray, index.get(), header)) !=
                0) {
            index.addAndGet(headerLength);
            if (header.get().type == StreamObjectTypeHeaderStart.StorageIndexManifestMapping) {
                if (isStorageIndexManifestMappingExist) {
                    throw new DataElementParseErrorException(index.get() - headerLength,
                            "Failed to parse StorageIndexDataElement, only can contain zero or one " +
                                    "StorageIndexManifestMapping", null);
                }

                this.storageIndexManifestMapping =
                        (StorageIndexManifestMapping) StreamObject.parseStreamObject(header.get(),
                                byteArray, index);
                isStorageIndexManifestMappingExist = true;
            } else if (header.get().type == StreamObjectTypeHeaderStart.StorageIndexCellMapping) {
                this.storageIndexCellMappingList.add(
                        (StorageIndexCellMapping) StreamObject.parseStreamObject(header.get(),
                                byteArray, index));
            } else if (header.get().type ==
                    StreamObjectTypeHeaderStart.StorageIndexRevisionMapping) {
                this.storageIndexRevisionMappingList.add(
                        (StorageIndexRevisionMapping) StreamObject.parseStreamObject(header.get(),
                                byteArray, index));
            } else {
                throw new DataElementParseErrorException(index.get() - headerLength,
                        "Failed to parse StorageIndexDataElement, expect the inner object type " +
                                "StorageIndexCellMapping or StorageIndexRevisionMapping, but actual type value is " +
                                header.get().type, null);
            }
        }

        return index.get() - startIndex;
    }
}
