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

public class CellIDArray extends BasicObject {
    public long count;
    public List<CellID> content;

    public CellIDArray(long count, java.util.List<CellID> content) {
        this.count = count;
        this.content = content;
    }

    public CellIDArray(CellIDArray cellIdArray) {
        this.count = cellIdArray.count;
        if (cellIdArray.content != null) {
            for (CellID cellId : cellIdArray.content) {
                this.content.add(new CellID(cellId));
            }
        }
    }

    public CellIDArray() {
        this.content = new ArrayList<CellID>();
    }

    @Override
    protected int doDeserializeFromByteArray(byte[] byteArray, int startIndex)
            throws TikaException, IOException {
        AtomicInteger index = new AtomicInteger(startIndex);

        this.count = BasicObject.parse(byteArray, index, Compact64bitInt.class).getDecodedValue();

        for (long i = 0; i < this.count; i++) {
            this.content.add(BasicObject.parse(byteArray, index, CellID.class));
        }

        return index.get() - startIndex;
    }
}
