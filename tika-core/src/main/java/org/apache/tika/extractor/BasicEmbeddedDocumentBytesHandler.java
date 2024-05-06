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
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.UnsynchronizedBufferedInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.extractor.EmbeddedDocumentBytesConfig;

/**
 * For now, this is an in-memory EmbeddedDocumentBytesHandler that stores all the bytes in memory.
 * Users can retrieve the documents with {@link #getDocument(int)}.
 *
 * <p>We'll need to make this cache to disk at some point if there are many bytes of embedded
 * documents.
 */
public class BasicEmbeddedDocumentBytesHandler extends AbstractEmbeddedDocumentBytesHandler {
    private final EmbeddedDocumentBytesConfig config;

    public BasicEmbeddedDocumentBytesHandler(EmbeddedDocumentBytesConfig config) {
        this.config = config;
    }

    // this won't scale, but let's start fully in memory for now;
    Map<Integer, byte[]> docBytes = new HashMap<>();

    @Override
    public void add(int id, Metadata metadata, InputStream is) throws IOException {
        super.add(id, metadata, is);
        docBytes.put(id, IOUtils.toByteArray(is));
    }

    public InputStream getDocument(int id) throws IOException {
        return new UnsynchronizedBufferedInputStream.Builder().setByteArray(docBytes.get(id)).get();
    }

    @Override
    public void close() throws IOException {
        // delete tmp dir or whatever here
    }
}
