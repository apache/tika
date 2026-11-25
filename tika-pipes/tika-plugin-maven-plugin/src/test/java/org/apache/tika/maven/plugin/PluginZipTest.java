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
package org.apache.tika.maven.plugin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class PluginZipTest {

    private static final String SHA = "a".repeat(64);
    private static final LocalDateTime BUILD_TIME = LocalDateTime.of(2026, 10, 25, 19, 27, 36);

    @TempDir
    Path tmp;

    @Test
    public void libListRoundTrips() throws IOException {
        List<PluginLib.Entry> entries = List.of(
                new PluginLib.Entry("io.netty", "netty-tcnative", "jar", "linux-x86_64",
                        "2.0.85.Final", SHA),
                new PluginLib.Entry("com.google.guava", "guava", "jar", "", "33.8.0-jre", SHA));
        String text = PluginLib.format(entries);
        List<PluginLib.Entry> parsed = PluginLib.parse(
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        assertEquals(List.of(entries.get(1), entries.get(0)), parsed);
        assertEquals("netty-tcnative-2.0.85.Final-linux-x86_64.jar", parsed.get(1).fileName());
        assertEquals(text, PluginLib.format(parsed));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "g:../../evil:jar:1.0 SHA",
            "g:a:jar:1.0/../x SHA",
            "g:a\\b:jar:1.0 SHA",
            "g:a:jar:1.0",
            "g:a:1.0 SHA",
            "g:a:jar:c:1.0:extra SHA",
            "g:a:jar:1.0 abc123"})
    public void badLibLinesAreRejected(String line) {
        byte[] bytes = line.replace("SHA", SHA).getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> PluginLib.parse(new ByteArrayInputStream(bytes)));
    }

    @Test
    public void zipHoldsDescriptorNoticesAndLib() throws IOException {
        Path jar = pluginJar("p.jar", true);
        Path dep = Files.write(tmp.resolve("dep"), new byte[]{1, 2, 3});
        Path zip = tmp.resolve("out/p-1.0.zip");
        PluginZip.write(jar, "p-1.0.jar", Map.of("dep-2.0.jar", dep), zip);

        List<String> names = new ArrayList<>();
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            zf.stream().forEach(e -> {
                names.add(e.getName());
                assertEquals(ZipEntry.STORED, e.getMethod());
                assertEquals(BUILD_TIME, e.getTimeLocal());
            });
            assertArrayEquals(new byte[]{1, 2, 3},
                    zf.getInputStream(zf.getEntry("lib/dep-2.0.jar")).readAllBytes());
            assertArrayEquals(Files.readAllBytes(jar),
                    zf.getInputStream(zf.getEntry("lib/p-1.0.jar")).readAllBytes());
            assertEquals("plugin.id=p\n", new String(
                    zf.getInputStream(zf.getEntry("plugin.properties")).readAllBytes(),
                    StandardCharsets.UTF_8));
        }
        assertEquals(List.of("LICENSE", "NOTICE", "plugin.properties", "lib/dep-2.0.jar",
                "lib/p-1.0.jar"), names);
    }

    @Test
    public void sameInputsGiveSameBytes() throws IOException {
        Path jar = pluginJar("p.jar", true);
        Path a = Files.write(tmp.resolve("a"), new byte[]{1});
        Path b = Files.write(tmp.resolve("b"), new byte[]{2});
        Map<String, Path> forward = new LinkedHashMap<>();
        forward.put("a.jar", a);
        forward.put("b.jar", b);
        Map<String, Path> reverse = new LinkedHashMap<>();
        reverse.put("b.jar", b);
        reverse.put("a.jar", a);
        Path zip1 = tmp.resolve("1.zip");
        Path zip2 = tmp.resolve("2.zip");
        PluginZip.write(jar, "p-1.0.jar", forward, zip1);
        PluginZip.write(jar, "p-1.0.jar", reverse, zip2);
        assertArrayEquals(Files.readAllBytes(zip1), Files.readAllBytes(zip2));
    }

    @Test
    public void jarWithoutDescriptorOrLibListIsRejected() throws IOException {
        Path jar = pluginJar("bare.jar", false);
        IOException e = assertThrows(IOException.class, () -> PluginZip.readLib(jar));
        assertTrue(e.getMessage().contains(PluginLib.PATH), e.getMessage());
        assertThrows(IOException.class,
                () -> PluginZip.write(jar, "p-1.0.jar", Map.of(), tmp.resolve("x.zip")));
        assertTrue(Files.notExists(tmp.resolve("x.zip")));
    }

    @Test
    public void libListNamingThePluginJarIsRejected() throws IOException {
        Path jar = pluginJar("p.jar", true);
        assertThrows(IOException.class, () -> PluginZip.write(jar, "p-1.0.jar",
                Map.of("p-1.0.jar", jar), tmp.resolve("x.zip")));
    }

    private Path pluginJar(String name, boolean plugin) throws IOException {
        Path jar = tmp.resolve(name);
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("META-INF/LICENSE", "license");
        entries.put("META-INF/NOTICE", "notice");
        if (plugin) {
            entries.put("plugin.properties", "plugin.id=p\n");
            entries.put(PluginLib.PATH, "g:dep:jar:2.0 " + SHA + "\n");
        }
        try (OutputStream os = Files.newOutputStream(jar);
                ZipOutputStream zos = new ZipOutputStream(os)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                entry.setTimeLocal(BUILD_TIME);
                zos.putNextEntry(entry);
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return jar;
    }
}
