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
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.BasicObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.Compact64bitInt;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitReader;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitWriter;

/**
 * An 32-bit header for a compound object would indicate the start of a stream object
 */
public class StreamObjectHeaderStart32bit extends StreamObjectHeaderStart {
    /**
     * Gets or sets an optional compact uint64 that specifies the length in bytes for additional data (if any).
     * This field MUST be specified if the Length field contains 32767 and MUST NOT be specified if the Length field
     * contains any other value than 32767.
     */
    public Compact64bitInt largeLength;

    /**
     * Initializes a new instance of the StreamObjectHeaderStart32bit class with specified type and length.
     *
     * @param type   Specify the type of the StreamObjectHeaderStart32bit.
     * @param length Specify the length of the StreamObjectHeaderStart32bit.
     */
    public StreamObjectHeaderStart32bit(StreamObjectTypeHeaderStart type, int length) {
        this.headerType = StreamObjectHeaderStart.StreamObjectHeaderStart32bit;
        this.type = type;
        this.compound = StreamObject.getCompoundTypes().contains(this.type) ? 1 : 0;

        if (length >= 32767) {
            this.length = 32767;
            this.largeLength = new Compact64bitInt(length);
        } else {
            this.length = length;
            this.largeLength = null;
        }
    }

    /**
     * Initializes a new instance of the StreamObjectHeaderStart32bit class, this is the default constructor.
     */
    public StreamObjectHeaderStart32bit() {
    }

    /**
     * Initializes a new instance of the StreamObjectHeaderStart32bit class with specified type.
     *
     * @param streamObjectTypeHeaderStart Specify the type of the StreamObjectHeaderStart32bit.
     */
    public StreamObjectHeaderStart32bit(StreamObjectTypeHeaderStart streamObjectTypeHeaderStart) {
        this.type = streamObjectTypeHeaderStart;
    }

    /**
     * This method is used to convert the element of StreamObjectHeaderStart32bit basic object into a byte List.
     *
     * @return Return the byte list which store the byte information of StreamObjectHeaderStart32bit.
     */
    @Override
    public List<Byte> serializeToByteList() {
        BitWriter bitFieldWriter = new BitWriter(4);
        bitFieldWriter.appendInit32(this.headerType, 2);
        bitFieldWriter.appendInit32(this.compound, 1);
        bitFieldWriter.appendUInit32(this.type.getIntVal(), 14);
        bitFieldWriter.appendInit32(this.length, 15);

        List<Byte> listByte = bitFieldWriter.getByteList();

        if (this.largeLength != null) {
            listByte.addAll(this.largeLength.serializeToByteList());
        }

        return listByte;
    }

    /**
     * This method is used to deserialize the StreamObjectHeaderStart32bit basic object
     * from the specified byte array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return Return the length in byte of the StreamObjectHeaderStart32bit basic object.
     */
    @Override
    protected int doDeserializeFromByteArray(byte[] byteArray, int startIndex) {
        BitReader bitReader = new BitReader(byteArray, startIndex);
        this.headerType = bitReader.readInt32(2);
        if (this.headerType != StreamObjectHeaderStart.StreamObjectHeaderStart32bit) {
            throw new RuntimeException(String.format(Locale.US,
                    "Failed to get the StreamObjectHeaderStart32bit header type value, expect " +
                            "value %s, but actual value is %s",
                    StreamObjectHeaderStart.StreamObjectHeaderStart32bit, this.headerType));
        }

        this.compound = bitReader.readInt32(1);
        int typeValue = bitReader.readInt32(14);
        this.type = StreamObjectTypeHeaderStart.fromIntVal(typeValue);
        if (type == null) {
            throw new RuntimeException(String.format(Locale.US,
                    "Failed to get the StreamObjectHeaderStart32bit type value, the value %s is not defined",
                    typeValue));
        }

        if (StreamObject.getCompoundTypes().contains(this.type) && this.compound != 1) {
            throw new RuntimeException(String.format(Locale.US,
                    "Failed to parse the StreamObjectHeaderStart32bit header. If the type value is %s " +
                            "then the compound value should 1, but actual value is 0", typeValue));
        }

        this.length = bitReader.readInt32(15);

        AtomicInteger index = new AtomicInteger(startIndex);
        index.addAndGet(4);

        if (this.length == 32767) {
            this.largeLength = BasicObject.parse(byteArray, index, Compact64bitInt.class);
        }

        return index.get() - startIndex;
    }
}
