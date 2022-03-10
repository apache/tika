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
package org.apache.tika.detect.zip;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;

public class FrictionlessPackageDetector implements ZipContainerDetector {

    private static final MediaType WACZ = MediaType.application("x-wacz");
    private static final MediaType DATA_PACKAGE = MediaType.application("x-vnd.datapackage+zip");

    private enum PARTS {
        PACKAGE,
        WACZ_ARCHIVE,
        WACZ_INDEXES,
        WACZ_PAGES
    }

    @Override
    public MediaType detect(ZipFile zip, TikaInputStream tis) throws IOException {

        Enumeration<ZipArchiveEntry> entries = zip.getEntries();
        MediaType mt = null;
        Counter counter = new Counter();
        while (entries.hasMoreElements()) {
            ZipArchiveEntry zae = entries.nextElement();
            updateCounter(zae, counter);
            mt = getMediaType(counter);
            if (mt == WACZ) {
                return WACZ;
            }
        }
        return getMediaType(counter);
    }

    @Override
    public MediaType streamingDetectUpdate(ZipArchiveEntry zae, InputStream zis,
                                           StreamingDetectContext detectContext) {
        Counter counter = detectContext.get(Counter.class);
        if (counter == null) {
            counter = new Counter();
            detectContext.set(Counter.class, counter);
        }
        updateCounter(zae, counter);
        MediaType mt = getMediaType(counter);
        if (mt == WACZ) {
            return WACZ;
        }
        return null;
    }

    private void updateCounter(ZipArchiveEntry zae, Counter counter) {
        String name = zae.getName();
        if (name.startsWith("archive/")) {
            counter.update(PARTS.WACZ_ARCHIVE);
        } else if (name.startsWith("indexes/")) {
            counter.update(PARTS.WACZ_INDEXES);
        } else if (name.startsWith("pages/")) {
            counter.update(PARTS.WACZ_PAGES);
        } else if ("datapackage.json".equals(name)) {
            counter.update(PARTS.PACKAGE);
        }
    }

    MediaType getMediaType(Counter counter) {
        if (counter == null) {
            return null;
        }
        if (counter.parts.contains(PARTS.PACKAGE)) {
            if (counter.parts.size() == 1) {
                return DATA_PACKAGE;
            }
            //this is, um, heuristic; I think all the parts are
            //required, but I'm not sure what we'll see in practice.
            if (counter.parts.contains(PARTS.WACZ_ARCHIVE)) {
                return WACZ;
            } else if (counter.parts.contains(PARTS.WACZ_INDEXES) &&
                    counter.parts.contains(PARTS.WACZ_PAGES)) {
                return WACZ;
            }
        }
        return null;
    }

    @Override
    public MediaType streamingDetectFinal(StreamingDetectContext detectContext) {
        return getMediaType(detectContext.get(Counter.class));
    }

    private static class Counter {
        private Set<PARTS> parts = new HashSet<>();
        void update(PARTS val) {
            parts.add(val);
        }
    }
}
