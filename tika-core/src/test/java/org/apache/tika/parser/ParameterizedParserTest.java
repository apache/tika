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
package org.apache.tika.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;

public class ParameterizedParserTest {

    private static final Map<String, String> expcted = new HashMap<String, String>() {
        {
            put("testparam", "testparamval");
            put("xshort", "1000");
            put("xint", "999999999");
            put("xlong", "9999999999999");
            put("xbigint", "99999999999999999999999999999999999999999999999");
            put("xfloat", "10.2");
            put("xbool", "true");
            put("xdouble", "4.6");
            put("xurl", "http://apache.org");
            put("xfile", "somefile");
            put("xuri", "tika://customuri?param=value");

            put("inner", "inner");
            put("missing", "default");
        }
    };


    @Test
    public void testConfigurableParserTypes() throws Exception {
        Metadata md = getMetadata("TIKA-1986-parameterized.xml");
        for (Map.Entry<String, String> entry : expcted.entrySet()) {
            assertEquals(entry.getValue(),
                    md.get(entry.getKey()), "mismatch for " + entry.getKey());
        }
    }

    @Test
    public void testConfigurableParserTypesDecorated() throws Exception {
        Metadata md = getMetadata("TIKA-1986-parameterized-decorated.xml");
        for (Map.Entry<String, String> entry : expcted.entrySet()) {
            assertEquals(entry.getValue(),
                    md.get(entry.getKey()), "mismatch for " + entry.getKey());
        }
    }


    @Test
    public void testSomeParams() throws Exception {
        //test that a parameterized parser can read a config file
        //with only some changes to the initial values
        Metadata md = getMetadata("TIKA-1986-some-parameters.xml");
        assertEquals("-6.0", md.get("xdouble"));
        assertEquals("testparamval", md.get("testparam"));
        assertEquals("false", md.get("xbool"));
    }

    @Test
    public void testBadValue() throws Exception {
        assertThrows(TikaConfigException.class, () -> {
            getMetadata("TIKA-1986-bad-values.xml");
        });
    }

    @Test
    public void testBadType() throws Exception {
        assertThrows(TikaConfigException.class, () -> {
            getMetadata("TIKA-1986-bad-types.xml");
        });
    }

    //TODO later -- add a test for a parser that isn't configurable
    //but that has params in the config file

    private Metadata getMetadata(String name) throws TikaException, IOException, SAXException {
        URL url = this.getClass().getResource("/org/apache/tika/config/" + name);
        assertNotNull(url, "couldn't find: " + name);
        TikaConfig tikaConfig = new TikaConfig(url);
        Tika tika = new Tika(tikaConfig);
        Metadata metadata = new Metadata();
        tika.parse(url.openStream(), metadata);
        return metadata;
    }
}
