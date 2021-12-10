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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.exception.TikaException;

/**
 * Cell manifest data element
 */
public class CellManifestDataElementData extends DataElementData {
    public CellManifestCurrentRevision cellManifestCurrentRevision;

    /**
     * Initializes a new instance of the CellManifestDataElementData class.
     */
    public CellManifestDataElementData() {
        this.cellManifestCurrentRevision = new CellManifestCurrentRevision();
    }

    /**
     * Used to return the length of this element.
     *
     * @param byteArray  A Byte array
     * @param startIndex Start position
     * @return The element length
     */
    @Override
    public int deserializeDataElementDataFromByteArray(byte[] byteArray, int startIndex)
            throws TikaException, IOException {
        AtomicInteger index = new AtomicInteger(startIndex);
        this.cellManifestCurrentRevision =
                StreamObject.getCurrent(byteArray, index, CellManifestCurrentRevision.class);
        return index.get() - startIndex;
    }

    /**
     * Used to convert the element into a byte List.
     *
     * @return The Byte list
     */
    @Override
    public List<Byte> serializeToByteList() throws TikaException, IOException {
        return this.cellManifestCurrentRevision.serializeToByteList();
    }
}
