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

package org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.chunking;

import java.util.List;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.LeafNodeObject;

/**
 * This class specifies the base class for file chunking
 */
public abstract class AbstractChunking {
    /**
     * Initializes a new instance of the AbstractChunking class.
     *
     * @param fileContent The content of the file.
     */
    protected AbstractChunking(byte[] fileContent) {
        this.FileContent = fileContent;
    }

    protected byte[] FileContent;

    /**
     * This method is used to chunk the file data.
     *
     * @return A list of LeafNodeObjectData.
     */
    public abstract List<LeafNodeObject> Chunking();
}