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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Completeness backstop for the standard-scoped registry. {@link MetadataSchemaTest} proves the
 * registry matches the declarations it <em>scans</em>; this proves nothing <em>escapes</em> the
 * scan. It greps the source tree for every module declaring a {@code Property} or
 * {@code KeyPrefix} and fails if one is neither on the registry classpath nor an explicit
 * {@link #OUT_OF_SCOPE} family — so a new key-bearing parser cannot slip in silently.
 *
 * <p>Reads source (always present) for the declaring set and the runtime classpath for the scanned
 * set, so it is correct in a targeted {@code -pl tika-metadata-schema} run — no full build needed.
 */
public class MetadataCoverageTest {

    /** Parser families intentionally excluded from the standard-scoped registry (heavy/optional
     * deps). Adding a module here is a conscious scope decision; see README. */
    private static final Set<String> OUT_OF_SCOPE = Set.of(
            "tika-parser-scientific-module",   // envi. grib: netcdf:
            "tika-parser-sqlite3-module",      // sqlite3:
            "tika-parser-nlp-module",          // ctakes: grobid:header_ NER_
            "tika-vlm");                       // vlm:

    // Source markers for an actual key declaration (a factory call), not a mere type reference.
    private static final String[] DECL_MARKERS = {
            "Property.internal", "Property.external", "Property.composite",
            "KeyPrefix.file(", "KeyPrefix.tool("};

    private static final String SRC_MAIN = sep("src", "main") + File.separator + "java";
    private static final String SRC_TEST = sep("src", "test");

    @Test
    public void everyKeyDeclaringModuleIsScannedOrExcluded() throws Exception {
        Path repoRoot = repoRoot();
        assumeTrue(repoRoot != null, "repo root (tika-core + tika-parsers) not found");

        Set<String> declaring = new TreeSet<>();
        for (String sub : new String[]{"tika-core", "tika-parsers"}) {
            Path base = repoRoot.resolve(sub);
            if (!Files.isDirectory(base)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(base)) {
                for (Path p : (Iterable<Path>) walk::iterator) {
                    String module = keyDeclaringModule(p);
                    if (module != null) {
                        declaring.add(module);
                    }
                }
            }
        }
        assumeTrue(!declaring.isEmpty(), "no key-declaring source found (unexpected checkout layout)");

        Set<String> scanned = classpathModules();
        Set<String> escaped = new TreeSet<>(declaring);
        escaped.removeAll(scanned);
        escaped.removeAll(OUT_OF_SCOPE);

        assertTrue(escaped.isEmpty(), "Module(s) declare metadata keys but are neither scanned by "
                + "the registry nor listed OUT_OF_SCOPE: " + escaped + ". Either add the module to "
                + "the registry's scan classpath, or add it to OUT_OF_SCOPE (and document it).");
    }

    /** Module name if {@code p} is a main-source file declaring a key, else null. */
    private static String keyDeclaringModule(Path p) throws Exception {
        String s = p.toString();
        int i = s.indexOf(SRC_MAIN);
        if (!s.endsWith(".java") || i < 0 || s.contains(SRC_TEST)) {
            return null;
        }
        String src = Files.readString(p, StandardCharsets.UTF_8);
        boolean declares = false;
        for (String marker : DECL_MARKERS) {
            if (src.contains(marker)) {
                declares = true;
                break;
            }
        }
        if (!declares) {
            return null;
        }
        // s.substring(0, i) is the module dir itself (the part before ".../src/main/java/...")
        Path moduleDir = Paths.get(s.substring(0, i));
        return moduleDir.getFileName() == null ? null : moduleDir.getFileName().toString();
    }

    /** Artifacts the registry scan sees: reactor {@code target/classes} dirs plus dependency jars
     * (Tika dir name == artifactId, so both keys line up with {@link #keyDeclaringModule}). */
    private static Set<String> classpathModules() {
        Set<String> mods = new TreeSet<>();
        String classes = sep("target", "classes");
        for (String e : System.getProperty("java.class.path").split(File.pathSeparator)) {
            Path p = Paths.get(e);
            Path mod = null;
            if (e.endsWith(classes)) {
                mod = p.getParent().getParent();                 // <mod>/target/classes -> <mod>
            } else if (e.endsWith(".jar") && p.getParent() != null) {
                mod = p.getParent().getParent();                 // <artifactId>/<version>/x.jar -> <artifactId>
            }
            if (mod != null && mod.getFileName() != null) {
                mods.add(mod.getFileName().toString());
            }
        }
        return mods;
    }

    private static Path repoRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve("tika-core")) && Files.isDirectory(p.resolve("tika-parsers"))) {
                return p;
            }
            p = p.getParent();
        }
        return null;
    }

    private static String sep(String a, String b) {
        return File.separator + a + File.separator + b;
    }
}
