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

import org.apache.tika.parser.microsoft.fsshttpb.util.BitReader;
import org.apache.tika.parser.microsoft.fsshttpb.util.BitWriter;

/**
 * An 8-bit header for a compound object would indicate the end of a stream object
 */
public class StreamObjectHeaderEnd8bit extends StreamObjectHeaderEnd {
    /**
     * Initializes a new instance of the StreamObjectHeaderEnd8bit class with the specified type value.
     *
     * @param type Specify the integer value of the type.
     */
    public StreamObjectHeaderEnd8bit(int type) {

        this.type = StreamObjectTypeHeaderEnd.fromIntVal(type);
        if (this.type == null) {
            throw new RuntimeException(
                    String.format("The type value %s is not defined for the stream object end 8 bit header", type));
        }

    }

    /**
     * Initializes a new instance of the StreamObjectHeaderEnd8bit class, this is the default constructor.
     */
    public StreamObjectHeaderEnd8bit() {
    }

    /**
     * Initializes a new instance of the StreamObjectHeaderEnd8bit class with the specified type value.
     *
     * @param type Specify the value of the type.
     */
    public StreamObjectHeaderEnd8bit(StreamObjectTypeHeaderEnd type) {
        this(type.getIntVal());
    }

    /**
     * This method is used to convert the element of StreamObjectHeaderEnd8bit basic object into a byte List.
     *
     * @return Return the byte list which store the byte information of StreamObjectHeaderEnd8bit.
     */
    @Override
    public List<Byte> SerializeToByteList() {
        BitWriter bitFieldWriter = new BitWriter(1);
        bitFieldWriter.AppendInit32(0x1, 2);
        bitFieldWriter.AppendUInit32(this.type.getIntVal(), 6);
        return bitFieldWriter.getByteList();
    }

    /**
     * This method is used to get the byte value of the 8bit stream object header End.
     *
     * @return Return StreamObjectHeaderEnd8bit value represented by byte.
     */
    public byte ToByte() {
        List<Byte> bytes = this.SerializeToByteList();

        if (bytes.size() != 1) {
            throw new RuntimeException("The unexpected StreamObjectHeaderEnd8bit length");
        }

        return bytes.get(0);
    }

    /**
     * This method is used to deserialize the StreamObjectHeaderEnd8bit basic object from the specified byte array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return Return the length in byte of the StreamObjectHeaderEnd8bit basic object.
     */
    @Override
    protected int DoDeserializeFromByteArray(byte[] byteArray, int startIndex) {
        BitReader reader = new BitReader(byteArray, startIndex);
        int headerType = reader.ReadInt32(2);

        if (headerType != 0x1) {
            throw new RuntimeException(String.format(
                    "Failed to get the StreamObjectHeaderEnd8bit header type value, expect value %s, but actual value is %s",
                    0x1, headerType));
        }

        int typeValue = reader.ReadUInt32(6);
        this.type = StreamObjectTypeHeaderEnd.fromIntVal(typeValue);
        if (this.type == null) {
            throw new RuntimeException(
                    String.format("Failed to get the StreamObjectHeaderEnd8bit type value, the value %s is not defined",
                            typeValue));
        }

        return 1;
    }
}