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
package org.apache.tika.pipes.grpc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.exception.TikaConfigException;

/**
 * tika-grpc must not ship Apache Ignite. Ignite support is loaded reflectively, so asking for it
 * without the jars has to fail with an actionable message rather than a NoClassDefFoundError.
 */
public class TikaGrpcIgniteConfigStoreTest {

    @Test
    public void testIgniteConfigStoreNotOnClasspath(@TempDir Path tmp) throws Exception {
        Path config = tmp.resolve("tika-config-ignite.json");
        Files.writeString(config, """
                {
                  "pipes": {
                    "configStoreType": "ignite",
                    "configStoreParams": "{\\"tableName\\":\\"tika_config_store\\"}"
                  }
                }
                """, StandardCharsets.UTF_8);

        TikaConfigException e = assertThrows(TikaConfigException.class,
                () -> new TikaGrpcServerImpl(config.toAbsolutePath().toString()));
        assertNotNull(e.getMessage());
        assertTrue(e.getMessage().contains("tika-pipes-config-store-ignite"),
                "expected an actionable message naming the missing module, got: " + e.getMessage());
    }
}
