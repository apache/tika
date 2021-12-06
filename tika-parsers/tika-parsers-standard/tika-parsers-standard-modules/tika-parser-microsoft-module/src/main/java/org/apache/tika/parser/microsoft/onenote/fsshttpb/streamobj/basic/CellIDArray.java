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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class CellIDArray extends BasicObject {
    /**
     * Initializes a new instance of the CellIDArray class.
     *
     * @param count   Specify the number of CellID in the CellID array.
     * @param content Specify the list of CellID.
     */
    public CellIDArray(long count, java.util.List<CellID> content) {
        this.Count = count;
        this.Content = content;
    }

    /**
     * Initializes a new instance of the CellIDArray class, this is copy constructor.
     *
     * @param cellIdArray Specify the CellIDArray.
     */
    public CellIDArray(CellIDArray cellIdArray) {
        this.Count = cellIdArray.Count;
        if (cellIdArray.Content != null) {
            for (CellID cellId : cellIdArray.Content) {
                this.Content.add(new CellID(cellId));
            }
        }
    }

    /**
     * Initializes a new instance of the CellIDArray class, this is default constructor.
     */
    public CellIDArray() {
        this.Content = new ArrayList<CellID>();
    }

    public long Count;

    public List<CellID> Content;

    /**
     * This method is used to convert the element of CellIDArray basic object into a byte List.
     *
     * @return Return the byte list which store the byte information of CellIDArray.
     */
    @Override
    public List<Byte> SerializeToByteList() {
        List<Byte> byteList = new ArrayList<Byte>();
        byteList.addAll((new Compact64bitInt(this.Count)).SerializeToByteList());
        if (this.Content != null) {
            for (CellID extendGuid : this.Content) {
                byteList.addAll(extendGuid.SerializeToByteList());
            }
        }

        return byteList;
    }

    /**
     * This method is used to deserialize the CellIDArray basic object from the specified byte array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return Return the length in byte of the CellIDArray basic object.
     */
    @Override
    protected int DoDeserializeFromByteArray(byte[] byteArray, int startIndex) {
        AtomicInteger index = new AtomicInteger(startIndex);

        this.Count = BasicObject.parse(byteArray, index, Compact64bitInt.class).getDecodedValue();

        for (long i = 0; i < this.Count; i++) {
            this.Content.add(BasicObject.parse(byteArray, index, CellID.class));
        }

        return index.get() - startIndex;
    }
}