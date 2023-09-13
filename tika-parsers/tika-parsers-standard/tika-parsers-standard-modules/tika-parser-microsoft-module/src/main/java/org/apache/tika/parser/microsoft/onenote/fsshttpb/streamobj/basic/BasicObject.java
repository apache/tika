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
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.IFSSHTTPBSerializable;

/**
 * Base object for FSSHTTPB.
 */
public abstract class BasicObject implements IFSSHTTPBSerializable {
    /**
     * Used to parse byte array to special object.
     *
     * @param byteArray The byte array contains raw data.
     * @param index     The index special where to start.
     * @return The instance of target object.
     */
    public static <T extends BasicObject> T parse(byte[] byteArray, AtomicInteger index,
                                                  Class<T> clazz) throws TikaException,
            IOException {
        try {
            T fsshttpbObject = clazz.getDeclaredConstructor().newInstance();
            index.addAndGet(fsshttpbObject.deserializeFromByteArray(byteArray, index.get()));

            return fsshttpbObject;
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException |
                 InvocationTargetException e) {
            throw new TikaException("Could not parse basic object", e);
        }
    }

    /**
     * Used to return the length of this element.
     *
     * @param byteArray  The byte list.
     * @param startIndex The start position.
     * @return The element length.
     */
    public int deserializeFromByteArray(byte[] byteArray, int startIndex)
            throws TikaException, IOException {
        return this.doDeserializeFromByteArray(byteArray, startIndex);
    }

    /**
     * Used to serialize item to byte list.
     *
     * @return The byte list.
     */
    public abstract List<Byte> serializeToByteList() throws IOException;

    /**
     * Used to return the length of this element.
     *
     * @param byteArray  The byte list.
     * @param startIndex The start position.
     * @return The element length
     */
    protected abstract int doDeserializeFromByteArray(byte[] byteArray, int startIndex)
            throws IOException, TikaException;
}
