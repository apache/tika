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

import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.junit.Assert;
import org.junit.Test;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class ParametrizedParserTest {

    private static final String TIKA_CFG_FILE = "org/apache/tika/config/TIKA-1986-parametrized.xml";
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
            put("xfile", "/");
            put("xuri", "tika://customuri?param=value");

            put("inner", "inner");
            put("missing", "default");
        }
    };


    @Test
    public void testConfigurableParserTypes() throws Exception {
        URL configFileUrl = getClass().getClassLoader().getResource(TIKA_CFG_FILE);
        assert configFileUrl != null;
        TikaConfig config = new TikaConfig(configFileUrl);
        Tika tika = new Tika(config);
        Metadata md = new Metadata();
        tika.parse(configFileUrl.openStream(), md);

        for (Map.Entry<String, String> entry : expcted.entrySet()) {
            Assert.assertEquals("mismatch for " + entry.getKey(), entry.getValue(), md.get(entry.getKey()));
        }
    }


}
