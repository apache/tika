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
package org.apache.tika.pipes.core.server;

import java.util.List;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentBytesHandler;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.pipes.core.extractor.EmittingEmbeddedDocumentBytesHandler;

class MetadataListAndEmbeddedBytes {

        List<Metadata> metadataList;
        final EmbeddedDocumentBytesHandler embeddedDocumentBytesHandler;

        public MetadataListAndEmbeddedBytes(List<Metadata> metadataList,
                                            EmbeddedDocumentBytesHandler embeddedDocumentBytesHandler) {
            this.metadataList = metadataList;
            this.embeddedDocumentBytesHandler = embeddedDocumentBytesHandler;
        }

        public List<Metadata> getMetadataList() {
            return metadataList;
        }

        public void filter(MetadataFilter filter) throws TikaException {
            metadataList = filter.filter(metadataList);
        }

        public EmbeddedDocumentBytesHandler getEmbeddedDocumentBytesHandler() {
            return embeddedDocumentBytesHandler;
        }

        /**
         * This tests whether there's any type of embedded document store
         * ...that, for example, may require closing at the end of the parse.
         *
         * @return
         */
        public boolean hasEmbeddedDocumentByteStore() {
            return embeddedDocumentBytesHandler != null;
        }

        /**
         * If the intent is that the metadata and byte store be packaged in a zip
         * or similar and emitted via a single stream emitter.
         * <p>
         * This is basically a test that this is not an EmbeddedDocumentEmitterStore.
         *
         * @return
         */
        public boolean toBePackagedForStreamEmitter() {
            return !(embeddedDocumentBytesHandler instanceof EmittingEmbeddedDocumentBytesHandler);
        }

    @Override
    public String toString() {
        return "MetadataListAndEmbeddedBytes{" + "metadataList=" + metadataList + '}';
    }
}
