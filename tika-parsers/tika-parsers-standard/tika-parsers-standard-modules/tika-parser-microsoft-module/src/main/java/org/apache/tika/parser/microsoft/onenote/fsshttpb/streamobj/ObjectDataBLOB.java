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
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.ByteUtil;

/**
 * Specifies an object data BLOB stream object - the opaque binary data of an object,
 * e.g. an embedded image or file. See MS-FSSHTTPB section 2.2.1.12.8.
 */
public class ObjectDataBLOB extends StreamObject {
    public BinaryItem data;

    public ObjectDataBLOB() {
        super(StreamObjectTypeHeaderStart.ObjectDataBLOB);
        this.data = new BinaryItem();
    }

    /**
     * @return the opaque binary data as a byte array, or null if not present.
     */
    public byte[] getData() {
        if (this.data == null || this.data.content == null) {
            return null;
        }
        return ByteUtil.toByteArray(this.data.content);
    }

    @Override
    protected void deserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex,
                                                 int lengthOfItems)
            throws TikaException, IOException {
        AtomicInteger index = new AtomicInteger(currentIndex.get());
        this.data = BasicObject.parse(byteArray, index, BinaryItem.class);

        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "ObjectDataBLOB",
                    "Stream object over-parse error", null);
        }

        currentIndex.set(index.get());
    }

    @Override
    protected int serializeItemsToByteList(List<Byte> byteList) throws IOException {
        int startPoint = byteList.size();
        if (this.data != null) {
            byteList.addAll(this.data.serializeToByteList());
        }
        return byteList.size() - startPoint;
    }
}
