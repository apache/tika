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

    @Override
    public MediaType detect(ZipFile zip, TikaInputStream tis) throws IOException {
        Names names = new Names();
        Enumeration<ZipArchiveEntry> entries = zip.getEntries();
        while (entries.hasMoreElements()) {
            names.update(entries.nextElement().getName());
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

    private static class Names {
        private boolean geogebraXml;
        private boolean macroXml;
        private boolean structureJson;
        private boolean slideXml;

        void update(String name) {
            if ("geogebra.xml".equals(name)) {
                geogebraXml = true;
            } else if ("geogebra_macro.xml".equals(name)) {
                macroXml = true;
            } else if ("structure.json".equals(name)) {
                structureJson = true;
            } else if (name.startsWith("_slide") && name.endsWith("/geogebra.xml")) {
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
