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

/**
 * Object Metadata Declaration
 */
public class ObjectGroupMetadataDeclarations extends StreamObject {
    public List<ObjectGroupMetadata> objectGroupMetadataList;

    /**
     * Initializes a new instance of the ObjectGroupMetadataDeclarations class.
     */
    public ObjectGroupMetadataDeclarations() {
        super(StreamObjectTypeHeaderStart.ObjectGroupMetadataDeclarations);
        this.objectGroupMetadataList = new ArrayList<>();
    }

    /**
     * Used to convert the element into a byte List
     *
     * @param byteList A Byte list
     * @return A constant value 0
     */
    @Override
    protected int serializeItemsToByteList(List<Byte> byteList) throws TikaException, IOException {
        if (this.objectGroupMetadataList != null) {
            for (ObjectGroupMetadata objectGroupMetadata : this.objectGroupMetadataList) {
                byteList.addAll(objectGroupMetadata.serializeToByteList());
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
    protected void deserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex,
                                                 int lengthOfItems)
            throws TikaException, IOException {
        if (lengthOfItems != 0) {
            throw new StreamObjectParseErrorException(currentIndex.get(),
                    "ObjectGroupMetadataDeclarations", "Stream object over-parse error", null);
        }

        AtomicInteger index = new AtomicInteger(currentIndex.get());
        int headerLength;
        AtomicReference<StreamObjectHeaderStart> header = new AtomicReference<>();
        this.objectGroupMetadataList = new ArrayList<>();

        while ((headerLength = StreamObjectHeaderStart.tryParse(byteArray, index.get(), header)) !=
                0) {
            index.addAndGet(headerLength);
            if (header.get().type == StreamObjectTypeHeaderStart.ObjectGroupMetadata) {
                this.objectGroupMetadataList.add(
                        (ObjectGroupMetadata) StreamObject.parseStreamObject(header.get(),
                                byteArray, index));
            } else {
                throw new StreamObjectParseErrorException(index.get(), "ObjectGroupDeclarations",
                        "Failed to parse ObjectGroupMetadataDeclarations, expect the inner object type " +
                                "ObjectGroupMetadata, but actual type value is " +
                                header.get().type, null);
            }
        }

        currentIndex.set(index.get());
    }
}
