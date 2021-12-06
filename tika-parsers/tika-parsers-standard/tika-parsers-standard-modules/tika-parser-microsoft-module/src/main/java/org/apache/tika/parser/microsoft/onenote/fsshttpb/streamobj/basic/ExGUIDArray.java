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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ExGUIDArray extends BasicObject {
    public Compact64bitInt Count;
    /**
     * Gets or sets an extended GUID array
     */
    public List<ExGuid> Content;

    /**
     * Initializes a new instance of the ExGUIDArray class with specified value.
     *
     * @param content Specify the list of ExGuid contents.
     */
    public ExGUIDArray(List<ExGuid> content) {
        this();
        this.Content = new ArrayList<>();
        if (content != null) {
            for (ExGuid extendGuid : content) {
                this.Content.add(new ExGuid(extendGuid));
            }
        }

        this.Count.setDecodedValue(this.Content.size());
    }

    /**
     * Initializes a new instance of the ExGUIDArray class, this is copy constructor.
     *
     * @param extendGuidArray Specify the ExGUIDArray where copies from.
     */
    public ExGUIDArray(ExGUIDArray extendGuidArray) {
        this(extendGuidArray.Content);
    }

    /**
     * Initializes a new instance of the ExGUIDArray class, this is the default constructor.
     */
    public ExGUIDArray() {
        this.Count = new Compact64bitInt();
        this.Content = new ArrayList<>();
    }

    public java.util.List<ExGuid> getContent() {
        return Content;
    }

    public void setContent(java.util.List<ExGuid> content) {
        this.Content = content;
        this.Count.setDecodedValue(this.Content.size());
    }

    /**
     * This method is used to convert the element of ExGUIDArray basic object into a byte List.
     *
     * @return Return the byte list which store the byte information of ExGUIDArray.
     */
    @Override
    public List<Byte> SerializeToByteList() {
        this.Count.setDecodedValue(this.Content.size());

        List<Byte> result = new ArrayList<Byte>();
        result.addAll(this.Count.SerializeToByteList());
        for (ExGuid extendGuid : this.Content) {
            result.addAll(extendGuid.SerializeToByteList());
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
    protected int DoDeserializeFromByteArray(byte[] byteArray,
                                             int startIndex) // return the length consumed
    {
        AtomicInteger index = new AtomicInteger(startIndex);
        this.Count = BasicObject.parse(byteArray, index, Compact64bitInt.class);

        this.Content.clear();
        for (int i = 0; i < this.Count.getDecodedValue(); i++) {
            ExGuid temp = BasicObject.parse(byteArray, index, ExGuid.class);
            this.Content.add(temp);
        }

        return index.get() - startIndex;
    }
}
