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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a plugin zip: {@code plugin.properties}, {@code LICENSE} and {@code NOTICE} from the
 * plugin jar, and {@code lib/} holding the plugin jar and the jars its {@link PluginLib} names.
 * The same inputs give the same bytes: entries are sorted, stored, and stamped with the time
 * of the jar's {@code plugin.properties}.
 */
public final class PluginZip {

    static final String DESCRIPTOR = "plugin.properties";

    private static final Map<String, String> COPIED = Map.of(
            DESCRIPTOR, DESCRIPTOR,
            "META-INF/LICENSE", "LICENSE",
            "META-INF/NOTICE", "NOTICE");

    private PluginZip() {
    }

    public static List<PluginLib.Entry> readLib(Path pluginJar) throws IOException {
        try (JarFile jar = new JarFile(pluginJar.toFile())) {
            ZipEntry entry = jar.getEntry(PluginLib.PATH);
            if (entry == null) {
                throw new IOException(pluginJar.getFileName() + " has no " + PluginLib.PATH
                        + "; it was not built as a Tika plugin with a lib list (Tika 4.3.0+)");
            }
            try (InputStream is = jar.getInputStream(entry)) {
                return PluginLib.parse(is);
            }
        }
    }

    /**
     * @param pluginJarName name for the plugin jar under {@code lib/}
     * @param libs          {@code lib/} file name to file, the plugin jar excluded
     */
    public static void write(Path pluginJar, String pluginJarName, Map<String, Path> libs,
                             Path zip) throws IOException {
        Map<String, Path> files = new TreeMap<>();
        for (Map.Entry<String, Path> e : libs.entrySet()) {
            files.put("lib/" + e.getKey(), e.getValue());
        }
        if (files.put("lib/" + pluginJarName, pluginJar) != null) {
            throw new IOException("lib list names the plugin's own jar: " + pluginJarName);
        }
        Map<String, byte[]> copied = new TreeMap<>();
        LocalDateTime time;
        try (JarFile jar = new JarFile(pluginJar.toFile())) {
            ZipEntry descriptor = jar.getEntry(DESCRIPTOR);
            if (descriptor == null) {
                throw new IOException(pluginJar.getFileName() + " has no " + DESCRIPTOR);
            }
            time = descriptor.getTimeLocal();
            for (Map.Entry<String, String> e : COPIED.entrySet()) {
                ZipEntry entry = jar.getEntry(e.getKey());
                if (entry != null) {
                    try (InputStream is = jar.getInputStream(entry)) {
                        copied.put(e.getValue(), is.readAllBytes());
                    }
                }
            }
        }
        Files.createDirectories(zip.toAbsolutePath().getParent());
        // not createTempFile: that makes the zip owner-only
        Path tmp = zip.resolveSibling(zip.getFileName() + ".tmp");
        try {
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmp))) {
                zos.setMethod(ZipOutputStream.STORED);
                for (Map.Entry<String, byte[]> e : copied.entrySet()) {
                    CRC32 crc = new CRC32();
                    crc.update(e.getValue());
                    zos.putNextEntry(stored(e.getKey(), e.getValue().length, crc, time));
                    zos.write(e.getValue());
                    zos.closeEntry();
                }
                for (Map.Entry<String, Path> e : files.entrySet()) {
                    CRC32 crc = new CRC32();
                    try (InputStream is = Files.newInputStream(e.getValue())) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = is.read(buf)) != -1) {
                            crc.update(buf, 0, n);
                        }
                    }
                    zos.putNextEntry(stored(e.getKey(), Files.size(e.getValue()), crc, time));
                    Files.copy(e.getValue(), zos);
                    zos.closeEntry();
                }
            }
            Files.move(tmp, zip, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (InputStream is = new DigestInputStream(Files.newInputStream(file), digest)) {
            is.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static ZipEntry stored(String name, long size, CRC32 crc, LocalDateTime time) {
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(size);
        entry.setCompressedSize(size);
        entry.setCrc(crc.getValue());
        entry.setTimeLocal(time);
        return entry;
    }
}
