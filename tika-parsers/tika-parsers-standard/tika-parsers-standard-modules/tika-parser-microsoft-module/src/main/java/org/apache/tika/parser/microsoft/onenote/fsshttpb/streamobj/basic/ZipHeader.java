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
/**
 * This class is used to check is this a zip file header
 */

import java.util.Arrays;

public class ZipHeader {
    /**
     * The file header in zip.
     */
    public static final byte[] LOCAL_FILE_HEADER = new byte[]{0x50, 0x4b, 0x03, 0x04};

    /**
     * Prevents a default instance of the ZipHeader class from being created
     */
    private ZipHeader() {
    }

    /**
     * Check the input data is a local file header.
     *
     * @param byteArray The content of a file.
     * @param index     The index where to start.
     * @return True if the input data is a local file header, otherwise false.
     */
    public static boolean isFileHeader(byte[] byteArray, int index) {
        return Arrays.equals(LOCAL_FILE_HEADER, Arrays.copyOfRange(byteArray, index, 4));
    }
}
