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
package org.apache.tika.ml.junkdetect.tools;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records which corpus, config and code produced a junk-detector model, so a
 * model file can always be traced back to its inputs.
 *
 * <p>Chain: the source corpus carries a {@code manifest.tsv};
 * {@link BuildJunkTrainingData} copies its name and digest into
 * {@value #SOURCE_FILE} in the built directory; {@link TrainJunkModel} writes
 * {@code <model>.provenance} with the model digest and everything upstream.
 */
public final class TrainingProvenance {

    static final String MANIFEST = "manifest.tsv";
    static final String SOURCE_FILE = "source.tsv";
    public static final String PROVENANCE_SUFFIX = ".provenance";

    private TrainingProvenance() {
    }

    /** Fails unless {@code dir} is a directory holding a {@value #MANIFEST}. */
    static Path requireManifest(Path dir, String what) throws IOException {
        Path m = dir.resolve(MANIFEST);
        if (!Files.isRegularFile(m)) {
            throw new IOException(what + " " + dir + " has no " + MANIFEST
                    + "; refusing to use a directory without a recorded identity");
        }
        return m;
    }

    /** Writes {@value #SOURCE_FILE} into the built directory, naming the source corpus. */
    static void writeSource(Path sourceCorpus, Path builtDir) throws IOException {
        Path m = requireManifest(sourceCorpus, "source corpus");
        Map<String, String> kv = new LinkedHashMap<>();
        kv.put("source_corpus", sourceCorpus.toAbsolutePath().normalize().getFileName().toString());
        kv.put("source_manifest_sha256", sha256(m));
        kv.put("source_content_sha256", contentSha256(sourceCorpus));
        writeKv(builtDir.resolve(SOURCE_FILE), kv);
    }

    /**
     * SHA-256 over every regular file under {@code dir}, in path order: each file's relative
     * path and SHA-256.  The manifest only lists languages and counts, so this is what pins the
     * corpus text itself.
     */
    static String contentSha256(Path dir) throws IOException {
        List<Path> files;
        try (java.util.stream.Stream<Path> s = Files.walk(dir)) {
            files = s.filter(Files::isRegularFile).sorted().collect(java.util.stream.Collectors.toList());
        }
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        for (Path f : files) {
            String rel = dir.relativize(f).toString().replace('\\', '/');
            md.update((rel + "\t" + sha256(f) + "\n").getBytes(StandardCharsets.UTF_8));
        }
        StringBuilder sb = new StringBuilder(64);
        for (byte b : md.digest()) {
            sb.append(String.format(java.util.Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    /** Reads a key/value file written by {@link #writeKv}. */
    static Map<String, String> readKv(Path p) throws IOException {
        Map<String, String> kv = new LinkedHashMap<>();
        for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
            int t = line.indexOf('\t');
            if (t > 0 && !line.startsWith("#")) {
                kv.put(line.substring(0, t), line.substring(t + 1));
            }
        }
        return kv;
    }

    static void writeKv(Path p, Map<String, String> kv) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> e : kv.entrySet()) {
                w.write(e.getKey());
                w.write('\t');
                w.write(e.getValue().replace('\t', ' ').replace('\n', ' '));
                w.write('\n');
            }
        }
    }

    /**
     * Writes {@code <model>.provenance}: model digest, built-data and source-corpus
     * identity, every static training constant, the command line and git state.
     */
    static void writeModelProvenance(Path model, Path builtDir, String[] args,
                                     String gitCommit, List<Class<?>> configClasses)
            throws IOException {
        Path builtManifest = requireManifest(builtDir, "training data");
        Path source = builtDir.resolve(SOURCE_FILE);
        if (!Files.isRegularFile(source)) {
            throw new IOException("training data " + builtDir + " has no " + SOURCE_FILE
                    + "; rebuild it with BuildJunkTrainingData");
        }
        Map<String, String> kv = new LinkedHashMap<>();
        kv.put("model_sha256", sha256(model));
        kv.put("trained_at", Instant.now().toString());
        kv.putAll(readKv(source));
        kv.put("training_data", shortPath(builtDir));
        kv.put("training_data_manifest_sha256", sha256(builtManifest));
        kv.put("git_commit", gitCommit == null ? "unrecorded" : gitCommit);
        StringBuilder a = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            boolean path = i > 0 && (args[i - 1].equals("--data-dir") || args[i - 1].equals("--output"));
            a.append(i == 0 ? "" : " ").append(path ? shortPath(Path.of(args[i])) : args[i]);
        }
        kv.put("args", a.toString());
        kv.put("java_version", System.getProperty("java.version"));
        for (Class<?> c : configClasses) {
            for (Field f : c.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (!Modifier.isStatic(mod) || !Modifier.isFinal(mod) || f.isSynthetic()) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    kv.put("config." + c.getSimpleName() + "." + f.getName(), render(f.get(null)));
                } catch (ReflectiveOperationException | RuntimeException e) {
                    kv.put("config." + c.getSimpleName() + "." + f.getName(), "?");
                }
            }
        }
        writeKv(model.resolveSibling(model.getFileName() + PROVENANCE_SUFFIX), kv);
    }

    /** Last two path components: identifies a run without recording a machine path. */
    static String shortPath(Path p) {
        Path n = p.toAbsolutePath().normalize();
        int c = n.getNameCount();
        return c >= 2 ? n.subpath(c - 2, c).toString() : n.toString();
    }

    private static String render(Object v) {
        if (v instanceof int[]) {
            return Arrays.toString((int[]) v);
        }
        if (v instanceof double[]) {
            return Arrays.toString((double[]) v);
        }
        if (v instanceof float[]) {
            return Arrays.toString((float[]) v);
        }
        if (v instanceof long[]) {
            return Arrays.toString((long[]) v);
        }
        if (v instanceof Object[]) {
            return Arrays.deepToString((Object[]) v);
        }
        return String.valueOf(v);
    }

    static String sha256(Path p) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        byte[] buf = new byte[1 << 16];
        try (InputStream is = Files.newInputStream(p)) {
            int n;
            while ((n = is.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        }
        StringBuilder sb = new StringBuilder(64);
        for (byte b : md.digest()) {
            sb.append(String.format(java.util.Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }
}
