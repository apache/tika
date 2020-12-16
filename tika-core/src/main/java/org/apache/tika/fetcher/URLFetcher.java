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

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Optional;

public class URLFetcher implements Fetcher {

    private static final String HTTP_PREFIX = "http:";
    private static final String HTTPS_PREFIX = "https:";
    private static final String FTP_PREFIX = "ftp:";

    @Override
    public boolean canFetch(String url) {
        if (url.startsWith(HTTP_PREFIX) ||
            url.startsWith(HTTPS_PREFIX) ||
            url.startsWith(FTP_PREFIX)) {
            return true;
        }
        return false;
    }

    @Override
    public Optional<InputStream> fetch(String url, Metadata metadata)
            throws TikaException, IOException {
        return Optional.of(TikaInputStream.get(new URL(url)));
    }
}
