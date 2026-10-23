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
package org.apache.tika.test;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds config setters that look like they reach the host (a path, executable, class name,
 * URL, credential, ...) but carry no {@code @OperatorOnly}. A module's test asserts the
 * result is empty, so the next reflective or path-taking setter fails the build instead of
 * shipping as a per-request hole.
 * <p>
 * Scope: every class on the test classpath under {@code org.apache.tika} whose simple name
 * ends in {@code Config}, minus the package prefixes the caller excludes (surfaces that are
 * never per-request). The name test is a heuristic by design: a reviewed false positive is
 * annotated anyway, or excluded by the caller with a reason.
 */
public final class OperatorOnlyAudit {

    /** Setter-name suffixes that usually mean host, not document. */
    public static final Pattern SENSITIVE = Pattern.compile(
            "(?i).*(path|paths|executable|exe|command|cmd|binary|class|classname|url|uri|endpoint"
                    + "|host|hostname|apikey|secret|credential|credentials|token|password|passwd"
                    + "|bucket|region|model|dir|directory|file|keystore|truststore)$");

    private static final String ANNOTATION = "org.apache.tika.config.OperatorOnly";

    private OperatorOnlyAudit() {
    }

    /**
     * @param excludedPackagePrefixes packages whose config is never per-request
     * @param allowed                 reviewed {@code Class#setter} names to skip
     * @return {@code Class#setter} for every unannotated sensitive setter, sorted
     */
    public static List<String> unannotatedSensitiveSetters(List<String> excludedPackagePrefixes,
                                                           List<String> allowed) throws IOException {
        ClassLoader loader = OperatorOnlyAudit.class.getClassLoader();
        TreeSet<String> violations = new TreeSet<>();
        for (String className : configClassNames()) {
            if (excludedPackagePrefixes.stream().anyMatch(className::startsWith)) {
                continue;
            }
            Class<?> clazz;
            Method[] methods;
            try {
                clazz = Class.forName(className, false, loader);
                methods = clazz.getDeclaredMethods();
            } catch (Throwable e) {
                // optional dependency missing from this classpath; not auditable here
                continue;
            }
            for (Method m : methods) {
                if (!isSetter(m) || !SENSITIVE.matcher(m.getName().substring(3)).matches()) {
                    continue;
                }
                String id = clazz.getName() + "#" + m.getName();
                if (allowed.contains(id) || isAnnotated(clazz, m)) {
                    continue;
                }
                violations.add(id);
            }
        }
        return new ArrayList<>(violations);
    }

    private static boolean isSetter(Method m) {
        return Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers())
                && m.getName().startsWith("set") && m.getName().length() > 3
                && m.getParameterCount() == 1 && !m.isSynthetic();
    }

    /** The setter, an override above it, or the matching getter/is-er carries the annotation. */
    private static boolean isAnnotated(Class<?> clazz, Method setter) {
        String property = setter.getName().substring(3);
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                boolean sameSetter = m.getName().equals(setter.getName())
                        && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].equals(setter.getParameterTypes()[0]);
                boolean getter = m.getParameterCount() == 0
                        && (m.getName().equals("get" + property) || m.getName().equals("is" + property));
                if ((sameSetter || getter) && hasAnnotation(m)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasAnnotation(Method m) {
        for (Annotation a : m.getAnnotations()) {
            if (a.annotationType().getName().equals(ANNOTATION)) {
                return true;
            }
        }
        return false;
    }

    /** Every {@code org.apache.tika.**.*Config} class name on the test classpath. */
    static List<String> configClassNames() throws IOException {
        String classPath = System.getProperty("surefire.test.class.path",
                System.getProperty("java.class.path"));
        TreeSet<String> names = new TreeSet<>();
        for (String entry : classPath.split(File.pathSeparator)) {
            Path path = Paths.get(entry);
            if (Files.isDirectory(path)) {
                try (Stream<Path> walk = Files.walk(path)) {
                    walk.filter(Files::isRegularFile)
                            .map(p -> path.relativize(p).toString().replace(File.separatorChar, '/'))
                            .forEach(rel -> addIfConfig(names, rel));
                }
            } else if (Files.isRegularFile(path)
                    && path.toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                try (JarFile jar = new JarFile(path.toFile())) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        addIfConfig(names, entries.nextElement().getName());
                    }
                }
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(names));
    }

    private static void addIfConfig(TreeSet<String> names, String resource) {
        if (!resource.startsWith("org/apache/tika/") || !resource.endsWith(".class")) {
            return;
        }
        String name = resource.substring(0, resource.length() - ".class".length());
        int slash = name.lastIndexOf('/');
        int dollar = name.lastIndexOf('$');
        String simple = name.substring(Math.max(slash, dollar) + 1);
        if (simple.endsWith("Config")) {
            names.add(name.replace('/', '.'));
        }
    }
}
