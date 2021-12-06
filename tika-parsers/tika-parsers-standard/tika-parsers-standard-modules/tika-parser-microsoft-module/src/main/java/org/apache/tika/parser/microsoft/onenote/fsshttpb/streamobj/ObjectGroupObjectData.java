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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.BasicObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.BinaryItem;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.CellIDArray;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ExGUIDArray;

public class ObjectGroupObjectData extends StreamObject {
    public ExGUIDArray ObjectExGUIDArray;
    public CellIDArray cellIDArray;
    public BinaryItem Data;

    /**
     * Initializes a new instance of the ObjectGroupObjectData class.
     */
    public ObjectGroupObjectData() {
        super(StreamObjectTypeHeaderStart.ObjectGroupObjectData);
        this.ObjectExGUIDArray = new ExGUIDArray();
        this.cellIDArray = new CellIDArray();
        this.Data = new BinaryItem();
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
                                                 int lengthOfItems) {
        AtomicInteger index = new AtomicInteger(currentIndex.get());
        this.ObjectExGUIDArray = BasicObject.parse(byteArray, index, ExGUIDArray.class);
        this.cellIDArray = BasicObject.parse(byteArray, index, CellIDArray.class);
        this.Data = BasicObject.parse(byteArray, index, BinaryItem.class);

        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "ObjectGroupObjectData",
                    "Stream object over-parse error", null);
        }

        currentIndex.set(index.get());
    }

    /**
     * Used to convert the element into a byte List
     *
     * @param byteList A Byte list
     * @return The number of the element
     */
    @Override
    protected int serializeItemsToByteList(List<Byte> byteList) {
        int itemsIndex = byteList.size();
        byteList.addAll(this.ObjectExGUIDArray.serializeToByteList());
        byteList.addAll(this.cellIDArray.serializeToByteList());
        byteList.addAll(this.Data.serializeToByteList());
        return byteList.size() - itemsIndex;
    }
}
