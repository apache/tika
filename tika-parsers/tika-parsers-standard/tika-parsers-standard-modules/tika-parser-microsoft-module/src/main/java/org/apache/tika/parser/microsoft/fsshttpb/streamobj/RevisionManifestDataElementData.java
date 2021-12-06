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

public class RevisionManifestDataElementData extends DataElementData {
    /**
     * Initializes a new instance of the RevisionManifestDataElementData class.
     */
    public RevisionManifestDataElementData() {
        this.RevisionManifest = new RevisionManifest();
        this.RevisionManifestRootDeclareList = new ArrayList<>();
        this.RevisionManifestObjectGroupReferencesList = new ArrayList<>();
    }

    public RevisionManifest RevisionManifest;

    public List<RevisionManifestRootDeclare> RevisionManifestRootDeclareList;

    public List<RevisionManifestObjectGroupReferences> RevisionManifestObjectGroupReferencesList;

    /**
     * Used to return the length of this element.
     *
     * @param byteArray  A Byte list
     * @param startIndex Start position
     * @return The length of the element
     */
    @Override
    public int DeserializeDataElementDataFromByteArray(byte[] byteArray, int startIndex) {
        AtomicInteger index = new AtomicInteger(startIndex);
        this.RevisionManifest = StreamObject.GetCurrent(byteArray, index, RevisionManifest.class);

        this.RevisionManifestRootDeclareList = new ArrayList<>();
        this.RevisionManifestObjectGroupReferencesList = new ArrayList<>();
        AtomicReference<StreamObjectHeaderStart> header = new AtomicReference<>();
        int headerLength = 0;
        while ((headerLength = StreamObjectHeaderStart.TryParse(byteArray, index.get(), header)) != 0) {
            if (header.get().type == StreamObjectTypeHeaderStart.RevisionManifestRootDeclare) {
                index.addAndGet(headerLength);
                this.RevisionManifestRootDeclareList.add(
                        (RevisionManifestRootDeclare) StreamObject.ParseStreamObject(header.get(), byteArray, index));
            } else if (header.get().type == StreamObjectTypeHeaderStart.RevisionManifestObjectGroupReferences) {
                index.addAndGet(headerLength);
                this.RevisionManifestObjectGroupReferencesList.add(
                        (RevisionManifestObjectGroupReferences) StreamObject.ParseStreamObject(header.get(), byteArray,
                                index));
            } else {
                throw new DataElementParseErrorException(index.get(),
                        "Failed to parse RevisionManifestDataElement, expect the inner object type RevisionManifestRootDeclare or RevisionManifestObjectGroupReferences, but actual type value is " +
                                header.get().type, null);
            }
        }

        return index.get() - startIndex;
    }

    /**
     * Used to convert the element into a byte List.
     *
     * @return A Byte list
     */
    @Override
    public List<Byte> SerializeToByteList() {
        List<Byte> byteList = new ArrayList<>();
        byteList.addAll(this.RevisionManifest.SerializeToByteList());

        if (this.RevisionManifestRootDeclareList != null) {
            for (RevisionManifestRootDeclare revisionManifestRootDeclare : this.RevisionManifestRootDeclareList) {
                byteList.addAll(revisionManifestRootDeclare.SerializeToByteList());
            }
        }

        if (this.RevisionManifestObjectGroupReferencesList != null) {
            for (RevisionManifestObjectGroupReferences revisionManifestObjectGroupReferences : this.RevisionManifestObjectGroupReferencesList) {
                byteList.addAll(revisionManifestObjectGroupReferences.SerializeToByteList());
            }
        }

        return byteList;
    }
}