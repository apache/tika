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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.BasicObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.BinaryItem;

public class DataHashObject extends StreamObject {
    /**
     * Gets or sets a binary item as specified in [MS-FSSHTTPB] section 2.2.1.3 that specifies a
     * value that is unique to the file data represented by this root node object.
     * The value of this item depends on the file chunking algorithm used, as specified in section 2.4.
     */
    public BinaryItem Data;

    /**
     * Initializes a new instance of the DataHashObject class.
     */
    public DataHashObject() {
        super(StreamObjectTypeHeaderStart.DataHashObject);
        this.Data = new BinaryItem();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DataHashObject that = (DataHashObject) o;
        return Objects.equals(Data, that.Data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Data);
    }

    @Override
    public String toString() {
        return "DataHashObject{" + "Data=" + Data + ", streamObjectHeaderEnd=" +
                streamObjectHeaderEnd + '}';
    }

    /**
     * Used to de-serialize the element.
     *
     * @param byteArray     A Byte array
     * @param currentIndex  Start position
     * @param lengthOfItems The length of the items
     */
    @Override
    protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex,
                                                 int lengthOfItems) {
        AtomicInteger index = new AtomicInteger(currentIndex.get());

        this.Data = BasicObject.parse(byteArray, index, BinaryItem.class);

        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "Signature",
                    "Stream Object over-parse error", null);
        }

        currentIndex.set(index.get());
    }

    /**
     * Used to convert the element into a byte List.
     *
     * @param byteList A Byte list
     * @return The number of elements
     */
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        int length = byteList.size();
        byteList.addAll(this.Data.SerializeToByteList());
        return byteList.size() - length;
    }
}
