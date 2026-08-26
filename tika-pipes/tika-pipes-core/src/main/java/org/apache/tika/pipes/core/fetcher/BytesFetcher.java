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
package org.apache.tika.pipes.core.fetcher;

import java.io.IOException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.ComponentIds;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.utils.StringUtils;

/**
 * Serves the bytes in the {@link InlineBytes} parse-context entry -- set by the caller
 * in-process, or planted by the server from the {@code PipesRequest} envelope -- so a host that
 * already holds the content does not have to spool it to disk purely to hand it across the
 * process boundary.
 * <p>
 * The fetch key is not a location -- it carries the caller's filename, which is why the parent
 * no longer has to scrub a spool filename out of the returned metadata.
 * <p>
 * {@link org.apache.tika.pipes.core.fetcher.FetcherManager} hands out one instance per id and
 * requires thread safety, so the payload is read from the {@code parseContext} argument on every
 * call and never held as state.
 */
public class BytesFetcher implements Fetcher {

    /** Reserved: a request cannot name this, so only the host can route a parse through it. */
    public static final String FETCHER_ID = ComponentIds.SYSTEM_PREFIX + "bytes";

    @Override
    public TikaInputStream fetch(String fetchKey, Metadata metadata, ParseContext parseContext)
            throws TikaException, IOException {
        InlineBytes inline = parseContext == null ? null : parseContext.get(InlineBytes.class);
        if (inline == null || inline.getBytes() == null) {
            throw new IOException("no inline-bytes in the ParseContext; " + FETCHER_ID +
                    " is only usable on a request that carries its content inline");
        }
        if (!StringUtils.isBlank(fetchKey)
                && metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY) == null) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fetchKey);
        }
        return TikaInputStream.get(inline.getBytes(), metadata);
    }

    @Override
    public ExtensionConfig getExtensionConfig() {
        return new ExtensionConfig(FETCHER_ID, "bytes-fetcher", null);
    }
}
