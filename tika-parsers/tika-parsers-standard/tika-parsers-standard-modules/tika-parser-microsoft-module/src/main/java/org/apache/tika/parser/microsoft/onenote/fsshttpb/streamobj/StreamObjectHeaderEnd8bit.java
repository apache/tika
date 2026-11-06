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
import java.util.Locale;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitReader;

/**
 * An 8-bit header for a compound object would indicate the end of a stream object
 */
public class StreamObjectHeaderEnd8bit extends StreamObjectHeaderEnd {

    /**
     * Initializes a new instance of the StreamObjectHeaderEnd8bit class, this is the default constructor.
     */
    public StreamObjectHeaderEnd8bit() {
    }

    /**
     * This method is used to deserialize the StreamObjectHeaderEnd8bit basic object from the
     * specified byte array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return Return the length in byte of the StreamObjectHeaderEnd8bit basic object.
     */
    @Override
    protected int doDeserializeFromByteArray(byte[] byteArray, int startIndex)
            throws IOException, TikaException {
        BitReader reader = new BitReader(byteArray, startIndex);
        int headerType = reader.readInt32(2);

        if (headerType != 0x1) {
            throw new TikaException(String.format(Locale.US,
                    "Failed to get the StreamObjectHeaderEnd8bit header type value, " +
                            "expect value %s, but actual value is %s", 0x1, headerType));
        }

        int typeValue = reader.readUInt32(6);
        this.type = StreamObjectTypeHeaderEnd.fromIntVal(typeValue);
        if (this.type == null) {
            throw new TikaException(String.format(Locale.US,
                    "Failed to get the StreamObjectHeaderEnd8bit type value, the value %s is not defined",
                    typeValue));
        }

        return 1;
    }
}
