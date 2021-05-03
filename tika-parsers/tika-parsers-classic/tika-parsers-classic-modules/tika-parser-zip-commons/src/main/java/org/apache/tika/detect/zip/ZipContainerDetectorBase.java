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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.tika.mime.MediaType;

abstract class ZipContainerDetectorBase {


    /**
     * To be considered as an IPA file, it needs to match all of these
     */
    private static HashSet<Pattern> ipaEntryPatterns = new HashSet<Pattern>() {
        private static final long serialVersionUID = 6545295886322115362L;

        {
            add(Pattern.compile("^Payload/$"));
            add(Pattern.compile("^Payload/.*\\.app/$"));
            add(Pattern.compile("^Payload/.*\\.app/_CodeSignature/$"));
            add(Pattern.compile("^Payload/.*\\.app/_CodeSignature/CodeResources$"));
            add(Pattern.compile("^Payload/.*\\.app/Info\\.plist$"));
            add(Pattern.compile("^Payload/.*\\.app/PkgInfo$"));
        }
    };

    static MediaType detectJar(Set<String> entryNames) {
        if (entryNames.contains("META-INF/MANIFEST.MF")) {
            // It's a Jar file, or something based on Jar

            // Is it an Android APK?
            if (entryNames.contains("AndroidManifest.xml")) {
                return MediaType.application("vnd.android.package-archive");
            }

            // Check for WAR and EAR
            if (entryNames.contains("WEB-INF/")) {
                return MediaType.application("x-tika-java-web-archive");
            }
            if (entryNames.contains("META-INF/application.xml")) {
                return MediaType.application("x-tika-java-enterprise-archive");
            }

            // Looks like a regular Jar Archive
            return MediaType.application("java-archive");
        } else {
            // Some Android APKs miss the default Manifest
            if (entryNames.contains("AndroidManifest.xml")) {
                return MediaType.application("vnd.android.package-archive");
            }

            return null;
        }
    }

    static MediaType detectKmz(Set<String> entryFileNames) {
        //look for a single kml at the main level
        boolean kmlFound = false;
        for (String entryFileName : entryFileNames) {
            if (entryFileName.indexOf('/') != -1 || entryFileName.indexOf('\\') != -1) {
                continue;
            }
            if (entryFileName.endsWith(".kml") && !kmlFound) {
                kmlFound = true;
            } else {
                return null;
            }
        }
        if (kmlFound) {
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static MediaType detectIpa(Set<String> entryNames) {
        // Note - consider generalising this logic, if another format needs many regexp matching
        Set<Pattern> tmpPatterns = (Set<Pattern>) ipaEntryPatterns.clone();

        for (String entryName : entryNames) {
            Iterator<Pattern> ip = tmpPatterns.iterator();
            while (ip.hasNext()) {
                if (ip.next().matcher(entryName).matches()) {
                    ip.remove();
                }
            }
            if (tmpPatterns.isEmpty()) {
                // We've found everything we need to find
                return MediaType.application("x-itunes-ipa");
            }

        }
        return null;
    }


}
