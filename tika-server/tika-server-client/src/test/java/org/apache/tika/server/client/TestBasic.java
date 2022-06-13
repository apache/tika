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
package org.apache.tika.server.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class TestBasic {

    @Test
    public void testConfig() throws Exception {
        Path p = Paths.get(
                TestBasic.class.getResource("/tika-config-simple-fs-emitter.xml").toURI());
        assertTrue(Files.isRegularFile(p));

        TikaServerClientConfig clientConfig = TikaServerClientConfig.build(p);
        assertEquals(6, clientConfig.getNumThreads());
        assertEquals(5, clientConfig.getHttpClientFactory().getMaxConnections());
    }

    @Test
    @Disabled("turn this into an actual test in tika-integration-tests?")
    public void testBasic() throws Exception {
        Path p = Paths.get(
                TestBasic.class.getResource("/tika-config-simple-fs-emitter.xml").toURI());
        assertTrue(Files.isRegularFile(p));
        String[] args = new String[]{p.toAbsolutePath().toString()};
        long start = System.currentTimeMillis();
        TikaClientCLI.main(args);
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("elapsed " + elapsed);
    }
}
