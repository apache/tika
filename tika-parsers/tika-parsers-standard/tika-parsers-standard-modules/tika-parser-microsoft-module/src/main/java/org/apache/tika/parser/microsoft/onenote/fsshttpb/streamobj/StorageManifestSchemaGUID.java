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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.ByteUtil;

/**
 * Specifies a storage manifest schema GUID
 */
public class StorageManifestSchemaGUID extends StreamObject {
    public UUID guid;

    /**
     * Initializes a new instance of the StorageManifestSchemaGUID class.
     */
    public StorageManifestSchemaGUID() {
        super(StreamObjectTypeHeaderStart.StorageManifestSchemaGUID);
        // this.GUID = DataElementExGuids.StorageManifestGUID;
    }

    /**
     * Used to de-serialize the items.
     *
     * @param byteArray     Byte array
     * @param currentIndex  Start position
     * @param lengthOfItems The length of the items
     */
    @Override
    protected void deserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex,
                                                 int lengthOfItems) {
        AtomicInteger index = new AtomicInteger(currentIndex.get());
        byte[] temp = Arrays.copyOf(byteArray, 16);
        this.guid = UUID.nameUUIDFromBytes(temp);
        index.addAndGet(16);

        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(),
                    "StorageManifestSchemaGUID", "Stream object over-parse error", null);
        }

        currentIndex.set(index.get());
    }

    /**
     * Used to convert the element into a byte List.
     *
     * @param byteList A Byte list
     * @return A constant value 16
     */
    @Override
    protected int serializeItemsToByteList(List<Byte> byteList) {
        byteList.addAll(
                ByteUtil.toListOfByte(this.guid.toString().getBytes(StandardCharsets.UTF_8)));
        return 16;
    }
}
