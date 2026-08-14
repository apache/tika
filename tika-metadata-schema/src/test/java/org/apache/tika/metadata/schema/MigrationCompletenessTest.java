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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.filter.LegacyKeyMigrationFilter;

/**
 * The completeness half of the migration gate (TIKA-4816 round-3 review):
 * {@code MetadataMigrationTableTest} proves the committed table matches its inputs, but is
 * structurally blind to 3.x keys that produce <em>no</em> row at all — the field-identity join
 * silently emits nothing for a key whose field was deleted, so every future deletion would
 * silently fall out of the bridge (as {@code cp:subject} and
 * {@code Iptc4xmpExt:DigitalSourcefileType} did). This test closes that hole: every key in the
 * 3.x snapshot must be accounted for — surviving into 4.x, bridged or DROPPED by a table row
 * (egress-only rows count: they are deliberate adjudications), rewritten by a filter prefix
 * rule, or allowlisted here with a reason.
 */
public class MigrationCompletenessTest {

    private static final String THREE_X = "/org/apache/tika/metadata/metadata-keys-3x.json";
    private static final String FOUR_X = "/org/apache/tika/metadata/metadata-key-fields.json";
    private static final String FOUR_X_KEYS = "/org/apache/tika/metadata/metadata-keys.json";
    private static final String OVERLAY = "/org/apache/tika/metadata/migration-overlay.tsv";

    private static final Pattern KEY_FIELD = Pattern.compile("\"key\":\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern V3_FIELD = Pattern.compile("\\{\"v3\":\"((?:[^\"\\\\]|\\\\.)*)\"");

    /** Deliberately unaccounted 3.x keys, each with the reason a bridge/DROP row is wrong. */
    private static final Map<String, String> ALLOWLIST = Map.of();

    @Test
    public void everyThreeXKeyIsAccountedFor() throws Exception {
        Set<String> threeX = extract(read(THREE_X), KEY_FIELD);
        Set<String> fourX = extract(read(FOUR_X_KEYS), KEY_FIELD);
        String regenerated = MigrationTableGenerator.generate(read(THREE_X), read(FOUR_X),
                read(FOUR_X_KEYS), read(OVERLAY));
        Set<String> bridged = extract(regenerated, V3_FIELD);

        List<String> unaccounted = new ArrayList<>();
        for (String key : threeX) {
            if (fourX.contains(key) || bridged.contains(key) || ALLOWLIST.containsKey(key)
                    || rewrittenByAPrefixRule(key)) {
                continue;
            }
            unaccounted.add(key);
        }
        unaccounted.sort(String::compareTo);
        assertEquals(List.of(), unaccounted,
                "3.x keys with no 4.x survivor, no migration-table row, no filter rule, and no "
                        + "allowlist entry: adjudicate each in migration-overlay.tsv (bridge it, "
                        + "mark it DROPPED/EGRESS_ONLY) or allowlist it here with a reason");
    }

    /** True if either direction of the bundled LegacyKeyMigrationFilter rewrites or drops it. */
    private static boolean rewrittenByAPrefixRule(String key) throws Exception {
        for (LegacyKeyMigrationFilter.Direction direction :
                LegacyKeyMigrationFilter.Direction.values()) {
            LegacyKeyMigrationFilter.Config config = new LegacyKeyMigrationFilter.Config();
            config.direction = direction;
            LegacyKeyMigrationFilter filter = new LegacyKeyMigrationFilter(config);
            Metadata m = new Metadata();
            m.setTrusted(key, "probe");            // 3.x keys may be reserved (X-TIKA:*)
            List<Metadata> list = new ArrayList<>();
            list.add(m);
            filter.filter(list);
            if (!"probe".equals(m.get(key))) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> extract(String content, Pattern pattern) {
        Set<String> keys = new HashSet<>();
        Matcher m = pattern.matcher(content);
        while (m.find()) {
            keys.add(m.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
        }
        return keys;
    }

    private static String read(String resource) throws Exception {
        try (InputStream in = MigrationCompletenessTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing resource on the test classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
