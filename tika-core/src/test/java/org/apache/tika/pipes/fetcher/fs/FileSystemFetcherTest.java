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
package org.apache.tika.pipes.fetcher.fs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.InitializableProblemHandler;


public class FileSystemFetcherTest {

    @Test
    public void testDescendant() throws Exception {

        Path root = Paths.get("/ab/cd/");
        Path descendant = root.resolve("ef/gh/ij.pdf");
        assertTrue(FileSystemFetcher.isDescendant(root, descendant));

        descendant = Paths.get("/cd/ef.pdf");
        assertFalse(FileSystemFetcher.isDescendant(root, descendant));

        descendant = root.resolve("../../ij.pdf");
        assertFalse(FileSystemFetcher.isDescendant(root, descendant));
    }

    @Test
    public void testNullByte() throws Exception {
        FileSystemFetcher f = new FileSystemFetcher();
        assertThrows(InvalidPathException.class, () -> {
            f.setBasePath("bad\u0000path");
            f.setName("fs");
            f.checkInitialization(InitializableProblemHandler.IGNORE);
        });
    }
}
