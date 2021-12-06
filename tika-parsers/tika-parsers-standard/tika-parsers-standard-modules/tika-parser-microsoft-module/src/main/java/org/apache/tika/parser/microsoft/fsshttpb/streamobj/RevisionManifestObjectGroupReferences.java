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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.BasicObject;
import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.ExGuid;

/**
 * Specifies a revision manifest object group references, each followed by object group extended GUIDs
 */
public class RevisionManifestObjectGroupReferences extends StreamObject {
    /**
     * Initializes a new instance of the RevisionManifestObjectGroupReferences class.
     */
    public RevisionManifestObjectGroupReferences() {
        super(StreamObjectTypeHeaderStart.RevisionManifestObjectGroupReferences);
    }

    /**
     * Initializes a new instance of the RevisionManifestObjectGroupReferences class.
     *
     * @param objectGroupExtendedGUID Extended GUID
     */
    public RevisionManifestObjectGroupReferences(ExGuid objectGroupExtendedGUID) {
        super(StreamObjectTypeHeaderStart.RevisionManifestObjectGroupReferences);
        this.ObjectGroupExtendedGUID = objectGroupExtendedGUID;
    }

    /**
     * Gets or sets an extended GUID that specifies the object group for each Revision Manifest Object Group References.
     */
    public ExGuid ObjectGroupExtendedGUID;

    /**
     * Used to de-serialize the element.
     *
     * @param byteArray     A Byte array
     * @param currentIndex  Start position
     * @param lengthOfItems The length of the items
     */
    @Override
    protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems) {
        AtomicInteger index = new AtomicInteger(currentIndex.get());
        this.ObjectGroupExtendedGUID = BasicObject.parse(byteArray, index, ExGuid.class);
        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "RevisionManifestObjectGroupReferences",
                    "Stream object over-parse error", null);
        }

        currentIndex.set(index.get());
    }

    /**
     * Used to convert the element into a byte List.
     *
     * @param byteList A Byte list
     * @return The number of elements actually contained in the list.
     */
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        List<Byte> tmpList = this.ObjectGroupExtendedGUID.SerializeToByteList();
        byteList.addAll(tmpList);
        return tmpList.size();
    }
}