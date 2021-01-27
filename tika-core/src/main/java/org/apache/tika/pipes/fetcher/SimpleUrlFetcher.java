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
package org.apache.tika.pipes.fetcher;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.Set;

/**
 * This is a lightweight fetcher that uses Java's
 * {@link URL#openStream()}. Please consider a more
 * robust way to fetch URLs, e.g. Apache httpcomponents,
 * curl or wget...
 *
 * This is limited to http: and https: urls.  This does
 * not support the file:/// protocol.  See {@link FileSystemFetcher}.
 */
public class SimpleUrlFetcher extends AbstractFetcher {

    private static String NAME = "url";

    public SimpleUrlFetcher() {
        super(NAME);
    }


    @Override
    public InputStream fetch(String fetchKey, Metadata metadata)
            throws IOException, TikaException {
        URL url = new URL(fetchKey);
        if (! url.getProtocol().equals("http") &&
                ! url.getProtocol().equals("https") &&
                        ! url.getProtocol().equals("ftp")) {
            throw new TikaException("This fetcher only handles: http, https; NOT: "
                    + url.getProtocol());
        }
        return TikaInputStream.get(url, metadata);
    }

    public InputStream fetch(String fetchKey, long startRange, long endRange, Metadata metadata)
            throws IOException, TikaException {
        URL url = new URL(fetchKey);
        URLConnection connection = url.openConnection();
        connection.setRequestProperty("Range", "bytes="+startRange+"-"+endRange);
        metadata.set(HttpHeaders.CONTENT_LENGTH, Long.toString(endRange-startRange+1));
        TikaInputStream tis = TikaInputStream.get(connection.getInputStream());
        tis.getPath();
        return tis;
    }
}
