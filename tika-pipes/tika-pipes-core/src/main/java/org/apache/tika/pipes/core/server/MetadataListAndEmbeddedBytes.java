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
import org.apache.tika.extractor.UnpackHandler;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.core.extractor.EmittingUnpackHandler;
import org.apache.tika.pipes.core.extractor.FrictionlessUnpackHandler;
import org.apache.tika.pipes.core.extractor.TempFileUnpackHandler;

class MetadataListAndEmbeddedBytes {

        List<Metadata> metadataList;
        final UnpackHandler unpackHandler;

        public MetadataListAndEmbeddedBytes(List<Metadata> metadataList,
                                            UnpackHandler unpackHandler) {
            this.metadataList = metadataList;
            this.unpackHandler = unpackHandler;
        }

        public List<Metadata> getMetadataList() {
            return metadataList;
        }

        public void filter(MetadataFilter filter, ParseContext parseContext) throws TikaException {
            filter.filter(metadataList, parseContext);
        }

        public UnpackHandler getUnpackHandler() {
            return unpackHandler;
        }

        /**
         * This tests whether there's an unpack handler that may require
         * closing at the end of the parse.
         *
         * @return
         */
        public boolean hasUnpackHandler() {
            return unpackHandler != null;
        }

        /**
         * If the intent is that the metadata and byte store be packaged in a zip
         * or similar and emitted via a single stream emitter.
         * <p>
         * Returns false for:
         * - EmittingUnpackHandler: bytes are emitted individually during parsing
         * - TempFileUnpackHandler: bytes are zipped and emitted by PipesWorker.zipAndEmitEmbeddedFiles()
         * - FrictionlessUnpackHandler: bytes are emitted by PipesWorker.emitFrictionlessOutput()
         *
         * @return true if bytes need to be packaged and emitted, false if already handled
         */
        public boolean toBePackagedForStreamEmitter() {
            // EmittingUnpackHandler emits bytes individually during parsing
            // TempFileUnpackHandler collects bytes for zipping by PipesWorker
            // FrictionlessUnpackHandler collects bytes for Frictionless Data Package by PipesWorker
            // In all cases, the bytes are handled separately and don't need to be
            // packaged here
            return !(unpackHandler instanceof EmittingUnpackHandler) &&
                    !(unpackHandler instanceof TempFileUnpackHandler) &&
                    !(unpackHandler instanceof FrictionlessUnpackHandler);
        }

    @Override
    public String toString() {
        return "MetadataListAndEmbeddedBytes{" + "metadataList=" + metadataList + '}';
    }
}
