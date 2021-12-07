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
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.BinaryItem;

/**
 * Signature Object
 */
public class SignatureObject extends StreamObject {
    /**
     * Gets or sets a binary item as specified in [MS-FSSHTTPB] section 2.2.1.3 that specifies a
     * value that is unique to the file data represented by this root node object.
     * The value of this item depends on the file chunking algorithm used, as specified in section 2.4.
     */
    public BinaryItem signatureData;

    /**
     * Initializes a new instance of the SignatureObject class.
     */
    public SignatureObject() {
        super(StreamObjectTypeHeaderStart.SignatureObject);
        this.signatureData = new BinaryItem();
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

        this.signatureData = BasicObject.parse(byteArray, index, BinaryItem.class);

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
    protected int serializeItemsToByteList(List<Byte> byteList) throws IOException {
        int length = byteList.size();
        byteList.addAll(this.signatureData.serializeToByteList());
        return byteList.size() - length;
    }
}
