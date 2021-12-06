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

/**
 * Object Data
 */
public class ObjectGroupData extends StreamObject {
    /**
     * Initializes a new instance of the ObjectGroupData class.
     */
    public ObjectGroupData() {
        super(StreamObjectTypeHeaderStart.ObjectGroupData);
        this.ObjectGroupObjectDataList = new ArrayList<ObjectGroupObjectData>();
        this.ObjectGroupObjectDataBLOBReferenceList = new ArrayList<ObjectGroupObjectDataBLOBReference>();
    }

    /**
     * Gets or sets a list of Object Data.
     */
    public List<ObjectGroupObjectData> ObjectGroupObjectDataList;

    /**
     * Gets or sets a list of object data BLOB references that specifies the object.
     */
    public List<ObjectGroupObjectDataBLOBReference> ObjectGroupObjectDataBLOBReferenceList;

    /**
     * Used to convert the element into a byte List
     *
     * @param byteList A Byte list
     * @return A constant value 0
     */
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        if (this.ObjectGroupObjectDataList != null) {
            for (ObjectGroupObjectData objectGroupObjectData : this.ObjectGroupObjectDataList) {
                byteList.addAll(objectGroupObjectData.SerializeToByteList());
            }
        }

        if (this.ObjectGroupObjectDataBLOBReferenceList != null) {
            for (ObjectGroupObjectDataBLOBReference objectGroupObjectDataBLOBReference : this.ObjectGroupObjectDataBLOBReferenceList) {
                byteList.addAll(objectGroupObjectDataBLOBReference.SerializeToByteList());
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
            throw new StreamObjectParseErrorException(currentIndex.get(), "ObjectGroupDeclarations",
                    "Stream object over-parse error", null);
        }

        AtomicInteger index = new AtomicInteger(currentIndex.get());
        int headerLength = 0;
        AtomicReference<StreamObjectHeaderStart> header = new AtomicReference<>();

        this.ObjectGroupObjectDataList = new ArrayList<>();
        this.ObjectGroupObjectDataBLOBReferenceList = new ArrayList<>();

        while ((headerLength = StreamObjectHeaderStart.TryParse(byteArray, index.get(), header)) != 0) {
            StreamObjectTypeHeaderStart type = header.get().type;
            if (type == StreamObjectTypeHeaderStart.ObjectGroupObjectData) {
                index.addAndGet(headerLength);
                this.ObjectGroupObjectDataList.add(
                        (ObjectGroupObjectData) StreamObject.ParseStreamObject(header.get(), byteArray, index));
            } else if (type == StreamObjectTypeHeaderStart.ObjectGroupObjectDataBLOBReference) {
                index.addAndGet(headerLength);
                this.ObjectGroupObjectDataBLOBReferenceList.add(
                        (ObjectGroupObjectDataBLOBReference) StreamObject.ParseStreamObject(header.get(), byteArray,
                                index));
            } else {
                throw new StreamObjectParseErrorException(index.get(), "ObjectGroupDeclarations",
                        "Failed to parse ObjectGroupData, expect the inner object type either ObjectGroupObjectData or ObjectGroupObjectDataBLOBReference, but actual type value is " +
                                type, null);
            }
        }

        currentIndex.set(index.get());
    }
}