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

/**
 * Specifies an object group metadata
 */
public class ObjectGroupMetadata extends StreamObject {
    /**
     * Gets or sets a compact unsigned 64-bit integer that specifies the expected change frequency of the object.
     * This value MUST be:
     * 0, if the change frequency is not known.
     * 1, if the object is known to change frequently.
     * 2, if the object is known to change infrequently.
     * 3, if the object is known to change independently of any other objects.
     */
    public Compact64bitInt ObjectChangeFrequency;

    /**
     * Initializes a new instance of the ObjectGroupMetadata class.
     */
    public ObjectGroupMetadata() {
        super(StreamObjectTypeHeaderStart.ObjectGroupMetadata);
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
        this.ObjectChangeFrequency = BasicObject.parse(byteArray, index, Compact64bitInt.class);

        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "ObjectGroupMetadata",
                    "Stream object over-parse error", null);
        }

        currentIndex.set(index.get());
    }

    /**
     * Used to convert the element into a byte List
     *
     * @param byteList A Byte list
     * @return The number of elements actually contained in the list
     */
    @Override
    protected int serializeItemsToByteList(List<Byte> byteList) throws IOException {
        List<Byte> tmpList = this.ObjectChangeFrequency.serializeToByteList();
        byteList.addAll(tmpList);
        return tmpList.size();
    }
}
