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
package org.apache.tika.parser.strings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.Parser;

public class StringsConfigTest extends TikaTest {

    @Test
    public void testNoConfig() {
        StringsConfig config = new StringsConfig();
        assertEquals(StringsEncoding.SINGLE_7_BIT, config.getEncoding(),
                "Invalid default encoding value");
        assertEquals(4, config.getMinLength(), "Invalid default min-len value");
        assertEquals(120, config.getTimeoutSeconds(), "Invalid default timeout value");
    }

    @Test
    public void testPartialConfig() throws Exception {
        Parser parser = TikaLoader.load(
                        getConfigPath(StringsConfigTest.class, "tika-config-strings-partial.json"))
                .loadParsers();

        StringsParser p =
                (StringsParser) ((CompositeParser) parser).getAllComponentParsers()
                        .get(0);

        assertEquals(StringsEncoding.BIGENDIAN_16_BIT, p.getDefaultConfig().getEncoding(),
                "Invalid overridden encoding value");
        assertEquals(4, p.getDefaultConfig().getMinLength(), "Invalid default min-len value");
        assertEquals(60, p.getDefaultConfig().getTimeoutSeconds(), "Invalid overridden timeout value");
    }

    @Test
    public void testFullConfig() throws Exception {
        Parser parser = TikaLoader.load(
                        getConfigPath(StringsConfigTest.class, "tika-config-strings-full.json"))
                .loadParsers();

        StringsParser p =
                (StringsParser) ((CompositeParser) parser).getAllComponentParsers()
                        .get(0);
        assertEquals(StringsEncoding.BIGENDIAN_16_BIT, p.getDefaultConfig().getEncoding(),
                "Invalid overridden encoding value");
        assertEquals(3, p.getDefaultConfig().getMinLength(), "Invalid overridden min-len value");
        assertEquals(60, p.getDefaultConfig().getTimeoutSeconds(), "Invalid overridden timeout value");
    }

    @Test
    public void testValidateEconding() {
        StringsConfig config = new StringsConfig();
        assertThrows(IllegalArgumentException.class, () -> {
            config.setMinLength(0);
        });
    }
}
