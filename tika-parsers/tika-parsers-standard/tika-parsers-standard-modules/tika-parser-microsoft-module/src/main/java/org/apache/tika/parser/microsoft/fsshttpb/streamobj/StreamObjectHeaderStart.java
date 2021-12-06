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

import java.util.concurrent.atomic.AtomicReference;

import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.BasicObject;

/**
 * This class specifies the base class for 16-bit or 32-bit stream object header start
 */
public abstract class StreamObjectHeaderStart extends BasicObject {
    /**
     * Specify for 16-bit stream object header start.
     */
    public static final int StreamObjectHeaderStart16bit = 0x0;

    /**
     * Specify for 32-bit stream object header start.
     */
    public static final int StreamObjectHeaderStart32bit = 0x02;

    /**
     * Initializes a new instance of the StreamObjectHeaderStart class.
     */
    protected StreamObjectHeaderStart() {
    }

    /**
     * Initializes a new instance of the StreamObjectHeaderStart class with specified header type.
     *
     * @param streamObjectTypeHeaderStart Specify the value of the StreamObjectHeaderStart Type.
     */
    protected StreamObjectHeaderStart(StreamObjectTypeHeaderStart streamObjectTypeHeaderStart) {
        this.type = streamObjectTypeHeaderStart;
    }

    /**
     * Gets or sets the type of the stream object.
     * value 0 for 16-bit stream object header start,
     * value 2 for 32-bit stream object header start.
     */
    protected int headerType;

    /**
     * Gets or sets a value that specifies if set a compound parse type is needed and
     * MUST be ended with either an 8-bit stream object header end or a 16-bit stream object header end.
     * If the bit is zero, it specifies a single object. Otherwise it specifies a compound object.
     */
    protected int compound;

    public StreamObjectTypeHeaderStart type;

    protected int length;

    /**
     * This method is used to parse the actual 16bit or 32bit stream header.
     *
     * @param byteArray          Specify the Byte array.
     * @param startIndex         Specify the start position.
     * @param streamObjectHeader Specify the out value for the parse result.
     * @return Return true if success, otherwise returns false.
     */
    public static int TryParse(byte[] byteArray, int startIndex,
                               AtomicReference<StreamObjectHeaderStart> streamObjectHeader) {
        int headerType = byteArray[startIndex] & 0x03;
        if (headerType == StreamObjectHeaderStart.StreamObjectHeaderStart16bit) {
            streamObjectHeader.set(new StreamObjectHeaderStart16bit());
        } else {
            if (headerType == StreamObjectHeaderStart.StreamObjectHeaderStart32bit) {
                streamObjectHeader.set(new StreamObjectHeaderStart32bit());
            } else {
                return 0;
            }
        }

        try {
            return streamObjectHeader.get().DeserializeFromByteArray(byteArray, startIndex);
        } catch (Exception e) {
            return 0;
        }
    }
}