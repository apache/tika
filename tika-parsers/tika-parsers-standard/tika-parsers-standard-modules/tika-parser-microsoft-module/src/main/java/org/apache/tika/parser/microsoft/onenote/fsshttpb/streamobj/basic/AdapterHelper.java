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

package org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.tika.parser.microsoft.onenote.ExtendedGUID;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.ByteUtil;

public class AdapterHelper {

    /**
     * This method is used to read the Guid for byte array.
     *
     * @param byteArray  The byte array.
     * @param startIndex The offset of the Guid value.
     * @return Return the value of Guid.
     */
    public static UUID readGuid(byte[] byteArray, int startIndex) {
        byte[] bytes = Arrays.copyOfRange(byteArray, startIndex, startIndex + 16);
        return UUID.nameUUIDFromBytes(bytes);
    }

    /**
     * XOR two ExtendedGUID instances.
     *
     * @param exGuid1 The first ExtendedGUID instance.
     * @param exGuid2 The second ExtendedGUID instance.
     * @return Returns the result of XOR two ExtendedGUID instances.
     */
    public static ExGuid xorExtendedGUID(ExtendedGUID exGuid1, ExtendedGUID exGuid2)
            throws IOException {
        List<Byte> exGuid1Buffer = exGuid1.serializeToByteList();
        List<Byte> exGuid2Buffer = exGuid2.serializeToByteList();
        List<Byte> resultBuffer = new ArrayList<>(exGuid1Buffer.size());

        for (int i = 0; i < exGuid1Buffer.size(); i++) {
            byte fromExGuid1 = exGuid1Buffer.get(i);
            byte fromExGuid2 = exGuid2Buffer.get(i);
            resultBuffer.set(i, (byte) (fromExGuid1 ^ fromExGuid2));
        }
        ExGuid resultExGuid = new ExGuid();
        resultExGuid.doDeserializeFromByteArray(ByteUtil.toByteArray(resultBuffer), 0);
        return resultExGuid;
    }
}
