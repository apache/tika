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

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;

public class JarDetector implements ZipContainerDetector {

    private static SeenManifest SEEN_MANIFEST = new SeenManifest();

    @Override
    public MediaType detect(ZipFile zip, TikaInputStream tis) throws IOException {
        if (zip.getEntry("META-INF/MANIFEST.MF") != null) {
            // It's a Jar file, or something based on Jar

            // Is it an Android APK?
            if (zip.getEntry("AndroidManifest.xml") != null) {
                return MediaType.application("vnd.android.package-archive");
            }

            // Check for WAR and EAR
            if (zip.getEntry("WEB-INF/") != null) {
                return MediaType.application("x-tika-java-web-archive");
            }
            if (zip.getEntry("META-INF/application.xml") != null) {
                return MediaType.application("x-tika-java-enterprise-archive");
            }

            // Looks like a regular Jar Archive
            return MediaType.application("java-archive");
        } else {
            // Some Android APKs miss the default Manifest
            if (zip.getEntry("AndroidManifest.xml") != null) {
                return MediaType.application("vnd.android.package-archive");
            }

            return null;
        }
    }

    @Override
    public MediaType streamingDetectUpdate(ZipArchiveEntry zae, InputStream zis,
                                           StreamingDetectContext detectContext) {

        String name = zae.getName();
        if (name.equals("AndroidManifest.xml")) {
            return MediaType.application("vnd.android.package-archive");
        } else if (name.equals("META-INF/MANIFEST.MF")) {
            // It's a Jar file, or something based on Jar
            detectContext.set(SeenManifest.class, SEEN_MANIFEST);
        }
        SeenManifest seenManifest = detectContext.get(SeenManifest.class);

        if (seenManifest != null) {
            if (name.equals("AndroidManifest.xml")) {
                // Is it an Android APK?
                return MediaType.application("vnd.android.package-archive");
            } else if (name.equals("WEB-INF/")) {
                // Check for WAR and EAR
                return MediaType.application("x-tika-java-web-archive");
            }
            if (name.equals("META-INF/application.xml")) {
                return MediaType.application("x-tika-java-enterprise-archive");
            }
        }
        return null;

    }

    @Override
    public MediaType streamingDetectFinal(StreamingDetectContext detectContext) {
        if (detectContext.get(SeenManifest.class) != null) {
            // Looks like a regular Jar Archive
            return MediaType.application("java-archive");

        }
        return null;
    }

    private static class SeenManifest {
    }
}
