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
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ExGuid;

public class RevisionManifestObjectGroupReferences extends StreamObject {
    public ExGuid objectGroupExtendedGUID;

    public RevisionManifestObjectGroupReferences() {
        super(StreamObjectTypeHeaderStart.RevisionManifestObjectGroupReferences);
    }

    public RevisionManifestObjectGroupReferences(ExGuid objectGroupExtendedGUID) {
        super(StreamObjectTypeHeaderStart.RevisionManifestObjectGroupReferences);
        this.objectGroupExtendedGUID = objectGroupExtendedGUID;
    }

    @Override
    protected void deserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex,
                                                 int lengthOfItems)
            throws TikaException, IOException {
        AtomicInteger index = new AtomicInteger(currentIndex.get());
        this.objectGroupExtendedGUID = BasicObject.parse(byteArray, index, ExGuid.class);
        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(),
                    "RevisionManifestObjectGroupReferences", "Stream object over-parse error",
                    null);
        }

        currentIndex.set(index.get());
    }
}
