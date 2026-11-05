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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.ByteUtil;

public class BinaryItem extends BasicObject {
    private static final byte[] EMPTY = new byte[0];

    public Compact64bitInt length;
    /**
     * The binary payload. Object data BLOBs (embedded images and files) and every object's
     * property set live here, so it is held as a primitive array: a boxed list cost a
     * Byte reference per payload byte and dominated OneNote parse time.
     */
    public byte[] content;

    /**
     * Initializes a new instance of the BinaryItem class.
     */
    public BinaryItem() {
        this.length = new Compact64bitInt();
        this.content = EMPTY;
    }

    /**
     * Initializes a new instance of the BinaryItem class with the specified content.
     *
     * @param content Specify the binary content.
     */
    public BinaryItem(Collection<Byte> content) {
        this(ByteUtil.toByteArray(new ArrayList<>(content)));
    }

    /**
     * Initializes a new instance of the BinaryItem class with the specified content. The
     * array is used as-is, not copied.
     *
     * @param content Specify the binary content.
     */
    public BinaryItem(byte[] content) {
        this.length = new Compact64bitInt();
        this.content = content;
        this.length.setDecodedValue(content.length);
    }

    /**
     * This method is used to convert the element of BinaryItem basic object into a byte List.
     *
     * @return Return the byte list which store the byte information of BinaryItem.
     */
    @Override
    public List<Byte> serializeToByteList() throws IOException {
        this.length.setDecodedValue(this.content.length);

        List<Byte> result = new ArrayList<>();
        result.addAll(this.length.serializeToByteList());
        ByteUtil.appendByteArrayToListOfByte(result, this.content);

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
    protected int doDeserializeFromByteArray(byte[] byteArray, int startIndex)
            throws TikaException, IOException {
        AtomicInteger index = new AtomicInteger(startIndex);

        this.length = BasicObject.parse(byteArray, index, Compact64bitInt.class);

        long contentLength = this.length.getDecodedValue();
        int contentStart = index.get();
        if (contentLength < 0 || contentLength > byteArray.length - contentStart) {
            throw new TikaException("BinaryItem length " + contentLength + " at offset " +
                    contentStart + " exceeds the " + (byteArray.length - contentStart) +
                    " bytes remaining");
        }
        this.content = Arrays.copyOfRange(byteArray, contentStart,
                contentStart + (int) contentLength);
        index.addAndGet((int) contentLength);

        return index.get() - startIndex;
    }
}
