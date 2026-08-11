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
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.tika.digest.DigestDef;
import org.apache.tika.metadata.KeyPrefix;
import org.apache.tika.metadata.Property;

/**
 * Generates the machine-readable metadata key registry ({@code metadata-keys.json}) from the live
 * {@link Property} declarations. Dependency-free: it scans the runtime classpath for classes that
 * declare a {@code Property} field, force-loads them (their static init registers into the global
 * {@code Property} table), then serializes that table as stable, sorted JSON.
 *
 * <p>The generated file is committed; {@code MetadataSchemaTest} regenerates and asserts no diff,
 * so the registry can never drift from the declarations.
 *
 * <p>TIKA-4797: {@link #fieldTable()} additionally emits a {@code {class, field, key}} table (the
 * field provenance that {@code Property.PROPERTIES} discards) so the 3.x&#8594;4.x key migration can
 * be built by a field-identity join. Synthesized keys with no declaring field (digests) are absent
 * from that table by design; they migrate by prefix rule.
 */
public final class SchemaGenerator {

    // Field/parameter descriptors in a .class constant pool: a class referencing one is force-loaded.
    private static final byte[] PROP_DESC =
            "Lorg/apache/tika/metadata/Property;".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] KEY_PREFIX_DESC =
            "Lorg/apache/tika/metadata/KeyPrefix;".getBytes(StandardCharsets.ISO_8859_1);

    private SchemaGenerator() {
    }

    /** @return the registry as stable JSON (sorted by key). */
    public static String generate() throws Exception {
        ClassLoader cl = SchemaGenerator.class.getClassLoader();
        scanClasspath(cl);
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

    /**
     * TIKA-4797. @return the field-attributed table {@code [{class, field, key}]} as stable JSON,
     * sorted by {@code class#field} — one record per static {@link Property} constant. Aliases (two
     * fields, same key) are distinct records; that is what lets the migration join detect renames.
     */
    public static String fieldTable() throws Exception {
        ClassLoader cl = SchemaGenerator.class.getClassLoader();
        TreeMap<String, String[]> rows = new TreeMap<>();
        for (String cn : scanClasspath(cl)) {
            Class<?> c;
            try {
                c = Class.forName(cn, true, cl);
            } catch (Throwable ignore) {
                continue;
            }
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) || f.getType() != Property.class) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    Property p = (Property) f.get(null);
                    if (p != null) {
                        rows.put(c.getName() + "#" + f.getName(),
                                new String[]{c.getName(), f.getName(), p.getName()});
                    }
                } catch (Throwable ignore) {
                    // unreadable field; skip
                }
            }
        }
        return fieldTableJson(rows);
    }

    /** Scans the classpath, force-loads every Property/KeyPrefix-bearing class (static init
     * registers the constants), and returns the loaded class names. */
    private static List<String> scanClasspath(ClassLoader cl) throws IOException {
        List<String> loaded = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            File f = new File(entry);
            if (f.isDirectory()) {
                scanDir(f, f.toPath(), cl, loaded);
            } else if (f.getName().endsWith(".jar")) {
                scanJar(f, cl, loaded);
            }
        }
        return loaded;
    }

    private static void scanDir(File root, Path dir, ClassLoader cl, List<String> loaded)
            throws IOException {
        try (var stream = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (!p.toString().endsWith(".class")) {
                    continue;
                }
                String rel = root.toPath().relativize(p).toString().replace(File.separatorChar, '/');
                maybeLoad(rel, Files.readAllBytes(p), cl, loaded);
            }
        }
    }

    private static void scanJar(File jar, ClassLoader cl, List<String> loaded) throws IOException {
        try (JarFile jf = new JarFile(jar)) {
            for (Enumeration<JarEntry> e = jf.entries(); e.hasMoreElements(); ) {
                JarEntry je = e.nextElement();
                if (!je.getName().endsWith(".class")) {
                    continue;
                }
                maybeLoad(je.getName(), jf.getInputStream(je).readAllBytes(), cl, loaded);
            }
        }
    }

    private static void maybeLoad(String classPath, byte[] bytes, ClassLoader cl, List<String> loaded) {
        if (!classPath.startsWith("org/apache/tika/")
                || (!contains(bytes, PROP_DESC) && !contains(bytes, KEY_PREFIX_DESC))) {
            return;
        }
        String cn = classPath.substring(0, classPath.length() - 6).replace('/', '.');
        try {
            Class.forName(cn, true, cl);   // static init registers any Property constants
            loaded.add(cn);
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

    private static String fieldTableJson(TreeMap<String, String[]> rows) {
        StringBuilder sb = new StringBuilder("[\n");
        int i = 0;
        int n = rows.size();
        for (Map.Entry<String, String[]> e : rows.entrySet()) {
            String[] r = e.getValue();
            sb.append("  {\"class\":").append(quote(r[0]))
              .append(",\"field\":").append(quote(r[1]))
              .append(",\"key\":").append(quote(r[2]))
              .append("}").append(++i < n ? "," : "").append('\n');
        }
        return sb.append("]\n").toString();
    }

    private static String quote(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    /** Declared passthrough prefixes as stable JSON. Call after {@link #generate()} has loaded classes. */
    public static String passthroughJson() {
        TreeMap<String, String[]> m = new TreeMap<>();
        for (KeyPrefix p : KeyPrefix.registered()) {
            m.put(p.prefix(), new String[]{p.provenance().name(), p.description()});
        }
        StringBuilder sb = new StringBuilder("[\n");
        int i = 0;
        int n = m.size();
        for (Map.Entry<String, String[]> e : m.entrySet()) {
            sb.append("  {\"prefix\":").append(quote(e.getKey()))
              .append(",\"provenance\":\"").append(e.getValue()[0])
              .append("\",\"description\":").append(quote(e.getValue()[1]))
              .append("}").append(++i < n ? "," : "").append('\n');
        }
        return sb.append("]\n").toString();
    }

    public static void main(String[] args) throws Exception {
        Files.writeString(Path.of(args[0]), generate());   // triggers the classpath scan
        if (args.length > 1) {
            Files.writeString(Path.of(args[1]), passthroughJson());
        }
        if (args.length > 2) {
            Files.writeString(Path.of(args[2]), fieldTable());   // TIKA-4797 field-attributed table
        }
    }
}
