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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.exception.TikaException;

public class CellID extends BasicObject {
    public ExGuid extendGUID1;
    public ExGuid extendGUID2;

    public CellID(ExGuid extendGuid1, ExGuid extendGuid2) {
        this.extendGUID1 = extendGuid1;
        this.extendGUID2 = extendGuid2;
    }

    public CellID(CellID cellId) {
        if (cellId.extendGUID1 != null) {
            this.extendGUID1 = new ExGuid(cellId.extendGUID1);
        }

        if (cellId.extendGUID2 != null) {
            this.extendGUID2 = new ExGuid(cellId.extendGUID2);
        }
    }

    public CellID() {
    }

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

    @Override
    public int hashCode() {
        return this.extendGUID1.hashCode() + this.extendGUID2.hashCode();
    }

    @Override
    public String toString() {
        return "CellID{" +
                "extendGUID1=" + extendGUID1 +
                ", extendGUID2=" + extendGUID2 +
                '}';
    }

    @Override
    protected int doDeserializeFromByteArray(byte[] byteArray, int startIndex)
            throws TikaException, IOException {
        AtomicInteger index = new AtomicInteger(startIndex);

        this.extendGUID1 = BasicObject.parse(byteArray, index, ExGuid.class);
        this.extendGUID2 = BasicObject.parse(byteArray, index, ExGuid.class);

        return index.get() - startIndex;
    }
}
