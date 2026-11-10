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
package org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.BasicObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.Compact64bitInt;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ExGuid;

public class ObjectGroupObjectDeclare extends StreamObject {
    public ExGuid objectExtendedGUID;
    public Compact64bitInt objectPartitionID;
    /** MUST match the size of the binary item in the corresponding object data. */
    public Compact64bitInt objectDataSize;
    public Compact64bitInt objectReferencesCount;
    public Compact64bitInt cellReferencesCount;

    public ObjectGroupObjectDeclare() {
        super(StreamObjectTypeHeaderStart.ObjectGroupObjectDeclare);
        this.objectExtendedGUID = new ExGuid();
        this.objectPartitionID = new Compact64bitInt();
        this.objectDataSize = new Compact64bitInt();
        this.objectReferencesCount = new Compact64bitInt();
        this.cellReferencesCount = new Compact64bitInt();

        this.objectPartitionID.setDecodedValue(1);
        this.objectReferencesCount.setDecodedValue(1);
        this.cellReferencesCount.setDecodedValue(0);
    }

    @Override
    protected void deserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex,
                                                 int lengthOfItems)
            throws TikaException, IOException {
        AtomicInteger index = new AtomicInteger(currentIndex.get());

        this.objectExtendedGUID = BasicObject.parse(byteArray, index, ExGuid.class);
        this.objectPartitionID = BasicObject.parse(byteArray, index, Compact64bitInt.class);
        this.objectDataSize = BasicObject.parse(byteArray, index, Compact64bitInt.class);
        this.objectReferencesCount = BasicObject.parse(byteArray, index, Compact64bitInt.class);
        this.cellReferencesCount = BasicObject.parse(byteArray, index, Compact64bitInt.class);

        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(),
                    "ObjectGroupObjectDeclare", "Stream object over-parse error", null);
        }

        currentIndex.set(index.get());
    }
}
