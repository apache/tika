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
package org.apache.tika.metadata.filter;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

public abstract class MetadataFilter implements Serializable, Closeable {

    /**
     * Filters the metadata list in place using per-request context.
     * The list and the metadata objects within it may be modified.
     * Callers must pass a mutable list and should make a defensive
     * copy before calling if the original data must be preserved.
     *
     * @param metadataList the list to filter (must be mutable)
     * @param parseContext per-request context (e.g. skip flags, runtime config)
     * @throws TikaException if filtering fails
     */
    public abstract void filter(List<Metadata> metadataList, ParseContext parseContext)
            throws TikaException;

    /**
     * Convenience overload for callers that have no per-request context.
     * Delegates to {@link #filter(List, ParseContext)} with an empty context.
     */
    public void filter(List<Metadata> metadataList) throws TikaException {
        filter(metadataList, new ParseContext());
    }

    /**
     * Releases any resources held by this filter (e.g. HTTP connection pools,
     * thread pools). The default implementation is a no-op; filters that hold
     * long-lived resources should override this.
     * <p>
     * Callers that control the filter lifecycle (e.g. {@code PipesServer},
     * try-with-resources blocks) should call this when the filter is no longer
     * needed.
     */
    @Override
    public void close() throws IOException {
    }
}
