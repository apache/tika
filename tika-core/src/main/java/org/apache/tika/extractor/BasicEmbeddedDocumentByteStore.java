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
package org.apache.tika.extractor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.extractor.EmbeddedDocumentBytesConfig;

public class BasicEmbeddedDocumentByteStore extends AbstractEmbeddedDocumentByteStore {
    private final EmbeddedDocumentBytesConfig config;
    public BasicEmbeddedDocumentByteStore(EmbeddedDocumentBytesConfig config) {
        this.config = config;
    }
    //this won't scale, but let's start fully in memory for now;
    Map<Integer, byte[]> docBytes = new HashMap<>();
    public void add(int id, Metadata metadata, byte[] bytes) throws IOException {
        super.add(id, metadata, bytes);
        docBytes.put(id, bytes);
    }

    public byte[] getDocument(int id) {
        return docBytes.get(id);
    }

    @Override
    public void close() throws IOException {
        //delete tmp dir or whatever here
    }
}
