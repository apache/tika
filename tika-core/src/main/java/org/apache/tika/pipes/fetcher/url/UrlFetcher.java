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
package org.apache.tika.pipes.fetcher.url;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Locale;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.fetcher.AbstractFetcher;

/**
 * Simple fetcher for URLs. This simply calls {@link TikaInputStream#get(URL)}.
 * This intentionally does not support fetching for files.
 * Please use the FileSystemFetcher for that.  If you need more advanced control (passwords,
 * timeouts, proxies, etc), please use the tika-fetcher-http module.
 */
public class UrlFetcher extends AbstractFetcher {

    @Override
    public InputStream fetch(String fetchKey, Metadata metadata) throws IOException, TikaException {
        if (fetchKey.contains("\u0000")) {
            throw new IllegalArgumentException("URL must not contain \u0000. " +
                    "Please review the life decisions that led you to requesting " +
                    "a URL with this character in it.");
        }
        if (fetchKey.toLowerCase(Locale.US).startsWith("file:")) {
            throw new IllegalArgumentException(
                    "The UrlFetcher does not fetch from file shares; " +
                    "please use the FileSystemFetcher");
        }
        return TikaInputStream.get(new URL(fetchKey), metadata);
    }

}
