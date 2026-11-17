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

public class StreamObjectHeaderEnd16bit extends StreamObjectHeaderEnd {

    public StreamObjectHeaderEnd16bit() {
    }

    @Override
    protected int doDeserializeFromByteArray(byte[] byteArray, int startIndex)
            throws IOException, TikaException {
        BitReader reader = new BitReader(byteArray, startIndex);
        int headerType = reader.readInt32(2);

        if (headerType != 0x3) {
            throw new TikaException(String.format(Locale.US,
                    "Failed to get the StreamObjectHeaderEnd16bit header type value, expect value %d, " +
                            "but actual value is %s", 0x3, headerType));
        }

        int typeValue = reader.readUInt32(14);
        this.type = StreamObjectTypeHeaderEnd.fromIntVal(typeValue);
        if (this.type == null) {
            throw new TikaException(String.format(Locale.US,
                    "Failed to get the StreamObjectHeaderEnd16bit type value, the value %d is not defined",
                    typeValue));
        }

        return 2;
    }
}
