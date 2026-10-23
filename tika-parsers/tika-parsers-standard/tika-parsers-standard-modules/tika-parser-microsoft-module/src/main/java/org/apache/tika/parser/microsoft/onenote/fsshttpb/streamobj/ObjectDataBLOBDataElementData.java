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
 * Object data BLOB data element - carries the opaque binary data of an object, e.g. an
 * embedded image or file. See MS-FSSHTTPB section 2.2.1.12.8.
 */
public class ObjectDataBLOBDataElementData extends DataElementData {
    public ObjectDataBLOB objectDataBLOB;

    public ObjectDataBLOBDataElementData() {
        this.objectDataBLOB = new ObjectDataBLOB();
    }

    @Override
    public int deserializeDataElementDataFromByteArray(byte[] byteArray, int startIndex)
            throws TikaException, IOException {
        AtomicInteger index = new AtomicInteger(startIndex);
        this.objectDataBLOB = StreamObject.getCurrent(byteArray, index, ObjectDataBLOB.class);
        return index.get() - startIndex;
    }

    @Override
    public List<Byte> serializeToByteList() throws TikaException, IOException {
        return this.objectDataBLOB.serializeToByteList();
    }
}
