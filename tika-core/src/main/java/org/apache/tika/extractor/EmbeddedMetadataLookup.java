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

import org.apache.tika.config.TransientParseState;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;

/**
 * Finds the metadata kept for an embedded document after it has ended. The recursive parser
 * wrapper sets it for the duration of the top-level parse; a late writer (inference at the
 * end of the document) resolves its targets through it, because the wrapper keeps a copy of
 * each embedded document's metadata and the parser's own object is no longer read.
 *
 * @since Apache Tika 4.1
 */
public class EmbeddedMetadataLookup implements TransientParseState {

    private final AbstractRecursiveParserWrapperHandler handler;

    public EmbeddedMetadataLookup(AbstractRecursiveParserWrapperHandler handler) {
        this.handler = handler;
    }

    /** The kept metadata for the id path, or null when none is kept. */
    public Metadata kept(String idPath) {
        return idPath == null ? null : handler.getEmbeddedMetadata(idPath);
    }
}
