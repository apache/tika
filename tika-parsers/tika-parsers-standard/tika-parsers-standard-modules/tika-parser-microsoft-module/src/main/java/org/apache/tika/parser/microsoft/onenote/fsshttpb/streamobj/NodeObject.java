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

import java.util.List;

import org.apache.tika.exception.TikaException;

public abstract class NodeObject extends StreamObject {
    public org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ExGuid exGuid;
    public List<LeafNodeObject> intermediateNodeObjectList;
    public SignatureObject signature;
    public DataSizeObject dataSize;

    /**
     * Initializes a new instance of the NodeObject class.
     *
     * @param headerType Specify the node object header type.
     */
    protected NodeObject(StreamObjectTypeHeaderStart headerType) {
        super(headerType);
    }

    /**
     * Get all the content which is represented by the node object.
     *
     * @return Return the byte list of node object content.
     */
    public abstract List<Byte> getContent() throws TikaException;
}
