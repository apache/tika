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

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;

import java.io.IOException;
import java.util.Enumeration;

public class KMZDetector implements ZipDetector {
    @Override
    public MediaType detect(ZipFile zip, TikaInputStream tis) throws IOException {
        boolean kmlFound = false;

        Enumeration<ZipArchiveEntry> entries = zip.getEntries();
        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!entry.isDirectory()
                    && name.indexOf('/') == -1 && name.indexOf('\\') == -1) {
                if (name.endsWith(".kml") && !kmlFound) {
                    kmlFound = true;
                } else {
                    return null;
                }
            }
        }

        if (kmlFound) {
            return MediaType.application("vnd.google-earth.kmz");
        } else {
            return null;
        }
    }
}
