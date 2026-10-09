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
package org.apache.tika.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.test.OperatorOnlyAudit;

/** The audit reports an unannotated host-reaching setter and nothing else from this class. */
public class OperatorOnlyAuditTest {

    public static class UnguardedConfig {
        public void setToolPath(String path) {
        }

        public void setLanguage(String language) {
        }

        @OperatorOnly
        public void setApiKey(String key) {
        }

        public void setModel(String model) {
        }

        @OperatorOnly
        public String getModel() {
            return null;
        }
    }

    @Test
    public void testReportsOnlyTheUnannotatedSensitiveSetter() throws Exception {
        List<String> violations = OperatorOnlyAudit.unannotatedSensitiveSetters(List.of(), List.of());
        // the one unguarded setter here, and nothing from tika-core itself
        assertEquals(List.of(UnguardedConfig.class.getName() + "#setToolPath"), violations);
    }

    @Test
    public void testAllowlistSilencesAReviewedSetter() throws Exception {
        String reviewed = UnguardedConfig.class.getName() + "#setToolPath";
        assertFalse(OperatorOnlyAudit.unannotatedSensitiveSetters(List.of(), List.of(reviewed))
                .contains(reviewed));
    }
}
