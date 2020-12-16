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
package org.apache.tika.fetcher;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.exception.NoFetcherAvailableException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.utils.ServiceLoaderUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public class DefaultFetcher implements Fetcher {

    private final List<Fetcher> fetchers;

    public DefaultFetcher() {
        this(new ServiceLoader());
    }

    public DefaultFetcher(ServiceLoader serviceLoader) {
        fetchers = serviceLoader.loadServiceProviders(Fetcher.class);
        ServiceLoaderUtils.sortLoadedClasses(fetchers);
    }

    public DefaultFetcher(List<Fetcher> fetchers) {
        this.fetchers = fetchers;
    }

    @Override
    public boolean canFetch(String url) {
        for (Fetcher fetcher : fetchers) {
            if (fetcher.canFetch(url)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<InputStream> fetch(String url, Metadata metadata) throws TikaException, IOException {
        for (Fetcher fetcher : fetchers) {
            if (fetcher.canFetch(url)) {
                return fetcher.fetch(url, metadata);
            }
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Fetcher fetcher : fetchers) {
            if (i++ > 0) {
                sb.append(", ");
            }
            sb.append(fetcher.getClass());
        }
        throw new NoFetcherAvailableException("No suitable fetcher found for: "
                + url + " in " + sb.toString());
    }

    public List<Fetcher> getFetchers() {
        return fetchers;
    }
}
