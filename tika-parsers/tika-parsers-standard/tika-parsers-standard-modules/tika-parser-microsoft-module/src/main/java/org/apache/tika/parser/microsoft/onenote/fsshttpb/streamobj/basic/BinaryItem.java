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

/**
 * A length-prefixed run of bytes.
 *
 * <p>The content is read in place, never copied on the way in: a deserialized item is a range
 * of the buffer it was parsed from, and an item built from an array keeps that array. Neither
 * may be modified while the item is in use. {@link #getContent()} swaps a range for an array of
 * its own on the first call; {@link #copyContent()} always returns a fresh array.
 *
 * <p>Construction, {@link #setContent} and serialization keep {@link #length} equal to the
 * content size, which is 0 for content set to null.
 */
public class BinaryItem extends BasicObject {
    private static final byte[] EMPTY = new byte[0];

    public Compact64bitInt length;

    /**
     * The payload is {@code source[offset, offset + count)}. Deserializing keeps a view of the
     * parsed buffer, so items that are never read, or only measured, cost no copy; object data
     * BLOBs hold whole embedded files. {@code source} is null when the content was explicitly
     * set to null.
     */
    private byte[] source;
    private int offset;
    private int count;

    /**
     * Initializes a new instance of the BinaryItem class.
     */
    public BinaryItem() {
        this.length = new Compact64bitInt();
        this.source = EMPTY;
    }

    /**
     * Initializes a new instance of the BinaryItem class with the specified content.
     *
     * @param content Specify the binary content.
     */
    public BinaryItem(Collection<Byte> content) {
        this(ByteUtil.toByteArray(content instanceof List<Byte> list ? list : new ArrayList<>(content)));
    }

    /**
     * Initializes a new instance of the BinaryItem class with the specified content. The
     * array is used as-is, not copied.
     *
     * @param content Specify the binary content, or null for none.
     */
    public BinaryItem(byte[] content) {
        this.length = new Compact64bitInt();
        setContent(content);
    }

    /**
     * @return the content as an array of exactly its length, or null if the content was set
     * to null. A deserialized item copies its range out of the parsed buffer on the first
     * call and returns that same array afterwards.
     */
    public byte[] getContent() {
        if (source != null && (offset != 0 || count != source.length)) {
            source = Arrays.copyOfRange(source, offset, offset + count);
            offset = 0;
        }
        return source;
    }

    /**
     * @return a new copy of the content, or null if the content was set to null. Unlike
     * {@link #getContent()}, the item does not keep the copy.
     */
    public byte[] copyContent() {
        return source == null ? null : Arrays.copyOfRange(source, offset, offset + count);
    }

    /**
     * @return the content length in bytes, 0 if the content was set to null
     */
    public int getContentLength() {
        return count;
    }

    /**
     * Replaces the content. The array is used as-is, not copied.
     */
    public void setContent(byte[] content) {
        this.source = content;
        this.offset = 0;
        this.count = content == null ? 0 : content.length;
        this.length.setDecodedValue(this.count);
    }

    /**
     * This method is used to convert the element of BinaryItem basic object into a byte List.
     *
     * @return Return the byte list which store the byte information of BinaryItem.
     */
    @Override
    public List<Byte> serializeToByteList() throws IOException {
        List<Byte> length = this.length.setDecodedValue(count).serializeToByteList();
        List<Byte> result = new ArrayList<>(length.size() + count);
        result.addAll(length);
        for (int i = offset; i < offset + count; i++) {
            result.add(source[i]);
        }
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
        this.source = byteArray;
        this.offset = contentStart;
        this.count = (int) contentLength;
        index.addAndGet((int) contentLength);

        return index.get() - startIndex;
    }
}
