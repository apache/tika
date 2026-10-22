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
package org.apache.tika.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.tika.config.OperatorOnly;
import org.apache.tika.config.ParseContextConfig;
import org.apache.tika.parser.ParseContext;

/**
 * Per-request JSON may not name an {@link OperatorOnly} property; operator JSON may. The
 * refusal is by JSON key before any setter runs, through aliases, into nested objects, and
 * survives a subclass override that drops the annotation.
 */
public class OperatorOnlyGuardTest {

    public static class Inner {
        private String path = "default-inner";
        private int depth = 1;

        public String getPath() {
            return path;
        }

        @OperatorOnly
        public void setPath(String path) {
            this.path = path;
        }

        public int getDepth() {
            return depth;
        }

        public void setDepth(int depth) {
            this.depth = depth;
        }
    }

    public static class Config {
        static volatile boolean executableSetterRan;

        private String executable = "/usr/bin/default";
        private String language = "eng";
        private Inner inner = new Inner();

        public String getExecutable() {
            return executable;
        }

        @OperatorOnly
        @JsonAlias("binary")
        public void setExecutable(String executable) {
            executableSetterRan = true;
            this.executable = executable;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public Inner getInner() {
            return inner;
        }

        public void setInner(Inner inner) {
            this.inner = inner;
        }
    }

    /** An override without the annotation. */
    public static class SubConfig extends Config {
        @Override
        public void setExecutable(String executable) {
            super.setExecutable(executable);
        }
    }

    private static final String KEY = "my-parser";

    private static <T> T resolve(String json, boolean trusted, Class<T> clazz, T defaults)
            throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig(KEY, json, trusted);
        return ParseContextConfig.getConfig(context, KEY, clazz, defaults);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"executable\": \"/tmp/evil\"}", "{\"executable\": null}",
            "{\"binary\": \"/tmp/evil\"}", "{\"inner\": {\"depth\": 3, \"path\": \"/tmp/evil\"}}"})
    public void testPerRequestIsRefusedBeforeAnySetterRuns(String json) {
        Config.executableSetterRan = false;
        IOException e = assertThrows(IOException.class, () -> resolve(json, false, Config.class, new Config()));
        assertTrue(e.getMessage().contains("at runtime: per-request config for '" + KEY + "'"), e.getMessage());
        assertFalse(Config.executableSetterRan);
    }

    @Test
    public void testUnrelatedAndNestedPlainFieldsMerge() throws Exception {
        Config merged = resolve("{\"language\": \"fra\", \"inner\": {\"depth\": 3}}", false,
                Config.class, new Config());
        assertEquals("fra", merged.getLanguage());
        assertEquals(3, merged.getInner().getDepth());
        assertEquals("/usr/bin/default", merged.getExecutable());
    }

    @Test
    public void testOperatorConfigMaySetIt() throws Exception {
        Config merged = resolve("{\"executable\": \"/opt/bin/x\", \"inner\": {\"path\": \"p\"}}", true,
                Config.class, new Config());
        assertEquals("/opt/bin/x", merged.getExecutable());
        assertEquals("p", merged.getInner().getPath());
    }

    @Test
    public void testOverrideWithoutAnnotationStaysLocked() {
        assertThrows(IOException.class,
                () -> resolve("{\"executable\": \"/tmp/evil\"}", false, SubConfig.class, new SubConfig()));
    }

    @Test
    public void testUnknownKeyIsLeftToJackson() {
        IOException e = assertThrows(IOException.class, () -> resolve("{\"nope\": 1}", false, Config.class, new Config()));
        assertFalse(e.getMessage().contains("at runtime"), e.getMessage());
    }
}
