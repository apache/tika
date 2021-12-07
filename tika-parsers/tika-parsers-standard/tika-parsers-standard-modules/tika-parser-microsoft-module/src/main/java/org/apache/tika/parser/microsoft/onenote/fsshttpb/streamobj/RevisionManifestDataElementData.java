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

public class RevisionManifestDataElementData extends DataElementData {
    public RevisionManifest revisionManifest;
    public List<RevisionManifestRootDeclare> revisionManifestRootDeclareList;
    public List<RevisionManifestObjectGroupReferences> revisionManifestObjectGroupReferences;

    /**
     * Initializes a new instance of the RevisionManifestDataElementData class.
     */
    public RevisionManifestDataElementData() {
        this.revisionManifest = new RevisionManifest();
        this.revisionManifestRootDeclareList = new ArrayList<>();
        this.revisionManifestObjectGroupReferences = new ArrayList<>();
    }

    /**
     * Used to return the length of this element.
     *
     * @param byteArray  A Byte list
     * @param startIndex Start position
     * @return The length of the element
     */
    @Override
    public int deserializeDataElementDataFromByteArray(byte[] byteArray, int startIndex)
            throws TikaException, IOException {
        AtomicInteger index = new AtomicInteger(startIndex);
        this.revisionManifest = StreamObject.getCurrent(byteArray, index, RevisionManifest.class);

        this.revisionManifestRootDeclareList = new ArrayList<>();
        this.revisionManifestObjectGroupReferences = new ArrayList<>();
        AtomicReference<StreamObjectHeaderStart> header = new AtomicReference<>();
        int headerLength = 0;
        while ((headerLength = StreamObjectHeaderStart.tryParse(byteArray, index.get(), header)) !=
                0) {
            if (header.get().type == StreamObjectTypeHeaderStart.RevisionManifestRootDeclare) {
                index.addAndGet(headerLength);
                this.revisionManifestRootDeclareList.add(
                        (RevisionManifestRootDeclare) StreamObject.parseStreamObject(header.get(),
                                byteArray, index));
            } else if (header.get().type ==
                    StreamObjectTypeHeaderStart.RevisionManifestObjectGroupReferences) {
                index.addAndGet(headerLength);
                this.revisionManifestObjectGroupReferences.add(
                        (RevisionManifestObjectGroupReferences) StreamObject.parseStreamObject(
                                header.get(), byteArray, index));
            } else {
                throw new DataElementParseErrorException(index.get(),
                        "Failed to parse RevisionManifestDataElement, expect the inner object type " +
                                "RevisionManifestRootDeclare or RevisionManifestObjectGroupReferences, " +
                                "but actual type value is " + header.get().type, null);
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
    public List<Byte> serializeToByteList() throws TikaException, IOException {
        List<Byte> byteList = new ArrayList<>();
        byteList.addAll(this.revisionManifest.serializeToByteList());

        if (this.revisionManifestRootDeclareList != null) {
            for (RevisionManifestRootDeclare revisionManifestRootDeclare : this.revisionManifestRootDeclareList) {
                byteList.addAll(revisionManifestRootDeclare.serializeToByteList());
            }
        }

        if (this.revisionManifestObjectGroupReferences != null) {
            for (RevisionManifestObjectGroupReferences revisionManifestObjectGroupReferences :
                    this.revisionManifestObjectGroupReferences) {
                byteList.addAll(revisionManifestObjectGroupReferences.serializeToByteList());
            }
        }

        return byteList;
    }
}
