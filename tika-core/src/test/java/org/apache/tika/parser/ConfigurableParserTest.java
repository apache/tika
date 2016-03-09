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

public class ConfigurableParserTest {

    public static final String TIKA_CFG_FILE = "org/apache/tika/config/TIKA-1508-configurable.xml";
    public static final String TEST_PARAM = "testparam";
    public static final String TEST_PARAM_VAL = "testparamval";

    @Test
    public void testConfigurableParser() throws Exception {
        URL configFileUrl = getClass().getClassLoader().getResource(TIKA_CFG_FILE);
        assert configFileUrl != null;
        TikaConfig config = new TikaConfig(configFileUrl);
        Tika tika = new Tika(config);
        Metadata md = new Metadata();
        tika.parse(configFileUrl.openStream(), md);
        Assert.assertEquals(TEST_PARAM_VAL, md.get(TEST_PARAM));
        //assert that param from configuration file is read, given to parser and it copied to metadata
    }
}
