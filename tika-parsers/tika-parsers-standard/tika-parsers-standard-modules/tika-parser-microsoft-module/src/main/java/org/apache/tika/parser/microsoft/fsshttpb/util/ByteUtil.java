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

package org.apache.tika.parser.microsoft.fsshttpb.util;

import java.util.ArrayList;
import java.util.List;

public final class ByteUtil {
    public static byte[] toByteArray(List<Byte> bytes) {
        byte[] res = new byte[bytes.size()];
        for (int i = 0; i < res.length; ++i) {
            res[i] = bytes.get(i);
        }
        return res;
    }

    public static List<Byte> toListOfByte(byte[] bytes) {
        List<Byte> listOfByte = new ArrayList<>();
        for (byte b : bytes) {
            listOfByte.add(b);
        }
        return listOfByte;
    }

    public static void appendByteArrayToListOfByte(List<Byte> byteList, byte[] byteArrayToAdd) {
        for (byte b : byteArrayToAdd) {
            byteList.add(b);
        }
    }
}
