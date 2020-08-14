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

import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;

import java.io.IOException;

public class JarDetector implements ZipDetector {
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
}
