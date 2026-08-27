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
import java.util.regex.Pattern;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;

/**
 * Detects the zip-based GeoGebra formats by their well-known entry names:
 * <ul>
 *   <li>{@code geogebra.xml} at the root &rarr; a worksheet (*.ggb)</li>
 *   <li>{@code structure.json} at the root plus at least one
 *       {@code _slide&lt;N&gt;/geogebra.xml} &rarr; GeoGebra Notes/Slides (*.ggs)</li>
 *   <li>{@code geogebra_macro.xml} at the root &rarr; a tool (*.ggt)</li>
 * </ul>
 * A worksheet with macros contains both {@code geogebra.xml} and
 * {@code geogebra_macro.xml}, so the worksheet check takes precedence over
 * the tool check, and the decision is only made once all entry names have
 * been seen.
 */
public class GeoGebraDetector implements ZipContainerDetector {

    private static final MediaType GGB = MediaType.application("vnd.geogebra.file");
    private static final MediaType GGS = MediaType.application("vnd.geogebra.slides");
    private static final MediaType GGT = MediaType.application("vnd.geogebra.tool");

    private static final String GEOGEBRA_XML = "geogebra.xml";
    private static final String MACRO_XML = "geogebra_macro.xml";
    private static final String STRUCTURE_JSON = "structure.json";

    private static final Pattern SLIDE_XML_PATTERN =
            Pattern.compile("^_slide\\d+/geogebra\\.xml$");

    @Override
    public MediaType detect(ZipFile zip, TikaInputStream tis) throws IOException {
        //this runs for every zip Tika sees: look the root names up and only
        //walk the entries for a slide when there is a structure.json
        Names names = new Names();
        names.geogebraXml = zip.getEntry(GEOGEBRA_XML) != null;
        names.macroXml = zip.getEntry(MACRO_XML) != null;
        names.structureJson = zip.getEntry(STRUCTURE_JSON) != null;
        if (names.structureJson) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (!names.slideXml && entries.hasMoreElements()) {
                names.slideXml = isSlideXml(entries.nextElement().getName());
            }
        }
        return names.decide();
    }

    @Override
    public MediaType streamingDetectUpdate(ZipArchiveEntry zae, InputStream zis,
                                           StreamingDetectContext detectContext) {
        Names names = detectContext.get(Names.class);
        if (names == null) {
            names = new Names();
            detectContext.set(Names.class, names);
        }
        names.update(zae.getName());
        return null;
    }

    @Override
    public MediaType streamingDetectFinal(StreamingDetectContext detectContext) {
        Names names = detectContext.get(Names.class);
        return names == null ? null : names.decide();
    }

    private static boolean isSlideXml(String name) {
        return SLIDE_XML_PATTERN.matcher(name).matches();
    }

    private static class Names {
        private boolean geogebraXml;
        private boolean macroXml;
        private boolean structureJson;
        private boolean slideXml;

        void update(String name) {
            if (GEOGEBRA_XML.equals(name)) {
                geogebraXml = true;
            } else if (MACRO_XML.equals(name)) {
                macroXml = true;
            } else if (STRUCTURE_JSON.equals(name)) {
                structureJson = true;
            } else if (isSlideXml(name)) {
                slideXml = true;
            }
        }

        MediaType decide() {
            if (structureJson && slideXml) {
                return GGS;
            }
            if (geogebraXml) {
                return GGB;
            }
            if (macroXml) {
                return GGT;
            }
            return null;
        }
    }
}
