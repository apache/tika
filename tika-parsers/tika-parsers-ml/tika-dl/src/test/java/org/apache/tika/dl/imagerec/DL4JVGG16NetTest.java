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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;

import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.Test;

import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;

public class DL4JVGG16NetTest {

    @Test
    public void recognise() throws Exception {
        assumeFalse(SystemUtils.OS_ARCH.equals("aarch64"), "doesn't yet work on aarch64");
        TikaConfig config = null;
        try (InputStream is = getClass().getResourceAsStream("dl4j-vgg16-config.xml")) {
            config = new TikaConfig(is);
        } catch (Exception e) {
            if (e.getMessage() != null && (e.getMessage().contains("Connection refused") ||
                    e.getMessage().contains("connect timed out") || e.getMessage().contains("403"))) {
                assumeTrue(false, "skipping test because of connection issue");
            }
            throw e;
        }

        assumeTrue(config != null, "something went wrong loading tika config");
        Tika tika = new Tika(config);
        Metadata md = new Metadata();
        try (InputStream is = getClass().getResourceAsStream("lion.jpg")) {
            tika.parse(is, md);
        }
        String[] objects = md.getValues("OBJECT");
        boolean found = false;
        for (String object : objects) {
            if (object.contains("lion")) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }
}
