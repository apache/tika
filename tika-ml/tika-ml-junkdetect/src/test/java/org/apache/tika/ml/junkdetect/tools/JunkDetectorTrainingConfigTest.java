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
package org.apache.tika.ml.junkdetect.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Pin-test for {@link JunkDetectorTrainingConfig}.
 *
 * <p>The values exercised here are the durable choices that define the
 * shipping junk-detector model's identity.  This test exists so that any
 * change to those values requires updating an assertion in the same
 * commit, surfacing the change in code review rather than letting it
 * slip silently.
 *
 * <p>If you are intentionally tuning a parameter, update both the
 * constant and the matching assertion below in the same change.  Do not
 * "fix" a failing assertion in isolation.
 */
class JunkDetectorTrainingConfigTest {

    @Test
    void corpusBuildValues() {
        assertEquals(500_000_000L,
                JunkDetectorTrainingConfig.TOTAL_BUDGET_BYTES);
        assertEquals(5_000_000L,
                JunkDetectorTrainingConfig.PER_LANGUAGE_CAP_BYTES);
        assertEquals(0.05,
                JunkDetectorTrainingConfig.MIN_TARGET_SCRIPT_FRAC, 1e-9);
        assertEquals(50,
                JunkDetectorTrainingConfig.MIN_BYTES_PER_SENTENCE);
        assertEquals(0.30,
                JunkDetectorTrainingConfig.MAX_PUNC_FRAC, 1e-9);
        assertEquals(500,
                JunkDetectorTrainingConfig.MIN_DEV_SENTENCES);
        assertEquals(2_000,
                JunkDetectorTrainingConfig.SCRIPT_SAMPLE_LINES);
        assertEquals(200_000L,
                JunkDetectorTrainingConfig.ENTROPY_SAMPLE_BYTES);
        assertEquals(42,
                JunkDetectorTrainingConfig.SEED);
    }

    @Test
    void droppedScripts() {
        Set<String> drop = JunkDetectorTrainingConfig.DROP_SCRIPTS;
        assertEquals(Set.of("GOTHIC", "THAANA"), drop);
        // Must be immutable: any caller that tries to mutate the set
        // should fail loudly rather than corrupting the shared config.
        assertThrows(UnsupportedOperationException.class,
                () -> drop.add("FAKE"));
    }

    @Test
    void scriptBudgetOverridesEmpty() {
        // v7 hypothesis test (HAN=60MB) ran but gave only marginal gains.
        // Override map is intentionally empty pending a more decisive
        // experiment.
        assertTrue(JunkDetectorTrainingConfig.SCRIPT_BUDGET_OVERRIDES.isEmpty());
    }

    @Test
    void modelTrainValues() {
        assertEquals(3, JunkDetectorTrainingConfig.MIN_BIGRAM_COUNT);
        assertEquals(0.5, JunkDetectorTrainingConfig.OA_LOAD_FACTOR, 1e-9);
        assertEquals(16, JunkDetectorTrainingConfig.KEY_INDEX_BITS);
        assertTrue(JunkDetectorTrainingConfig.KEY_INDEX_BITS <= 16,
                "KEY_INDEX_BITS must be <= 16 to fit packed key in an int");
    }

    @Test
    void notInstantiable() {
        // The class is a frozen configuration container; making it
        // instantiable would invite per-call mutation.
        java.lang.reflect.Constructor<?>[] ctors =
                JunkDetectorTrainingConfig.class.getDeclaredConstructors();
        assertEquals(1, ctors.length, "expected exactly one constructor");
        assertFalse(java.lang.reflect.Modifier.isPublic(ctors[0].getModifiers()),
                "constructor should not be public");
    }
}
