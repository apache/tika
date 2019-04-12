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
package org.apache.tika.parser.pkg;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.tika.mime.MediaType;

abstract class ZipContainerDetectorBase {


    static final MediaType TIKA_OOXML = MediaType.application("x-tika-ooxml");
    static final MediaType DOCX =
            MediaType.application("vnd.openxmlformats-officedocument.wordprocessingml.document");
    static final MediaType DOCM =
            MediaType.application("vnd.ms-word.document.macroEnabled.12");
    static final MediaType DOTX =
            MediaType.application("vnd.ms-word.document.macroEnabled.12");
    static final MediaType PPTX =
            MediaType.application("vnd.openxmlformats-officedocument.presentationml.presentation");
    static final MediaType PPSM =
            MediaType.application("vnd.ms-powerpoint.slideshow.macroEnabled.12");
    static final MediaType PPSX =
            MediaType.application("vnd.openxmlformats-officedocument.presentationml.slideshow");
    static final MediaType PPTM =
            MediaType.application("vnd.ms-powerpoint.presentation.macroEnabled.12");
    static final MediaType POTM =
            MediaType.application("vnd.ms-powerpoint.template.macroenabled.12");
    static final MediaType POTX =
            MediaType.application("vnd.openxmlformats-officedocument.presentationml.template");
    static final MediaType THMX =
            MediaType.application("vnd.openxmlformats-officedocument");
    static final MediaType XLSB =
            MediaType.application("vnd.ms-excel.sheet.binary.macroenabled.12");
    static final MediaType XLSX =
            MediaType.application("vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    static final MediaType XLSM =
            MediaType.application("vnd.ms-excel.sheet.macroEnabled.12");
    static final MediaType XPS =
            MediaType.application("vnd.ms-xpsdocument");

    static final Set<String> OOXML_HINTS = fillSet(
            "word/document.xml",
            "_rels/.rels",
            "[Content_Types].xml",
            "ppt/presentation.xml",
            "ppt/slides/slide1.xml",
            "xl/workbook.xml",
            "xl/sharedStrings.xml",
            "xl/worksheets/sheet1.xml"
    );

    private static Set<String> fillSet(String ... args) {
        Set<String> tmp = new HashSet<>();
        for (String arg : args) {
            tmp.add(arg);
        }
        return Collections.unmodifiableSet(tmp);
    }

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
            if (entryFileName.indexOf('/') != -1
                    || entryFileName.indexOf('\\') != -1) {
                continue;
            }
            if (entryFileName.endsWith(".kml") && !kmlFound) {
                kmlFound = true;
            } else {
                return null;
            }
        }
        if (kmlFound) {
            return MediaType.application("vnd.google-earth.kmz");
        }
        return null;
    }

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
        }};
    @SuppressWarnings("unchecked")
    static MediaType detectIpa(Set<String> entryNames) {
        // Note - consider generalising this logic, if another format needs many regexp matching
        Set<Pattern> tmpPatterns = (Set<Pattern>)ipaEntryPatterns.clone();

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
