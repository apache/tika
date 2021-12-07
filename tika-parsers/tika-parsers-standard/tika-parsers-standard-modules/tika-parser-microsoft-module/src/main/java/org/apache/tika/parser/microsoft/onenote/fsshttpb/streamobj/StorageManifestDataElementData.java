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

public class StorageManifestDataElementData extends DataElementData {
    public StorageManifestSchemaGUID storageManifestSchemaGUID;
    public List<StorageManifestRootDeclare> storageManifestRootDeclareList;

    /**
     * Initializes a new instance of the StorageManifestDataElementData class.
     */
    public StorageManifestDataElementData() {
        // Storage Manifest
        this.storageManifestSchemaGUID = new StorageManifestSchemaGUID();
        this.storageManifestRootDeclareList = new ArrayList<>();
    }

    /**
     * Used to convert the element into a byte List.
     *
     * @return A Byte list
     */
    @Override
    public List<Byte> serializeToByteList() throws TikaException, IOException {
        List<Byte> byteList = new ArrayList<Byte>();
        byteList.addAll(this.storageManifestSchemaGUID.serializeToByteList());

        if (this.storageManifestRootDeclareList != null) {
            for (StorageManifestRootDeclare storageManifestRootDeclare : this.storageManifestRootDeclareList) {
                byteList.addAll(storageManifestRootDeclare.serializeToByteList());
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
    public int deserializeDataElementDataFromByteArray(byte[] byteArray, int startIndex)
            throws TikaException, IOException {
        AtomicInteger index = new AtomicInteger(startIndex);

        this.storageManifestSchemaGUID =
                StreamObject.getCurrent(byteArray, index, StorageManifestSchemaGUID.class);
        this.storageManifestRootDeclareList = new ArrayList<>();

        AtomicReference<StreamObjectHeaderStart> header = new AtomicReference<>();
        int headerLength = 0;
        while ((headerLength = StreamObjectHeaderStart.tryParse(byteArray, index.get(), header)) !=
                0) {
            if (header.get().type == StreamObjectTypeHeaderStart.StorageManifestRootDeclare) {
                index.addAndGet(headerLength);
                this.storageManifestRootDeclareList.add(
                        (StorageManifestRootDeclare) StreamObject.parseStreamObject(header.get(),
                                byteArray, index));
            } else {
                throw new DataElementParseErrorException(index.get(),
                        "Failed to parse StorageManifestDataElement, expect the inner object type " +
                                "StorageManifestRootDeclare, but actual type value is " +
                                header.get().type, null);
            }
        }

        return index.get() - startIndex;
    }
}
