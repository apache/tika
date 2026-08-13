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
package org.apache.tika.metadata.schema;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * TIKA-4794: no Tika-coined 4.x metadata key or open-namespace prefix may contain an underscore.
 * Underscores are the external standard's own spelling or nothing — and no bundle key currently has
 * a legitimate one, so {@link #ALLOWLIST} starts empty. Scans both committed registries; a stray
 * {@code _} (e.g. a missed kebab, a Java-enum name leaking into a key) fails the build.
 */
public class MetadataNoUnderscoreTest {

    /**
     * Keys/prefixes whose underscore is an external standard's spelling (verbatim).
     * {@code ClimateForecast} (tika-core) mints its 15 constants from the NCAR CCSM /
     * Climate Forecast convention (http://cf-pcmdi.llnl.gov/) attribute names as-is; 6 of
     * them carry the convention's own underscores. These are not Tika-coined, so renaming
     * them is out of scope here (it would also change the keys Tika has emitted since 0.x).
     *
     * <p>TIKA-4816 stage 5a (registry scope expansion) adds {@code sqlite3:application_id}
     * and {@code sqlite3:user_version} ({@code SQLite3Parser}): these are SQLite's own PRAGMA
     * names verbatim (sqlite.org/pragma.html#pragma_application_id,
     * #pragma_user_version) -- same "external standard's own spelling" category as
     * {@code ClimateForecast}, not Tika-coined.
     */
    private static final Set<String> ALLOWLIST = Set.of(
            "prg_ID", "cmd_ln", "table_id", "project_id", "experiment_id",
            "model_name_english", "sqlite3:application_id", "sqlite3:user_version");

    @Test
    public void noUnderscoreInTikaCoinedKeys() throws Exception {
        List<String> offenders = new ArrayList<>();
        collect("/org/apache/tika/metadata/metadata-keys.json", "key", offenders);
        collect("/org/apache/tika/metadata/metadata-open-namespaces.json", "prefix", offenders);
        assertTrue(offenders.isEmpty(),
                "Tika-coined 4.x keys/prefixes must not contain '_' (external-standard names excepted "
                        + "via ALLOWLIST): " + offenders);
    }

    private static void collect(String resource, String field, List<String> offenders) throws Exception {
        String json;
        try (InputStream in = MetadataNoUnderscoreTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                return;   // registry not present in this scope; nothing to check
            }
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String marker = '"' + field + "\":\"";
        int i = 0;
        while ((i = json.indexOf(marker, i)) >= 0) {
            i += marker.length();
            StringBuilder sb = new StringBuilder();
            while (i < json.length()) {
                char c = json.charAt(i++);
                if (c == '\\' && i < json.length()) {
                    sb.append(json.charAt(i++));
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                }
            }
            String value = sb.toString();
            if (value.indexOf('_') >= 0 && !ALLOWLIST.contains(value)) {
                offenders.add(value);
            }
        }
    }
}
