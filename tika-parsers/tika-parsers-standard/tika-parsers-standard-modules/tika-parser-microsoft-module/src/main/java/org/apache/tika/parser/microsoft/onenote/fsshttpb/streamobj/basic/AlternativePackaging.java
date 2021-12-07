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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.DataElementPackage;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StreamObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StreamObjectHeaderEnd;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StreamObjectHeaderEnd16bit;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StreamObjectHeaderStart32bit;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitConverter;

public class AlternativePackaging {
    public UUID guidFileType;
    public UUID guidFile;
    public UUID guidLegacyFileVersion;
    public UUID guidFileFormat;
    public long rgbReserved;
    public StreamObjectHeaderStart32bit packagingStart;
    public ExGuid storageIndexExtendedGUID;
    public UUID guidCellSchemaId;
    public DataElementPackage dataElementPackage;
    public StreamObjectHeaderEnd packagingEnd;

    /**
     * This method is used to deserialize the Alternative Packaging object from the specified byte
     * array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return Return the length in byte of the Alternative Packaging object.
     */
    public int doDeserializeFromByteArray(byte[] byteArray, int startIndex)
            throws IOException, TikaException {
        AtomicInteger index = new AtomicInteger(startIndex);
        this.guidFileType = AdapterHelper.readGuid(byteArray, index.get());
        index.addAndGet(16);
        this.guidFile = AdapterHelper.readGuid(byteArray, index.get());
        index.addAndGet(16);
        this.guidLegacyFileVersion = AdapterHelper.readGuid(byteArray, index.get());
        index.addAndGet(16);
        this.guidFileFormat = AdapterHelper.readGuid(byteArray, index.get());
        index.addAndGet(16);
        this.rgbReserved = BitConverter.toUInt32(byteArray, index.get());
        index.addAndGet(4);
        this.packagingStart = new StreamObjectHeaderStart32bit();
        this.packagingStart.deserializeFromByteArray(byteArray, index.get());
        index.addAndGet(4);
        this.storageIndexExtendedGUID = BasicObject.parse(byteArray, index, ExGuid.class);
        this.guidCellSchemaId = AdapterHelper.readGuid(byteArray, index.get());
        index.addAndGet(16);
        AtomicReference<DataElementPackage> pkg = new AtomicReference<>();
        StreamObject.tryGetCurrent(byteArray, index, pkg, DataElementPackage.class);
        this.dataElementPackage = pkg.get();
        this.packagingEnd = new StreamObjectHeaderEnd16bit();
        this.packagingEnd.deserializeFromByteArray(byteArray, index.get());
        index.addAndGet(2);

        return index.get() - startIndex;
    }
}
