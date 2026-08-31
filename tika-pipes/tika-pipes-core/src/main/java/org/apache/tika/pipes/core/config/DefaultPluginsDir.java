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
package org.apache.tika.pipes.core.config;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the default {@code plugins} directory when {@code plugin-roots}
 * is not configured (TIKA-4864). The probe order matches the install
 * layouts Tika ships:
 * <ol>
 *   <li>next to the jar the anchor class was loaded from (a flat install,
 *       tika-grpc's docker image);</li>
 *   <li>next to that directory's parent (the unpacked distributions and the
 *       tika-server docker image load classes from {@code lib/}, the plugins
 *       sit beside {@code lib/});</li>
 *   <li>a {@code plugins} directory in the current working directory.</li>
 * </ol>
 * The result is always absolute: the forked pipes server resolves the
 * configured value against its own working directory, which need not be the
 * parent's, so a relative default would make the two processes disagree.
 */
public final class DefaultPluginsDir {

    /**
     * The directory name probed in each location.
     */
    public static final String PLUGINS_DIR_NAME = "plugins";

    private DefaultPluginsDir() {
    }

    /**
     * Resolves the default plugins directory for the install layout of the
     * given class.
     *
     * @param anchor the class whose code source anchors the probe, usually
     *               the caller
     * @return the absolute path of the first {@code plugins} directory found,
     * or the absolute path of {@code plugins} in the working directory if
     * none exists yet
     */
    public static String resolve(Class<?> anchor) {
        Path codeSourceDir = null;
        try {
            codeSourceDir = Path.of(anchor.getProtectionDomain().getCodeSource().getLocation()
                    .toURI()).getParent();
        } catch (Exception e) {
            //no code source (e.g. a repacked classloader): probe the working
            //directory only
        }
        return resolve(codeSourceDir, Path.of("")).toString();
    }

    /**
     * The probe itself, separated from the code-source lookup for testing.
     *
     * @param codeSourceDir the directory holding the anchor's jar, or null
     * @param cwd           the working directory to fall back to
     * @return the absolute path of the resolved directory
     */
    public static Path resolve(Path codeSourceDir, Path cwd) {
        if (codeSourceDir != null) {
            Path nextToJar = codeSourceDir.resolve(PLUGINS_DIR_NAME);
            if (Files.isDirectory(nextToJar)) {
                return nextToJar.toAbsolutePath();
            }
            Path parent = codeSourceDir.getParent();
            if (parent != null) {
                Path nextToParent = parent.resolve(PLUGINS_DIR_NAME);
                if (Files.isDirectory(nextToParent)) {
                    return nextToParent.toAbsolutePath();
                }
            }
        }
        return cwd.resolve(PLUGINS_DIR_NAME).toAbsolutePath();
    }
}
