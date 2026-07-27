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
package org.apache.tika.metadata.schema;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.tika.digest.DigestDef;
import org.apache.tika.metadata.Property;

/**
 * Generates the machine-readable metadata key registry ({@code metadata-keys.json}) from the live
 * {@link Property} declarations. Dependency-free: it scans the runtime classpath for classes that
 * declare a {@code Property} field, force-loads them (their static init registers into the global
 * {@code Property} table), then serializes that table as stable, sorted JSON.
 *
 * <p>The generated file is committed; {@code MetadataSchemaTest} regenerates and asserts no diff,
 * so the registry can never drift from the declarations.
 */
public final class SchemaGenerator {

    // Field/parameter descriptor for org.apache.tika.metadata.Property in a .class constant pool.
    private static final byte[] PROP_DESC =
            "Lorg/apache/tika/metadata/Property;".getBytes(StandardCharsets.ISO_8859_1);

    private SchemaGenerator() {
    }

    /** @return the registry as stable JSON (sorted by key). */
    public static String generate() throws Exception {
        ClassLoader cl = SchemaGenerator.class.getClassLoader();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            File f = new File(entry);
            if (f.isDirectory()) {
                scanDir(f, f.toPath(), cl);
            } else if (f.getName().endsWith(".jar")) {
                scanJar(f, cl);
            }
        }
        Field fld = Property.class.getDeclaredField("PROPERTIES");
        fld.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Property> reg = (Map<String, Property>) fld.get(null);

        TreeMap<String, String[]> entries = new TreeMap<>();   // key -> [valueType, cardinality]
        for (Map.Entry<String, Property> e : reg.entrySet()) {
            entries.put(e.getKey(), new String[]{
                    e.getValue().getValueType().toString(), e.getValue().getPropertyType().toString()});
        }
        // Digest keys are a CLOSED cross-product of the supported algorithms and encodings, not an
        // open template. Synthesize them via the real DigestDef.metadataKey() so they can't drift.
        for (DigestDef.Algorithm a : DigestDef.Algorithm.values()) {
            for (DigestDef.Encoding enc : DigestDef.Encoding.values()) {
                entries.put(new DigestDef(a, enc).metadataKey(), new String[]{"TEXT", "SIMPLE"});
            }
        }
        return toJson(entries);
    }

    private static void scanDir(File root, Path dir, ClassLoader cl) throws IOException {
        try (var stream = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (!p.toString().endsWith(".class")) {
                    continue;
                }
                String rel = root.toPath().relativize(p).toString().replace(File.separatorChar, '/');
                maybeLoad(rel, Files.readAllBytes(p), cl);
            }
        }
    }

    private static void scanJar(File jar, ClassLoader cl) throws IOException {
        try (JarFile jf = new JarFile(jar)) {
            for (Enumeration<JarEntry> e = jf.entries(); e.hasMoreElements(); ) {
                JarEntry je = e.nextElement();
                if (!je.getName().endsWith(".class")) {
                    continue;
                }
                maybeLoad(je.getName(), jf.getInputStream(je).readAllBytes(), cl);
            }
        }
    }

    private static void maybeLoad(String classPath, byte[] bytes, ClassLoader cl) {
        if (!classPath.startsWith("org/apache/tika/") || !contains(bytes, PROP_DESC)) {
            return;
        }
        String cn = classPath.substring(0, classPath.length() - 6).replace('/', '.');
        try {
            Class.forName(cn, true, cl);   // static init registers any Property constants
        } catch (Throwable ignore) {
            // classes whose static init needs an absent dependency are skipped; the corpus test backs this
        }
    }

    private static boolean contains(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i <= hay.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static String toJson(TreeMap<String, String[]> entries) {
        StringBuilder sb = new StringBuilder("[\n");
        int i = 0;
        int n = entries.size();
        for (Map.Entry<String, String[]> e : entries.entrySet()) {
            String k = e.getKey();
            String ns = k.contains(":") ? k.substring(0, k.indexOf(':')) : "";
            sb.append("  {\"key\":").append(quote(k))
              .append(",\"namespace\":").append(quote(ns))
              .append(",\"valueType\":\"").append(e.getValue()[0])
              .append("\",\"cardinality\":\"").append(e.getValue()[1])
              .append("\"}").append(++i < n ? "," : "").append('\n');
        }
        return sb.append("]\n").toString();
    }

    private static String quote(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.writeString(out, generate());
        System.out.println("wrote " + out);
    }
}
