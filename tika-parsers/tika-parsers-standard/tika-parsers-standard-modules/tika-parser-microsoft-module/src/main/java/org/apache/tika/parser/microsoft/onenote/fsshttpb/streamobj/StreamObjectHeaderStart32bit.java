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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.BasicObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.Compact64bitInt;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitReader;

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
     * Initializes a new instance of the StreamObjectHeaderStart32bit class, this is the default constructor.
     */
    public StreamObjectHeaderStart32bit() {
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
    protected int doDeserializeFromByteArray(byte[] byteArray, int startIndex)
            throws IOException, TikaException {
        BitReader bitReader = new BitReader(byteArray, startIndex);
        this.headerType = bitReader.readInt32(2);
        if (this.headerType != StreamObjectHeaderStart.STREAM_OBJECT_HEADER_START_32_BIT) {
            throw new TikaException(String.format(Locale.US,
                    "Failed to get the StreamObjectHeaderStart32bit header type value, expect " +
                            "value %s, but actual value is %s",
                    StreamObjectHeaderStart.STREAM_OBJECT_HEADER_START_32_BIT, this.headerType));
        }

        this.compound = bitReader.readInt32(1);
        int typeValue = bitReader.readInt32(14);
        this.type = StreamObjectTypeHeaderStart.fromIntVal(typeValue);
        if (type == null) {
            throw new TikaException(String.format(Locale.US,
                    "Failed to get the StreamObjectHeaderStart32bit type value, the value %s is not defined",
                    typeValue));
        }

        if (StreamObject.getCompoundTypes().contains(this.type) && this.compound != 1) {
            throw new TikaException(String.format(Locale.US,
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
