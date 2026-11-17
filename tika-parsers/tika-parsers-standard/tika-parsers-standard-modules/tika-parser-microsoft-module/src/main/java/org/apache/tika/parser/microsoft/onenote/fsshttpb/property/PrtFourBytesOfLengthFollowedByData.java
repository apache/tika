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
package org.apache.tika.parser.microsoft.onenote.fsshttpb.property;

import java.io.IOException;
import java.util.Arrays;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitConverter;

public class PrtFourBytesOfLengthFollowedByData implements IProperty {
    public int cb;

    public byte[] data;

    public int doDeserializeFromByteArray(byte[] byteArray, int startIndex) throws IOException {
        int index = startIndex;
        this.cb = (int) BitConverter.toUInt32(byteArray, startIndex);
        index += 4;
        // copyOfRange zero-fills past the source end, so cb must be bounded by
        // the remaining bytes or a malformed length allocates cb bytes of heap
        if (this.cb < 0 || this.cb > byteArray.length - index) {
            throw new IOException("prtFourBytesOfLengthFollowedByData length " + this.cb +
                    " exceeds remaining data " + (byteArray.length - index));
        }
        this.data = Arrays.copyOfRange(byteArray, index, index + this.cb);
        index += this.cb;

        return index - startIndex;
    }
}
