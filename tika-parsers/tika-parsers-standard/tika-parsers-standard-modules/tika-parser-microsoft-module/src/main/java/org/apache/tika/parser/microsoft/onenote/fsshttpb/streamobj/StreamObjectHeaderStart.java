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

import java.util.concurrent.atomic.AtomicReference;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.BasicObject;

public abstract class StreamObjectHeaderStart extends BasicObject {
    public static final int STREAM_OBJECT_HEADER_START_16_BIT = 0x0;

    public static final int STREAM_OBJECT_HEADER_START_32_BIT = 0x02;
    public StreamObjectTypeHeaderStart type;
    protected int headerType;
    /** Nonzero for a compound object, which MUST be ended by an 8- or 16-bit header end. */
    protected int compound;
    protected int length;

    protected StreamObjectHeaderStart() {
    }

    public static int tryParse(byte[] byteArray, int startIndex,
                               AtomicReference<StreamObjectHeaderStart> streamObjectHeader) {
        int headerType = byteArray[startIndex] & 0x03;
        if (headerType == StreamObjectHeaderStart.STREAM_OBJECT_HEADER_START_16_BIT) {
            streamObjectHeader.set(new StreamObjectHeaderStart16bit());
        } else {
            if (headerType == StreamObjectHeaderStart.STREAM_OBJECT_HEADER_START_32_BIT) {
                streamObjectHeader.set(new StreamObjectHeaderStart32bit());
            } else {
                return 0;
            }
        }

        try {
            return streamObjectHeader.get().deserializeFromByteArray(byteArray, startIndex);
        } catch (Exception e) {
            return 0;
        }
    }
}
