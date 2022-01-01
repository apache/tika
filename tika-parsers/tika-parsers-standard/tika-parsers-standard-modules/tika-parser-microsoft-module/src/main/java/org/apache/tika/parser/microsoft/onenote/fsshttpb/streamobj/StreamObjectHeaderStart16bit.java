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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitReader;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitWriter;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.ByteUtil;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.LittleEndianBitConverter;

/**
 * An 16-bit header for a compound object would indicate the start of a stream object
 */
public class StreamObjectHeaderStart16bit extends StreamObjectHeaderStart {
    /**
     * Initializes a new instance of the StreamObjectHeaderStart16bit class with specified type and length.
     *
     * @param type   Specify the type of the StreamObjectHeaderStart16bit.
     * @param length Specify the length of the StreamObjectHeaderStart16bit.
     */
    public StreamObjectHeaderStart16bit(StreamObjectTypeHeaderStart type, int length)
            throws TikaException {
        if (this.length > 127) {
            throw new TikaException(
                    "Field Length - 16-bit Stream Object Header Start, Length (7-bits): A 7-bit " +
                            "unsigned integer that specifies the length in bytes for additional data " +
                            "(if any). If the length is more than 127 bytes, a 32-bit stream object header " +
                            "start MUST be used.");
        }

        this.headerType = 0x0;
        this.type = type;
        this.compound = StreamObject.getCompoundTypes().contains(this.type) ? 1 : 0;
        this.length = length;
    }

    /**
     * Initializes a new instance of the StreamObjectHeaderStart16bit class with specified type.
     *
     * @param type Specify the type of the StreamObjectHeaderStart16bit.
     */
    public StreamObjectHeaderStart16bit(StreamObjectTypeHeaderStart type) throws TikaException {
        this(type, 0);
    }

    /**
     * Initializes a new instance of the StreamObjectHeaderStart16bit class, this is the default constructor.
     */
    public StreamObjectHeaderStart16bit() {
    }

    /**
     * This method is used to convert the element of StreamObjectHeaderStart16bit basic object into a byte List.
     *
     * @return Return the byte list which store the byte information of StreamObjectHeaderStart16bit.
     */
    @Override
    public List<Byte> serializeToByteList() throws IOException {
        BitWriter bitField = new BitWriter(2);
        bitField.appendInit32(this.headerType, 2);
        bitField.appendInit32(this.compound, 1);
        bitField.appendUInit32(this.type.getIntVal(), 6);
        bitField.appendInit32(this.length, 7);
        List<Byte> result = new ArrayList<>();
        ByteUtil.appendByteArrayToListOfByte(result, bitField.getBytes());
        return result;
    }

    /**
     * This method is used to get the Uint16 value of the 16bit stream object header.
     *
     * @return Return the ushort value.
     */
    public short toUint16() throws IOException {
        return LittleEndianBitConverter.toUInt16(ByteUtil.toByteArray(this.serializeToByteList()),
                0);
    }

    /**
     * This method is used to deserialize the StreamObjectHeaderStart16bit basic object from the
     * specified byte array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return Return the length in byte of the StreamObjectHeaderStart16bit basic object.
     */
    @Override
    protected int doDeserializeFromByteArray(byte[] byteArray, int startIndex)
            throws IOException, TikaException {
        BitReader bitReader = new BitReader(byteArray, startIndex);
        this.headerType = bitReader.readInt32(2);
        if (this.headerType != StreamObjectHeaderStart.STREAM_OBJECT_HEADER_START_16_BIT) {
            throw new TikaException(String.format(Locale.US,
                    "Failed to get the StreamObjectHeaderStart16bit header type value, expect value %s, " +
                            "but actual value is %s", STREAM_OBJECT_HEADER_START_16_BIT,
                    this.headerType));
        }

        this.compound = bitReader.readInt32(1);
        int typeValue = bitReader.readInt32(6);
        this.type = StreamObjectTypeHeaderStart.fromIntVal(typeValue);
        if (type == null) {
            throw new TikaException(String.format(Locale.US,
                    "Failed to get the StreamObjectHeaderStart16bit type value, the value %s is not defined",
                    typeValue));
        }

        if (StreamObject.getCompoundTypes().contains(type) && this.compound != 1) {
            throw new TikaException(String.format(Locale.US,
                    "Failed to parse the StreamObjectHeaderStart16bit header. If the type value is %s then " +
                            "the compound value should 1, but actual value is 0", typeValue));
        }

        this.length = bitReader.readInt32(7);
        if (this.length > 127) {
            throw new TikaException(
                    "16-bit Stream Object Header Start, Length (7-bits): A 7-bit unsigned integer that " +
                            "specifies the length in bytes for additional data (if any). If the length is more than " +
                            "127 bytes, a 32-bit stream object header start MUST be used.");
        }

        return 2;
    }
}
