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

import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitReader;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitWriter;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.ByteUtil;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.LittleEndianBitConverter;

/**
 * An 16-bit header for a compound object would indicate the end of a stream object
 */
public class StreamObjectHeaderEnd16bit extends StreamObjectHeaderEnd {
    /**
     * Initializes a new instance of the StreamObjectHeaderEnd16bit class with the specified type value.
     *
     * @param type Specify the integer value of the type.
     */
    public StreamObjectHeaderEnd16bit(int type) {
        this.type = StreamObjectTypeHeaderEnd.fromIntVal(type);
        if (this.type == null) {
            throw new RuntimeException(String.format(Locale.US,
                    "The type value RuntimeException is not defined for the stream object end 16-bit header",
                    type));
        }

    }

    /**
     * Initializes a new instance of the StreamObjectHeaderEnd16bit class with the specified type value.
     *
     * @param headerType Specify the value of the type.
     */
    public StreamObjectHeaderEnd16bit(StreamObjectTypeHeaderEnd headerType) {
        this.type = headerType;
    }

    /**
     * Initializes a new instance of the StreamObjectHeaderEnd16bit class, this is the default constructor.
     */
    public StreamObjectHeaderEnd16bit() {
    }

    /**
     * This method is used to convert the element of StreamObjectHeaderEnd16bit basic object into a byte List.
     *
     * @return Return the byte list which store the byte information of StreamObjectHeaderEnd16bit.
     */
    @Override
    public List<Byte> serializeToByteList() {
        BitWriter bitFieldWriter = new BitWriter(2);
        bitFieldWriter.appendInit32(0x3, 2);
        bitFieldWriter.appendUInit32(this.type.getIntVal(), 14);
        return bitFieldWriter.getByteList();
    }

    /**
     * This method is used to get the byte value of the 16-bit stream object header End.
     *
     * @return Return StreamObjectHeaderEnd8bit value represented by unsigned short integer.
     */
    public short toUint16() {
        List<Byte> bytes = this.serializeToByteList();
        return LittleEndianBitConverter.ToUInt16(ByteUtil.toByteArray(bytes), 0);
    }

    /**
     * This method is used to deserialize the StreamObjectHeaderEnd16bit basic object from the
     * specified byte array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return Return the length in byte of the StreamObjectHeaderEnd16bit basic object.
     */
    @Override
    protected int doDeserializeFromByteArray(byte[] byteArray, int startIndex) {
        BitReader reader = new BitReader(byteArray, startIndex);
        int headerType = reader.readInt32(2);

        if (headerType != 0x3) {
            throw new RuntimeException(String.format(Locale.US,
                    "Failed to get the StreamObjectHeaderEnd16bit header type value, expect value %d, " +
                            "but actual value is %s", 0x3, headerType));
        }

        int typeValue = reader.readUInt32(14);
        this.type = StreamObjectTypeHeaderEnd.fromIntVal(typeValue);
        if (this.type == null) {
            throw new RuntimeException(String.format(Locale.US,
                    "Failed to get the StreamObjectHeaderEnd16bit type value, the value %d is not defined",
                    typeValue));
        }

        return 2;
    }
}
