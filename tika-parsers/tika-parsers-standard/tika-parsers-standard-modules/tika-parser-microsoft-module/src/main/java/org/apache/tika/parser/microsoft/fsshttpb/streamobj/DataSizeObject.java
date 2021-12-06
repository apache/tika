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

package org.apache.tika.parser.microsoft.fsshttpb.streamobj;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.parser.microsoft.fsshttpb.util.ByteUtil;
import org.apache.tika.parser.microsoft.fsshttpb.util.LittleEndianBitConverter;

/**
 * Data Size Object
 */
public class DataSizeObject extends StreamObject {
    /**
     * Initializes a new instance of the DataSizeObject class.
     */
    public DataSizeObject() {
        super(StreamObjectTypeHeaderStart.DataSizeObject);
    }

    public long DataSize;

    /**
     * Used to de-serialize the element.
     *
     * @param byteArray     A Byte array
     * @param currentIndex  Start position
     * @param lengthOfItems The length of the items
     */
    @Override
    protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems) {
        if (lengthOfItems != 8) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "DataSize", "Stream Object over-parse error",
                    null);
        }

        this.DataSize = LittleEndianBitConverter.ToUInt64(byteArray, currentIndex.get());
        currentIndex.addAndGet(8);
    }

    /**
     * Used to convert the element into a byte List.
     *
     * @param byteList A Byte list
     * @return A constant value 8
     */
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        ByteUtil.appendByteArrayToListOfByte(byteList, LittleEndianBitConverter.GetBytes(this.DataSize));
        return 8;
    }
}