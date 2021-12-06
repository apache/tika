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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class DataElementPackage extends StreamObject {

    public List<DataElement> DataElements = new ArrayList<>();
    public byte reserved;

    /**
     * Initializes a new instance of the DataElementHash class.
     */
    public DataElementPackage() {
        super(StreamObjectTypeHeaderStart.DataElementPackage);
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
        if (lengthOfItems != 1) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "DataElementPackage",
                    "Stream object over-parse error", null);
        }

        reserved = byteArray[currentIndex.getAndIncrement()];

        this.DataElements = new ArrayList<>();
        AtomicReference<DataElement> dataElement = new AtomicReference<>();
        while (StreamObject.tryGetCurrent(byteArray, currentIndex, dataElement,
                DataElement.class)) {
            this.DataElements.add(dataElement.get());
        }
    }

    /**
     * Used to convert the element into a byte List
     *
     * @param byteList A Byte list
     * @return The number of elements actually contained in the list
     */
    @Override
    protected int serializeItemsToByteList(List<Byte> byteList) {
        // Add the reserved byte
        byteList.add((byte) 0);
        for (DataElement dataElement : DataElements) {
            byteList.addAll(dataElement.serializeToByteList());
        }
        return 1;
    }
}
