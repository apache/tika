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
package org.apache.tika.pipes.core.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PipesServerTempDirTest {

    /** Only a parent-named dir is deleted; the system temp dir a hand-launched server sees is not. */
    @Test
    public void testDeletesOnlyParentCreatedDir(@TempDir Path tmp) throws Exception {
        for (String prefix : new String[]{PipesServer.TEMP_DIR_PREFIX,
                PipesServer.SHARED_TEMP_DIR_PREFIX}) {
            Path own = Files.createDirectories(tmp.resolve(prefix + "1-abc"));
            Files.writeString(own.resolve("spooled.tmp"), "document bytes");
            Files.createDirectories(own.resolve("nested"));
            assertTrue(PipesServer.deleteOwnTempDir(own));
            assertFalse(Files.exists(own));
        }

        Path notOwn = Files.createDirectories(tmp.resolve("something-else"));
        Files.writeString(notOwn.resolve("keep.tmp"), "not ours");
        assertFalse(PipesServer.deleteOwnTempDir(notOwn));
        assertTrue(Files.exists(notOwn.resolve("keep.tmp")));
        assertFalse(PipesServer.deleteOwnTempDir(tmp));
        assertTrue(Files.exists(notOwn));
    }
}
