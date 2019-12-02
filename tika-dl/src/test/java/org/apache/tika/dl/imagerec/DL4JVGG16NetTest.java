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
package org.apache.tika.dl.imagerec;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.junit.Test;

import java.io.InputStream;

public class DL4JVGG16NetTest {

    @Test
    public void recognise() throws Exception {
        TikaConfig config = null;
        InputStream is = getClass().getResourceAsStream("dl4j-vgg16-config.xml");
        try {
            config = new TikaConfig(getClass().getResourceAsStream("dl4j-vgg16-config.xml"));
        } catch (Exception e) {
            if (e.getMessage() != null
                    && (e.getMessage().contains("Connection refused")
                    || e.getMessage().contains("connect timed out"))) {
                assumeTrue("skipping test because of connection issue", false);
            }
            throw e;
        }

        assumeTrue("something went wrong loading tika config", config != null);
        Tika tika = new Tika(config);
        Metadata md = new Metadata();
        tika.parse(getClass().getResourceAsStream("lion.jpg"), md);
        String[] objects = md.getValues("OBJECT");
        boolean found = false;
        for (String object : objects) {
            if (object.contains("lion")) {
                found = true;
            }
        }
        assertTrue(found);
    }

}