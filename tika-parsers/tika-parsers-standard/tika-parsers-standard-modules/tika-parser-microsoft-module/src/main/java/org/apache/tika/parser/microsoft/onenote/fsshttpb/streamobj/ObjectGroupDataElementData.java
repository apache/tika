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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tika.exception.TikaException;

public class ObjectGroupDataElementData extends DataElementData {
    public org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.DataElementHash
            dataElementHash;
    public ObjectGroupDeclarations objectGroupDeclarations;
    public ObjectGroupMetadataDeclarations objectMetadataDeclaration;
    public ObjectGroupData objectGroupData;

    /**
     * Initializes a new instance of the ObjectGroupDataElementData class.
     */
    public ObjectGroupDataElementData() {
        this.objectGroupDeclarations = new ObjectGroupDeclarations();

        // The ObjectMetadataDeclaration is only present for MOSS2013, so leave null for default value.
        this.objectMetadataDeclaration = null;

        // The DataElementHash is only present for MOSS2013, so leave null for default value.
        this.dataElementHash = null;
        this.objectGroupData = new ObjectGroupData();
    }

    /**
     * Used to return the length of this element.
     *
     * @param byteArray  A Byte array
     * @param startIndex Start position
     * @return The length of the element
     */
    @Override
    public int deserializeDataElementDataFromByteArray(byte[] byteArray, int startIndex)
            throws TikaException, IOException {
        AtomicInteger index = new AtomicInteger(startIndex);

        AtomicReference<DataElementHash> dataElementHash = new AtomicReference<>();
        if (StreamObject.tryGetCurrent(byteArray, index, dataElementHash, DataElementHash.class)) {
            this.dataElementHash = dataElementHash.get();
        }

        this.objectGroupDeclarations =
                StreamObject.getCurrent(byteArray, index, ObjectGroupDeclarations.class);

        AtomicReference<ObjectGroupMetadataDeclarations> objectMetadataDeclaration =
                new AtomicReference<>(new ObjectGroupMetadataDeclarations());
        if (StreamObject.tryGetCurrent(byteArray, index, objectMetadataDeclaration,
                ObjectGroupMetadataDeclarations.class)) {
            this.objectMetadataDeclaration = objectMetadataDeclaration.get();
        }

        this.objectGroupData = StreamObject.getCurrent(byteArray, index, ObjectGroupData.class);

        return index.get() - startIndex;
    }
}
