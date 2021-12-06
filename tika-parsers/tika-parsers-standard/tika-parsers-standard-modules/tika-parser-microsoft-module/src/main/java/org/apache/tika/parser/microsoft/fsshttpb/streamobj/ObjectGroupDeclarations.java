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
 * Object Group Declarations
 */
public class ObjectGroupDeclarations extends StreamObject {
    /**
     * Initializes a new instance of the ObjectGroupDeclarations class.
     */
    public ObjectGroupDeclarations() {
        super(StreamObjectTypeHeaderStart.ObjectGroupDeclarations);
        this.ObjectDeclarationList = new ArrayList<>();
        this.ObjectGroupObjectBLOBDataDeclarationList = new ArrayList<>();
    }

    /**
     * Gets or sets a list of declarations that specifies the object.
     */
    public List<ObjectGroupObjectDeclare> ObjectDeclarationList;

    /**
     * Gets or sets a list of object data BLOB declarations that specifies the object.
     */
    public List<ObjectGroupObjectBLOBDataDeclaration> ObjectGroupObjectBLOBDataDeclarationList;

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
        this.ObjectDeclarationList = new ArrayList<>();
        this.ObjectGroupObjectBLOBDataDeclarationList = new ArrayList<>();
        while ((headerLength = StreamObjectHeaderStart.TryParse(byteArray, index.get(), header)) != 0) {
            if (header.get().type == StreamObjectTypeHeaderStart.ObjectGroupObjectDeclare) {
                index.addAndGet(headerLength);
                this.ObjectDeclarationList.add(
                        (ObjectGroupObjectDeclare) StreamObject.ParseStreamObject(header.get(), byteArray, index));
            } else if (header.get().type == StreamObjectTypeHeaderStart.ObjectGroupObjectBLOBDataDeclaration) {
                index.addAndGet(headerLength);
                this.ObjectGroupObjectBLOBDataDeclarationList.add(
                        (ObjectGroupObjectBLOBDataDeclaration) StreamObject.ParseStreamObject(header.get(), byteArray,
                                index));
            } else {
                throw new StreamObjectParseErrorException(index.get(), "ObjectGroupDeclarations",
                        "Failed to parse ObjectGroupDeclarations, expect the inner object type either ObjectGroupObjectDeclare or ObjectGroupObjectBLOBDataDeclaration, but actual type value is " +
                                header.get().type, null);
            }
        }

        currentIndex.set(index.get());
    }

    /**
     * Used to convert the element into a byte List
     *
     * @param byteList The Byte list
     * @return A constant value 0
     */
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        if (this.ObjectDeclarationList != null) {
            for (ObjectGroupObjectDeclare objectGroupObjectDeclare : this.ObjectDeclarationList) {
                byteList.addAll(objectGroupObjectDeclare.SerializeToByteList());
            }
        }

        if (this.ObjectGroupObjectBLOBDataDeclarationList != null) {
            for (ObjectGroupObjectBLOBDataDeclaration objectGroupObjectBLOBDataDeclaration : this.ObjectGroupObjectBLOBDataDeclarationList) {
                byteList.addAll(objectGroupObjectBLOBDataDeclaration.SerializeToByteList());
            }
        }

        return 0;
    }
}