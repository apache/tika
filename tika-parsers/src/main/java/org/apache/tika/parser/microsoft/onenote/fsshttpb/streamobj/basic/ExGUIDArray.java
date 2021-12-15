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

public class ExGUIDArray extends BasicObject {
    public Compact64bitInt count;
    /**
     * Gets or sets an extended GUID array
     */
    public List<ExGuid> content;

    /**
     * Initializes a new instance of the ExGUIDArray class with specified value.
     *
     * @param content Specify the list of ExGuid contents.
     */
    public ExGUIDArray(List<ExGuid> content) {
        this();
        this.content = new ArrayList<>();
        if (content != null) {
            for (ExGuid extendGuid : content) {
                this.content.add(new ExGuid(extendGuid));
            }
        }

        this.count.setDecodedValue(this.content.size());
    }

    /**
     * Initializes a new instance of the ExGUIDArray class, this is copy constructor.
     *
     * @param extendGuidArray Specify the ExGUIDArray where copies from.
     */
    public ExGUIDArray(ExGUIDArray extendGuidArray) {
        this(extendGuidArray.content);
    }

    /**
     * Initializes a new instance of the ExGUIDArray class, this is the default constructor.
     */
    public ExGUIDArray() {
        this.count = new Compact64bitInt();
        this.content = new ArrayList<>();
    }

    public List<ExGuid> getContent() {
        return content;
    }

    public void setContent(List<ExGuid> content) {
        this.content = content;
        this.count.setDecodedValue(this.content.size());
    }

    /**
     * This method is used to convert the element of ExGUIDArray basic object into a byte List.
     *
     * @return Return the byte list which store the byte information of ExGUIDArray.
     */
    @Override
    public List<Byte> serializeToByteList() throws IOException {
        this.count.setDecodedValue(this.content.size());

        List<Byte> result = new ArrayList<>();
        result.addAll(this.count.serializeToByteList());
        for (ExGuid extendGuid : this.content) {
            result.addAll(extendGuid.serializeToByteList());
        }

        return result;
    }

    /**
     * This method is used to deserialize the ExGUIDArray basic object from the specified byte array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return Return the length in byte of the ExGUIDArray basic object.
     */
    @Override
    protected int doDeserializeFromByteArray(byte[] byteArray,
                                             int startIndex)
            throws TikaException, IOException // return the length consumed
    {
        AtomicInteger index = new AtomicInteger(startIndex);
        this.count = BasicObject.parse(byteArray, index, Compact64bitInt.class);

        this.content.clear();
        for (int i = 0; i < this.count.getDecodedValue(); i++) {
            ExGuid temp = BasicObject.parse(byteArray, index, ExGuid.class);
            this.content.add(temp);
        }

        return index.get() - startIndex;
    }
}
