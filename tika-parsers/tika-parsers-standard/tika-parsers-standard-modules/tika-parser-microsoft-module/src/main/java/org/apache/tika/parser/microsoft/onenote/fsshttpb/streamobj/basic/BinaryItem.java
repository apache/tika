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

package org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class BinaryItem extends BasicObject {
    public Compact64bitInt Length;
    public List<Byte> Content;

    /**
     * Initializes a new instance of the BinaryItem class.
     */
    public BinaryItem() {
        this.Length = new Compact64bitInt();
        this.Content = new ArrayList<>();
    }

    /**
     * Initializes a new instance of the BinaryItem class with the specified content.
     *
     * @param content Specify the binary content.
     */
    public BinaryItem(Collection<Byte> content) {
        this.Length = new Compact64bitInt();
        this.Content = new ArrayList<>();
        this.Content.addAll(content);
        this.Length.setDecodedValue(this.Content.size());
    }

    /**
     * This method is used to convert the element of BinaryItem basic object into a byte List.
     *
     * @return Return the byte list which store the byte information of BinaryItem.
     */
    @Override
    public List<Byte> SerializeToByteList() {
        this.Length.setDecodedValue(this.Content.size());

        List<Byte> result = new ArrayList<>();
        result.addAll(this.Length.SerializeToByteList());
        result.addAll(this.Content);

        return result;
    }

    /**
     * This method is used to de-serialize the BinaryItem basic object from the specified byte array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return Return the length in byte of the BinaryItem basic object.
     */
    @Override
    protected int DoDeserializeFromByteArray(byte[] byteArray, int startIndex) {
        AtomicInteger index = new AtomicInteger(startIndex);

        this.Length = BasicObject.parse(byteArray, index, Compact64bitInt.class);

        this.Content.clear();
        for (long i = 0; i < this.Length.getDecodedValue(); i++) {
            this.Content.add(byteArray[index.getAndIncrement()]);
        }

        return index.get() - startIndex;
    }
}
