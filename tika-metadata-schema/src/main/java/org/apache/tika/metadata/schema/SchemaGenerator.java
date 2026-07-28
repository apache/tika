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

import org.apache.tika.metadata.Property;

/**
 * TIKA-4797. Generates the field-attributed 3.x metadata-key table ({@code metadata-keys-3x.json}):
 * one record {@code {class, field, key}} per static {@link Property} constant on the tika-core +
 * standard-parser classpath. Field provenance is what lets the 3.x-&gt;4.x migration mapping be built
 * by a field-identity join (same field, changed key = rename) instead of string heuristics.
 *
 * <p>Dependency-free: scans the runtime classpath for classes that declare a {@code Property} field,
 * force-loads them (static init registers the constants), then reflects each declared field. The
 * committed file is gated by {@code MetadataKeys3xTest} so it can never drift from the declarations.
 */
public final class SchemaGenerator {

    private static final byte[] PROP_DESC =
            "Lorg/apache/tika/metadata/Property;".getBytes(StandardCharsets.ISO_8859_1);

    private SchemaGenerator() {
    }

    /** @return the table as stable JSON, sorted by {@code class#field}. */
    public static String generate() throws Exception {
        ClassLoader cl = SchemaGenerator.class.getClassLoader();
        List<String> classNames = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            File f = new File(entry);
            if (f.isDirectory()) {
                scanDir(f, f.toPath(), classNames);
            } else if (f.getName().endsWith(".jar")) {
                scanJar(f, classNames);
            }
        }
        TreeMap<String, String[]> rows = new TreeMap<>();   // class#field -> [class, field, key]
        for (String cn : classNames) {
            Class<?> c;
            try {
                c = Class.forName(cn, true, cl);
            } catch (Throwable ignore) {
                continue;   // classes whose static init needs an absent dependency are skipped
            }
            for (Field fld : c.getDeclaredFields()) {
                if (!Modifier.isStatic(fld.getModifiers()) || fld.getType() != Property.class) {
                    continue;
                }
                try {
                    fld.setAccessible(true);
                    Property p = (Property) fld.get(null);
                    if (p != null) {
                        rows.put(c.getName() + "#" + fld.getName(),
                                new String[]{c.getName(), fld.getName(), p.getName()});
                    }
                } catch (Throwable ignore) {
                    // unreadable field; skip
                }
            }
        }
        return toJson(rows);
    }

    private static void scanDir(File root, Path dir, List<String> out) throws IOException {
        try (var stream = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (p.toString().endsWith(".class")) {
                    String rel = root.toPath().relativize(p).toString().replace(File.separatorChar, '/');
                    maybe(rel, Files.readAllBytes(p), out);
                }
            }
        }
    }

    private static void scanJar(File jar, List<String> out) throws IOException {
        try (JarFile jf = new JarFile(jar)) {
            for (Enumeration<JarEntry> e = jf.entries(); e.hasMoreElements(); ) {
                JarEntry je = e.nextElement();
                if (je.getName().endsWith(".class")) {
                    maybe(je.getName(), jf.getInputStream(je).readAllBytes(), out);
                }
            }
        }
    }

    private static void maybe(String classPath, byte[] bytes, List<String> out) {
        if (classPath.startsWith("org/apache/tika/") && contains(bytes)) {
            out.add(classPath.substring(0, classPath.length() - 6).replace('/', '.'));
        }
    }

    private static boolean contains(byte[] hay) {
        outer:
        for (int i = 0; i <= hay.length - PROP_DESC.length; i++) {
            for (int j = 0; j < PROP_DESC.length; j++) {
                if (hay[i + j] != PROP_DESC[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static String toJson(TreeMap<String, String[]> rows) {
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

    public static void main(String[] args) throws Exception {
        Files.writeString(Path.of(args[0]), generate());
    }
}
