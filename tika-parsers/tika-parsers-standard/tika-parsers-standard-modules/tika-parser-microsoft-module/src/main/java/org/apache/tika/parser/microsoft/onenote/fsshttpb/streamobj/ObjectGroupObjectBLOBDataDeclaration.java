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

public class ObjectGroupObjectBLOBDataDeclaration extends StreamObject {
    public ExGuid objectExGUID;
    public ExGuid objectDataBLOBExGUID;
    public Compact64bitInt objectPartitionID;
    public Compact64bitInt objectReferencesCount;
    public Compact64bitInt cellReferencesCount;

    public ObjectGroupObjectBLOBDataDeclaration() {
        super(StreamObjectTypeHeaderStart.ObjectGroupObjectBLOBDataDeclaration);
        this.objectExGUID = new ExGuid();
        this.objectDataBLOBExGUID = new ExGuid();
        this.objectPartitionID = new Compact64bitInt();
        this.objectReferencesCount = new Compact64bitInt();
        this.cellReferencesCount = new Compact64bitInt();
    }

    @Override
    protected void deserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex,
                                                 int lengthOfItems)
            throws TikaException, IOException {
        AtomicInteger index = new AtomicInteger(currentIndex.get());

        this.objectExGUID = BasicObject.parse(byteArray, index, ExGuid.class);
        this.objectDataBLOBExGUID = BasicObject.parse(byteArray, index, ExGuid.class);
        this.objectPartitionID = BasicObject.parse(byteArray, index, Compact64bitInt.class);
        this.objectReferencesCount = BasicObject.parse(byteArray, index, Compact64bitInt.class);
        this.cellReferencesCount = BasicObject.parse(byteArray, index, Compact64bitInt.class);

        endItems(currentIndex, index, lengthOfItems, "ObjectGroupObjectBLOBDataDeclaration");
    }
}
