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
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class MemoryURLStreamHandler extends URLStreamHandler {

    private static final AtomicInteger counter = new AtomicInteger();

    private static final List<MemoryURLStreamRecord> records =
        new LinkedList<MemoryURLStreamRecord>();

    public static URL createURL(byte[] data) {
        try {
            int i = counter.incrementAndGet();
            URL url =  new URL("tika-in-memory", "localhost", "/" + i);

            MemoryURLStreamRecord record = new MemoryURLStreamRecord();
            record.url = new WeakReference<URL>(url);
            record.data = data;
            records.add(record);

            return url;
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        Iterator<MemoryURLStreamRecord> iterator = records.iterator();
        while (iterator.hasNext()) {
            MemoryURLStreamRecord record = iterator.next();
            URL url = record.url.get();
            if (url == null) {
                iterator.remove();
            } else if (url == u) {
                return new MemoryURLConnection(u, record.data);
            }
        }
        throw new IOException("Unknown URL: " + u);
    }

}
