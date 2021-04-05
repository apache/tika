/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.strings;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;

import org.junit.Test;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.parser.CompositeParser;

public class StringsConfigTest {

    @Test
    public void testNoConfig() {
        StringsConfig config = new StringsConfig();
        assertEquals("Invalid default encoding value", StringsEncoding.SINGLE_7_BIT,
                config.getEncoding());
        assertEquals("Invalid default min-len value", 4, config.getMinLength());
        assertEquals("Invalid default timeout value", 120, config.getTimeoutSeconds());
    }

    @Test
    public void testPartialConfig() throws Exception {
        TikaConfig tikaConfig = null;
        try (InputStream stream = StringsConfigTest.class
                .getResourceAsStream("/test-configs/tika-config-strings-partial.xml")) {
            tikaConfig = new TikaConfig(stream);

        }
        StringsParser p =
                (StringsParser) ((CompositeParser) tikaConfig.getParser()).getAllComponentParsers()
                        .get(0);

        assertEquals("Invalid overridden encoding value", StringsEncoding.BIGENDIAN_16_BIT,
                p.getStringsEncoding());
        assertEquals("Invalid default min-len value", 4, p.getMinLength());
        assertEquals("Invalid overridden timeout value", 60, p.getTimeoutSeconds());
    }

    @Test
    public void testFullConfig() throws Exception {
        TikaConfig tikaConfig = null;
        try (InputStream stream = StringsConfigTest.class
                .getResourceAsStream("/test-configs/tika-config-strings-full.xml")) {
            tikaConfig = new TikaConfig(stream);

        }
        StringsParser p =
                (StringsParser) ((CompositeParser) tikaConfig.getParser()).getAllComponentParsers()
                        .get(0);
        assertEquals("Invalid overridden encoding value", StringsEncoding.BIGENDIAN_16_BIT,
                p.getStringsEncoding());
        assertEquals("Invalid overridden min-len value", 3, p.getMinLength());
        assertEquals("Invalid overridden timeout value", 60, p.getTimeoutSeconds());

    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateEconding() {
        StringsConfig config = new StringsConfig();
        config.setMinLength(0);
    }
}
