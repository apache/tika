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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.exception.TikaException;

public class CellID extends BasicObject {
    public ExGuid extendGUID1;
    public ExGuid extendGUID2;

    /**
     * Initializes a new instance of the CellID class with specified ExGuids.
     *
     * @param extendGuid1 Specify the first ExGuid.
     * @param extendGuid2 Specify the second ExGuid.
     */
    public CellID(ExGuid extendGuid1, ExGuid extendGuid2) {
        this.extendGUID1 = extendGuid1;
        this.extendGUID2 = extendGuid2;
    }

    /**
     * Initializes a new instance of the CellID class, this is the copy constructor.
     *
     * @param cellId Specify the CellID.
     */

    public CellID(CellID cellId) {
        if (cellId.extendGUID1 != null) {
            this.extendGUID1 = new ExGuid(cellId.extendGUID1);
        }

        if (cellId.extendGUID2 != null) {
            this.extendGUID2 = new ExGuid(cellId.extendGUID2);
        }
    }

    /**
     * Initializes a new instance of the CellID class, this is default constructor.
     */
    public CellID() {
    }

    /**
     * This method is used to convert the element of CellID basic object into a byte List.
     *
     * @return Return the byte list which store the byte information of CellID.
     */
    @Override
    public List<Byte> serializeToByteList() throws IOException {
        java.util.List<Byte> byteList = new ArrayList<>();
        byteList.addAll(this.extendGUID1.serializeToByteList());
        byteList.addAll(this.extendGUID2.serializeToByteList());
        return byteList;
    }

    /**
     * Override the Equals method.
     *
     * @param obj Specify the object.
     * @return Return true if equals, otherwise return false.
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CellID)) {
            return false;
        }
        CellID another = (CellID) obj;

        if (another.extendGUID1 != null && another.extendGUID2 != null &&
                this.extendGUID1 != null && this.extendGUID2 != null) {
            return another.extendGUID1.equals(this.extendGUID1) &&
                    another.extendGUID2.equals(this.extendGUID2);
        }

        return false;
    }

    /**
     * Override the GetHashCode.
     *
     * @return Return the hash value.
     */
    @Override
    public int hashCode() {
        return this.extendGUID1.hashCode() + this.extendGUID2.hashCode();
    }

    /**
     * This method is used to deserialize the CellID basic object from the specified byte array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return Return the length in byte of the CellID basic object.
     */
    @Override
    protected int doDeserializeFromByteArray(byte[] byteArray, int startIndex)
            throws TikaException, IOException {
        AtomicInteger index = new AtomicInteger(startIndex);

        this.extendGUID1 = BasicObject.parse(byteArray, index, ExGuid.class);
        this.extendGUID2 = BasicObject.parse(byteArray, index, ExGuid.class);

        return index.get() - startIndex;
    }
}
