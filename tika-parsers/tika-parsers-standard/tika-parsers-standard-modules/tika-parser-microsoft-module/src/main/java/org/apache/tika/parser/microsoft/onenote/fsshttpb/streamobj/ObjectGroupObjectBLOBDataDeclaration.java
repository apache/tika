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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.BasicObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.Compact64bitInt;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ExGuid;

/**
 * object data BLOB declaration
 */
public class ObjectGroupObjectBLOBDataDeclaration extends StreamObject {
    public ExGuid objectExGUID;
    public ExGuid objectDataBLOBExGUID;
    public Compact64bitInt objectPartitionID;
    /**
     * Gets or sets a compact unsigned 64-bit integer that specifies the size in bytes of the
     * object.opaque binary data  for the declared object.
     * This MUST match the size of the binary item in the corresponding object data BLOB
     * referenced by the Object Data BLOB reference for this object.
     */
    public Compact64bitInt objectDataSize;
    public Compact64bitInt objectReferencesCount;
    public Compact64bitInt cellReferencesCount;

    /**
     * Initializes a new instance of the ObjectGroupObjectBLOBDataDeclaration class.
     */
    public ObjectGroupObjectBLOBDataDeclaration() {
        super(StreamObjectTypeHeaderStart.ObjectGroupObjectBLOBDataDeclaration);
        this.objectExGUID = new ExGuid();
        this.objectDataBLOBExGUID = new ExGuid();
        this.objectPartitionID = new Compact64bitInt();
        this.objectDataSize = new Compact64bitInt();
        this.objectReferencesCount = new Compact64bitInt();
        this.cellReferencesCount = new Compact64bitInt();
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
        AtomicInteger index = new AtomicInteger(currentIndex.get());

        this.objectExGUID = BasicObject.parse(byteArray, index, ExGuid.class);
        this.objectDataBLOBExGUID = BasicObject.parse(byteArray, index, ExGuid.class);
        this.objectPartitionID = BasicObject.parse(byteArray, index, Compact64bitInt.class);
        this.objectReferencesCount = BasicObject.parse(byteArray, index, Compact64bitInt.class);
        this.cellReferencesCount = BasicObject.parse(byteArray, index, Compact64bitInt.class);

        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(),
                    "ObjectGroupObjectBLOBDataDeclaration", "Stream object over-parse error", null);
        }

        currentIndex.set(index.get());
    }

    /**
     * Used to convert the element into a byte List.
     *
     * @param byteList A Byte list
     * @return The number of the element
     */
    @Override
    protected int serializeItemsToByteList(List<Byte> byteList) throws IOException {
        int itemsIndex = byteList.size();
        byteList.addAll(this.objectExGUID.serializeToByteList());
        byteList.addAll(this.objectDataBLOBExGUID.serializeToByteList());
        byteList.addAll(this.objectPartitionID.serializeToByteList());
        byteList.addAll(this.objectDataSize.serializeToByteList());
        byteList.addAll(this.objectReferencesCount.serializeToByteList());
        byteList.addAll(this.cellReferencesCount.serializeToByteList());
        return byteList.size() - itemsIndex;
    }
}
