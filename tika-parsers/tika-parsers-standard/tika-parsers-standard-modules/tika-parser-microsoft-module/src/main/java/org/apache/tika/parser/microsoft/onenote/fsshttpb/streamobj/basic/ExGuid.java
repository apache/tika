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
import java.util.UUID;

import org.apache.poi.openxml4j.exceptions.InvalidOperationException;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitReader;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.GuidUtil;

public class ExGuid extends BasicObject {
    public static final int ExtendedGUIDNullType = 0;

    public static final int ExtendedGUID5BitUintType = 4;

    public static final int ExtendedGUID10BitUintType = 32;

    public static final int ExtendedGUID17BitUintType = 64;

    public static final int ExtendedGUID32BitUintType = 128;
    public int type;
    public int value;
    public UUID guid;

    public ExGuid(int value, UUID identifier) {
        this.value = value;
        this.guid = identifier;
    }

    public ExGuid(ExGuid guid2) {
        this.value = guid2.value;
        this.guid = guid2.guid;
        this.type = guid2.type;
    }

    public ExGuid() {
        this.guid = GuidUtil.emptyGuid();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ExGuid) {
            ExGuid another = (ExGuid) obj;

            if (this.guid != null && another.guid != null) {
                return another.guid.equals(this.guid) && another.value == this.value;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.guid.hashCode() + Integer.valueOf(this.value).hashCode();
    }

    @Override
    public String toString() {
        return "ExGuid{" +
                "type=" + type +
                ", value=" + value +
                ", guid=" + guid +
                '}';
    }

    @Override
    protected int doDeserializeFromByteArray(byte[] byteArray, int startIndex) throws IOException {
        BitReader bitReader = new BitReader(byteArray, startIndex);
        int numberOfContinousZeroBit = 0;
        while (numberOfContinousZeroBit < 8 && bitReader.moveNext()) {
            if (!bitReader.getCurrent()) {
                numberOfContinousZeroBit++;
            } else {
                break;
            }
        }

        switch (numberOfContinousZeroBit) {
            case 2:
                this.value = bitReader.readUInt32(5);
                this.guid = bitReader.readGuid();
                this.type = ExtendedGUID5BitUintType;
                return 17;

            case 5:
                this.value = bitReader.readUInt32(10);
                this.guid = bitReader.readGuid();
                this.type = ExtendedGUID10BitUintType;
                return 18;

            case 6:
                this.value = bitReader.readUInt32(17);
                this.guid = bitReader.readGuid();
                this.type = ExtendedGUID17BitUintType;
                return 19;

            case 7:
                this.value = bitReader.readUInt32(32);
                this.guid = bitReader.readGuid();
                this.type = ExtendedGUID32BitUintType;
                return 21;

            case 8:
                this.guid = GuidUtil.emptyGuid();
                this.type = ExtendedGUIDNullType;
                return 1;

            default:
                throw new InvalidOperationException(
                        "Failed to parse the ExGuid, the type value is unexpected");
        }
    }
}
