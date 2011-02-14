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
package org.apache.tika.fork;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

class MemoryURLStreamHandler extends URLStreamHandler {

    private static final AtomicInteger counter = new AtomicInteger();

    private static final Map<URL, byte[]> URLs = new WeakHashMap<URL, byte[]>();

    public static URL createURL(byte[] data) {
        try {
            int i = counter.incrementAndGet();
            URL url = new URL("tika-in-memory", "localhost", "/" + i);
            URLs.put(url, data);
            return url;
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        byte[] data = URLs.get(u);
        if (data != null) {
            return new MemoryURLConnection(u, URLs.get(u));
        } else {
            throw new IOException("Unknown URL: " + u);
        }
    }

}
