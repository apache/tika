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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.exception.DataElementParseErrorException;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.BasicObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.Compact64bitInt;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.DataElementType;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ExGuid;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.SerialNumber;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.SequenceNumberGenerator;

public class DataElement extends StreamObject {

    /**
     * Data Element Data Type Mapping
     */
    private static final Map<DataElementType, Class> DATA_ELEMENT_DATA_TYPE_MAPPING;

    /**
     *  Initializes static members of the DataElement class
     */
    static {
        DATA_ELEMENT_DATA_TYPE_MAPPING = new HashMap<>();
        for (DataElementType value : DataElementType.values()) {
            String className = DataElement.class.getPackage().getName() + "." + value.name();

            try {
                DATA_ELEMENT_DATA_TYPE_MAPPING.put(value, Class.forName(className));
            } catch (ClassNotFoundException e) {
                // This is OK, we are not pulling over every single class
            }
        }
    }

    public ExGuid dataElementExGuid;
    public SerialNumber serialNumber;
    public DataElementType dataElementType;
    public DataElementData data;

    /**
     * Initializes a new instance of the DataElement class.
     *
     * @param type data
     *             element type
     *             *
     * @param data Specifies
     *             the data
     *             of the
     *             element .
     */


    public DataElement(DataElementType type, DataElementData data) {
        super(StreamObjectTypeHeaderStart.DataElement);
        if (!DATA_ELEMENT_DATA_TYPE_MAPPING.containsKey(type)) {
            throw new IllegalArgumentException("Invalid argument type value" + type.getIntVal());
        }

        this.dataElementType = type;
        this.data = data;
        this.dataElementExGuid =
                new ExGuid(SequenceNumberGenerator.GetCurrentSerialNumber(), UUID.randomUUID());
        this.serialNumber = new SerialNumber(UUID.randomUUID(),
                SequenceNumberGenerator.GetCurrentSerialNumber());
    }

    /**
     * Initializes a new instance of the DataElement class.
     */
    public DataElement() {
        super(StreamObjectTypeHeaderStart.DataElement);
    }

    /**
     * Used to get data.
     *
     * @return Data of
     * the element
     */
    public <T extends DataElementData> T getData(Class<T> clazz) throws TikaException {
        if (this.data.getClass().equals(clazz)) {
            return (T) this.data;
        } else {
            throw new TikaException(String.format(Locale.US,
                    "Unable to cast DataElementData to the type %s, its actual type is %s",
                    clazz.getName(), this.data.getClass().getName()));
        }
    }

    /**
     * Used to de-serialize the element.
     *
     * @param byteArray     A
     *                      Byte array
     * @param currentIndex  Start
     *                      position
     * @param lengthOfItems The
     *                      length of
     *                      the items
     */
    @Override
    protected void deserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex,
                                                 int lengthOfItems) throws TikaException {
        AtomicInteger index = new AtomicInteger(currentIndex.get());

        try {
            this.dataElementExGuid = BasicObject.parse(byteArray, index, ExGuid.class);
            this.serialNumber = BasicObject.parse(byteArray, index, SerialNumber.class);
            this.dataElementType = DataElementType.fromIntVal(
                    (int) BasicObject.parse(byteArray, index, Compact64bitInt.class)
                            .getDecodedValue());
        } catch (Exception e) {
            throw new DataElementParseErrorException(index.get(), e);
        }

        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new DataElementParseErrorException(currentIndex.get(),
                    "Failed to check the data element header length, whose value does not cover the " +
                            "dataElementExGUID, SerialNumber and DataElementType", null);
        }

        if (DATA_ELEMENT_DATA_TYPE_MAPPING.containsKey(this.dataElementType)) {
            try {
                this.data = (DataElementData) DATA_ELEMENT_DATA_TYPE_MAPPING.get(this.dataElementType)
                        .newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new TikaException("Could not instantiate a " + dataElementType, e);
            }

            try {
                index.addAndGet(
                        this.data.deserializeDataElementDataFromByteArray(byteArray, index.get()));
            } catch (Exception e) {
                throw new DataElementParseErrorException(index.get(), e);
            }
        } else {
            throw new DataElementParseErrorException(index.get(),
                    "Failed to create specific data element instance with the type " +
                            this.dataElementType, null);
        }

        currentIndex.set(index.get());
    }

    /**
     * Used to convert the element into a byte List.
     *
     * @param byteList A Byte list
     * @return The element length
     */
    @Override
    protected int serializeItemsToByteList(List<Byte> byteList) throws IOException, TikaException {
        int startIndex = byteList.size();
        byteList.addAll(this.dataElementExGuid.serializeToByteList());
        byteList.addAll(this.serialNumber.serializeToByteList());
        byteList.addAll(
                new Compact64bitInt(this.dataElementType.getIntVal()).serializeToByteList());

        int headerLength = byteList.size() - startIndex;
        byteList.addAll(this.data.serializeToByteList());

        return headerLength;
    }
}
