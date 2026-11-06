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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;

/**
 * Application packages recognized by their entry names alone: USDZ, MuseScore, compressed
 * MusicXML, CorelDRAW (zip), Power BI, FreeCAD, SMART Notebook, Anki, Fritzing, Scratch,
 * LEGO Digital Designer and App Inventor.
 */
public class AppPackageDetector implements ZipContainerDetector {

    static final MediaType USDZ = MediaType.parse("model/vnd.usdz+zip");
    static final MediaType MUSESCORE = MediaType.application("x-musescore");
    static final MediaType MUSICXML = MediaType.application("vnd.recordare.musicxml");
    static final MediaType CORELDRAW_ZIP = MediaType.application("x-vnd.corel.draw.document+zip");
    static final MediaType POWER_BI = MediaType.application("x-powerbi");
    static final MediaType FREECAD = MediaType.application("x-extension-fcstd");
    static final MediaType SMART_NOTEBOOK = MediaType.application("x-smarttech-notebook");
    static final MediaType ANKI = MediaType.application("x-anki-package");
    static final MediaType FRITZING = MediaType.application("x-fritzing-bundle");
    static final MediaType SCRATCH = MediaType.application("x-scratch-project");
    static final MediaType LEGO_LXF = MediaType.application("x-lego-lxf");
    static final MediaType APP_INVENTOR = MediaType.application("x-appinventor-project");

    private static final int MAX_NAMES = 10000;

    private static final int SNIFF_BYTES = 4096;

    private static final int MAX_SNIFFS = 16;

    private static final Pattern MUSICXML_ROOT = Pattern.compile("<score-(?:partwise|timewise)\\b");

    // Scratch 2 numbers its assets; Scratch 3 names them by md5
    private static final Pattern SCRATCH_PROJECT = Pattern.compile("\"(?:targets|objName)\"");

    private static final Pattern SCRATCH_ASSET =
            Pattern.compile("(?:[0-9]+|[0-9a-f]{32})\\.(?:png|svg|jpg|jpeg|gif|bmp|wav|mp3)");

    @Override
    public MediaType detect(ZipFile zip, TikaInputStream tis) throws IOException {
        Names names = new Names();
        Enumeration<ZipArchiveEntry> entries = zip.getEntriesInPhysicalOrder();
        while (entries.hasMoreElements() && names.list.size() < MAX_NAMES) {
            ZipArchiveEntry entry = entries.nextElement();
            names.add(entry);
            if (names.sniffed.size() < MAX_SNIFFS && sniffs(entry.getName())) {
                try (InputStream is = zip.getInputStream(entry)) {
                    names.sniffed.put(entry.getName(), head(is));
                } catch (IOException e) {
                    //swallow
                }
            }
        }
        return detect(names);
    }

    @Override
    public MediaType streamingDetectUpdate(ZipArchiveEntry zae, InputStream zis,
                                           StreamingDetectContext detectContext) {
        Names names = detectContext.get(Names.class);
        if (names == null) {
            names = new Names();
            detectContext.set(Names.class, names);
        }
        if (names.list.size() < MAX_NAMES) {
            names.add(zae);
            if (names.sniffed.size() < MAX_SNIFFS && sniffs(zae.getName())) {
                try {
                    names.sniffed.put(zae.getName(), head(zis));
                } catch (IOException e) {
                    //swallow
                }
            }
        }
        return null;
    }

    @Override
    public MediaType streamingDetectFinal(StreamingDetectContext detectContext) {
        Names names = detectContext.get(Names.class);
        return names == null ? null : detect(names);
    }

    // root XML files (MusicXML score) and project.json (Scratch) are checked by content
    private static boolean sniffs(String name) {
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            return false;
        }
        String lc = name.toLowerCase(Locale.ROOT);
        return lc.equals("project.json") || lc.endsWith(".xml") || lc.endsWith(".musicxml");
    }

    private static String head(InputStream is) throws IOException {
        return new String(is.readNBytes(SNIFF_BYTES), StandardCharsets.ISO_8859_1);
    }

    static MediaType detect(Names found) {
        List<String> entryNames = found.list;
        if (entryNames.isEmpty()) {
            return null;
        }
        Set<String> names = new HashSet<>();
        Set<String> rootExts = new HashSet<>();
        boolean opf = false;
        boolean annotationMetadata = false;
        boolean scratchAssetsOnly = true;
        for (String raw : entryNames) {
            String name = raw.replace('\\', '/');
            names.add(name);
            String lc = name.toLowerCase(Locale.ROOT);
            opf |= lc.endsWith(".opf");
            annotationMetadata |= name.startsWith("annotationmetadata/");
            if (name.indexOf('/') < 0) {
                int dot = lc.lastIndexOf('.');
                if (dot > 0) {
                    rootExts.add(lc.substring(dot));
                }
                if (!name.equals("project.json") && !SCRATCH_ASSET.matcher(lc).matches()) {
                    scratchAssetsOnly = false;
                }
            } else {
                scratchAssetsOnly = false;
            }
        }
        // the package's first file is its root USD layer, stored uncompressed
        String first = entryNames.get(0).toLowerCase(Locale.ROOT);
        if (found.firstStored && first.indexOf('/') < 0
                && (first.endsWith(".usdc") || first.endsWith(".usda") || first.endsWith(".usd"))) {
            return USDZ;
        }
        if (names.contains("META-INF/container.xml")) {
            if (rootExts.contains(".mscx")) {
                return MUSESCORE;
            }
            // EPUB and IDML share META-INF/container.xml; a MusicXML package holds a score
            if (!opf && found.anySniff(n -> !n.equals("project.json"), MUSICXML_ROOT)) {
                return MUSICXML;
            }
        }
        if (names.contains("content/riffData.cdr")) {
            return CORELDRAW_ZIP;
        }
        if (names.contains("Version") && names.contains("Report/Layout")
                && names.contains("DiagramLayout")) {
            return POWER_BI;
        }
        if (names.contains("Document.xml")
                && (names.contains("GuiDocument.xml") || rootExts.contains(".brp"))) {
            return FREECAD;
        }
        if (names.contains("imsmanifest.xml") && names.contains("settings.xml")
                && names.contains("metadata.xml") && annotationMetadata) {
            return SMART_NOTEBOOK;
        }
        if (names.contains("collection.anki2") || names.contains("collection.anki21")
                || names.contains("collection.anki21b")) {
            return ANKI;
        }
        if (rootExts.contains(".fz") && rootExts.contains(".fzp")) {
            return FRITZING;
        }
        if (names.contains("project.json") && scratchAssetsOnly && names.size() > 1
                && found.anySniff(n -> n.equals("project.json"), SCRATCH_PROJECT)) {
            return SCRATCH;
        }
        if (names.contains("IMAGE100.LXFML")) {
            return LEGO_LXF;
        }
        if (names.contains("youngandroidproject/project.properties")) {
            return APP_INVENTOR;
        }
        return null;
    }

    static class Names {
        private final List<String> list = new ArrayList<>();
        private final Map<String, String> sniffed = new HashMap<>();
        private boolean firstStored;

        void add(ZipArchiveEntry entry) {
            if (list.isEmpty()) {
                firstStored = entry.getMethod() == ZipArchiveEntry.STORED;
            }
            list.add(entry.getName());
        }

        boolean anySniff(Predicate<String> which, Pattern pattern) {
            for (Map.Entry<String, String> e : sniffed.entrySet()) {
                if (which.test(e.getKey()) && pattern.matcher(e.getValue()).find()) {
                    return true;
                }
            }
            return false;
        }
    }
}
