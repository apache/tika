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

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;


public class StorageManifestSchemaGUID extends StreamObject {
    public UUID guid;

    public StorageManifestSchemaGUID() {
        super(StreamObjectTypeHeaderStart.StorageManifestSchemaGUID);
    }

    @Override
    protected void deserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex,
                                                 int lengthOfItems) {
        AtomicInteger index = new AtomicInteger(currentIndex.get());
        if (index.get() < 0 || index.get() > byteArray.length - 16) {
            throw new StreamObjectParseErrorException(currentIndex.get(),
                    "StorageManifestSchemaGUID", "Not enough bytes for the GUID", null);
        }
        byte[] temp = Arrays.copyOfRange(byteArray, index.get(), index.get() + 16);
        this.guid = UUID.nameUUIDFromBytes(temp);
        index.addAndGet(16);

        endItems(currentIndex, index, lengthOfItems, "StorageManifestSchemaGUID");
    }
}
