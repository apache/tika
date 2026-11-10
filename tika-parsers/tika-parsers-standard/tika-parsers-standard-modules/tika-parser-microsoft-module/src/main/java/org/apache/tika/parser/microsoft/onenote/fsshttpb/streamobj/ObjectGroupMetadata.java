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

public class ObjectGroupMetadata extends StreamObject {
    /** 0 unknown, 1 frequent, 2 infrequent, 3 independent of any other objects. */
    public Compact64bitInt ObjectChangeFrequency;

    public ObjectGroupMetadata() {
        super(StreamObjectTypeHeaderStart.ObjectGroupMetadata);
    }

    @Override
    protected void deserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex,
                                                 int lengthOfItems)
            throws TikaException, IOException {
        AtomicInteger index = new AtomicInteger(currentIndex.get());
        this.ObjectChangeFrequency = BasicObject.parse(byteArray, index, Compact64bitInt.class);

        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "ObjectGroupMetadata",
                    "Stream object over-parse error", null);
        }

        currentIndex.set(index.get());
    }
}
