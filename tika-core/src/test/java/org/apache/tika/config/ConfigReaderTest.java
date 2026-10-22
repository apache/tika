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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.parser.ParseContext;

public class ConfigReaderTest {

    private final List<Object> references = new ArrayList<>();

    @AfterEach
    public void unregister() {
        references.forEach(ServiceLoader::removeService);
    }

    @Test
    public void testAbsentWithoutTikaSerialization() {
        assertNull(ConfigReader.find());
    }

    @Test
    public void testFindsReaderRegisteredLate() {
        assertNull(ConfigReader.find());
        ConfigReader reader = new StubReader();
        register(reader);
        assertSame(reader, ConfigReader.find());
    }

    @Test
    public void testRefusesTwoReaders() {
        register(new StubReader());
        register(new OtherStubReader());
        assertThrows(IllegalStateException.class, ConfigReader::find);
    }

    private void register(ConfigReader reader) {
        Object reference = new Object();
        references.add(reference);
        ServiceLoader.addService(reference, reader, 0);
    }

    private static class StubReader implements ConfigReader {
        @Override
        public <T> T readConfig(String json, Class<T> configClass, boolean trusted) {
            return null;
        }

        @Override
        public <T> T getConfig(ParseContext context, String configKey, Class<T> configClass,
                               T defaultConfig) {
            return defaultConfig;
        }
    }

    private static class OtherStubReader extends StubReader {
    }
}
