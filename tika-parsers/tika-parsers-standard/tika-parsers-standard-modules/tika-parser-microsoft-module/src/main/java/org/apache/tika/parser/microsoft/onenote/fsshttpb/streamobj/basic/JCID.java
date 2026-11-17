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

import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitReader;

public class JCID {
    public int index;
    public int isBinary;
    public int isPropertySet;
    public int isGraphNode;
    public int isFileData;
    public int isReadOnly;
    public int reserved;

    public int doDeserializeFromByteArray(byte[] byteArray, int startIndex) throws IOException {
        BitReader bitReader = new BitReader(byteArray, startIndex);
        this.index = bitReader.readInt32(16);
        this.isBinary = bitReader.readInt32(1);
        this.isPropertySet = bitReader.readInt32(1);
        this.isGraphNode = bitReader.readInt32(1);
        this.isFileData = bitReader.readInt32(1);
        this.isReadOnly = bitReader.readInt32(1);
        this.reserved = bitReader.readInt32(11);

        return 4;
    }
}
