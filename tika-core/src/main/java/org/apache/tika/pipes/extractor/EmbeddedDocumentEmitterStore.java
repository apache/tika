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
package org.apache.tika.pipes.extractor;

import java.io.Closeable;
import java.io.IOException;

import org.apache.commons.io.IOExceptionWithCause;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.extractor.AbstractEmbeddedDocumentByteStore;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.emitter.Emitter;
import org.apache.tika.pipes.emitter.EmitterManager;
import org.apache.tika.pipes.emitter.StreamEmitter;
import org.apache.tika.pipes.emitter.TikaEmitterException;

public class EmbeddedDocumentEmitterStore extends AbstractEmbeddedDocumentByteStore {
    private final EmitKey containerEmitKey;
    private final EmbeddedDocumentBytesConfig embeddedDocumentBytesConfig;
    private final StreamEmitter emitter;

    private static final Metadata METADATA = new Metadata();
    public EmbeddedDocumentEmitterStore(EmitKey containerEmitKey,
                                        EmbeddedDocumentBytesConfig embeddedDocumentBytesConfig,
                                        EmitterManager emitterManager) throws TikaConfigException {
        this.containerEmitKey = containerEmitKey;
        this.embeddedDocumentBytesConfig = embeddedDocumentBytesConfig;
        Emitter tmpEmitter =
                emitterManager.getEmitter(embeddedDocumentBytesConfig.getEmitter());
        if (! (tmpEmitter instanceof StreamEmitter)) {
            throw new TikaConfigException("Emitter " +
                    embeddedDocumentBytesConfig.getEmitter()
                    + " must implement a StreamEmitter");
        }
        this.emitter = (StreamEmitter) tmpEmitter;
    }

    @Override
    public void add(int id, Metadata metadata, byte[] bytes) throws IOException {
        //intentionally do not call super.add, because we want the ids list to be empty
        String emitKey = getEmitKey(containerEmitKey.getEmitKey(),
                id, embeddedDocumentBytesConfig, metadata);

        try {
            emitter.emit(emitKey, new UnsynchronizedByteArrayInputStream(bytes), METADATA);
        } catch (TikaEmitterException e) {
            throw new IOExceptionWithCause(e);
        }
    }

    @Override
    public byte[] getDocument(int id) {
        throw new UnsupportedOperationException("this is emit only.");
    }

    @Override
    public void close() throws IOException {
        if (emitter instanceof Closeable) {
            ((Closeable) emitter).close();
        }
    }
}
