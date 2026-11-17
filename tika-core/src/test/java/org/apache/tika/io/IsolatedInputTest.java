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
package org.apache.tika.io;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class IsolatedInputTest {

    @Test
    public void testFixedNameInItsOwnDirectory(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("document.dat");
        Files.write(source, "content".getBytes(UTF_8));
        Files.write(tmp.resolve("other.dat"), "other".getBytes(UTF_8));
        Path dir;
        try (IsolatedInput input = IsolatedInput.of(source)) {
            Path path = input.getPath();
            dir = path.getParent();
            assertTrue(path.isAbsolute());
            assertEquals("input", path.getFileName().toString());
            assertNotEquals(tmp, dir);
            assertEquals(List.of("input"), names(dir));
            assertArrayEquals("content".getBytes(UTF_8), Files.readAllBytes(path));
            // what a reader leaves behind goes with the directory
            Files.write(dir.resolve("input.idx"), new byte[]{1});
        }
        assertFalse(Files.exists(dir));
        assertArrayEquals("content".getBytes(UTF_8), Files.readAllBytes(source));
        assertEquals(List.of("document.dat", "other.dat"), names(tmp));
    }

    @Test
    public void testExtensionOfTheMediaType(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("document.dat");
        Files.write(source, "content".getBytes(UTF_8));
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "document.dat");
        try (IsolatedInput input = IsolatedInput.of(source, metadata)) {
            assertEquals("input", input.getPath().getFileName().toString());
        }
        metadata.set(HttpHeaders.CONTENT_TYPE, "application/pdf; charset=x");
        try (IsolatedInput input = IsolatedInput.of(source, metadata)) {
            assertEquals("input.pdf", input.getPath().getFileName().toString());
        }
        // the document's own spelling is kept when it is one of the type's extensions
        metadata.set(HttpHeaders.CONTENT_TYPE, "image/jpeg");
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "photo.JPEG");
        try (IsolatedInput input = IsolatedInput.of(source, metadata)) {
            assertEquals("input.JPEG", input.getPath().getFileName().toString());
        }
        // otherwise the type's usual one
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "photo.txt");
        try (IsolatedInput input = IsolatedInput.of(source, metadata)) {
            assertEquals("input.jpg", input.getPath().getFileName().toString());
        }
        metadata.set(HttpHeaders.CONTENT_TYPE, "application/x-not-registered");
        try (IsolatedInput input = IsolatedInput.of(source, metadata)) {
            assertEquals("input", input.getPath().getFileName().toString());
        }
    }

    @Test
    public void testEveryPlacementReadsTheSameAndLeavesTheSource(@TempDir Path tmp)
            throws Exception {
        Path source = tmp.resolve("document.dat");
        Files.write(source, "content".getBytes(UTF_8));
        for (IsolatedInput.Placement placement : IsolatedInput.Placement.values()) {
            Path dir;
            try (IsolatedInput input = IsolatedInput.of(source, ".dat", placement)) {
                dir = input.getPath().getParent();
                assertEquals(List.of("input.dat"), names(dir), placement.toString());
                assertArrayEquals("content".getBytes(UTF_8), Files.readAllBytes(input.getPath()));
                assertEquals(placement == IsolatedInput.Placement.SYMBOLIC_LINK,
                        Files.isSymbolicLink(input.getPath()), placement.toString());
            } catch (IOException e) {
                // not available on this file system
                continue;
            }
            assertFalse(Files.exists(dir), placement.toString());
            assertArrayEquals("content".getBytes(UTF_8), Files.readAllBytes(source));
        }
    }

    @Test
    public void testSourceThatIsARelativeLink(@TempDir Path tmp) throws Exception {
        Files.write(tmp.resolve("document.dat"), "content".getBytes(UTF_8));
        Path link = tmp.resolve("link.dat");
        try {
            Files.createSymbolicLink(link, Paths.get("document.dat"));
        } catch (IOException | UnsupportedOperationException e) {
            assumeTrue(false, "no symbolic links here: " + e);
        }
        try (IsolatedInput input = IsolatedInput.of(link)) {
            assertArrayEquals("content".getBytes(UTF_8), Files.readAllBytes(input.getPath()));
        }
    }

    private static List<String> names(Path dir) throws Exception {
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
        }
    }
}
