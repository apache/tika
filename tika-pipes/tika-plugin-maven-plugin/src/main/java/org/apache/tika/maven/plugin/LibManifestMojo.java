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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Writes the plugin's runtime dependencies, with checksums, to {@value PluginLib#PATH} so the
 * jar carries the contents of its own {@code lib/}. Needs packaged dependencies: in a reactor
 * build, run at least {@code package}.
 */
@Mojo(name = "lib-manifest", defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
        requiresDependencyResolution = ResolutionScope.RUNTIME, threadSafe = true)
public class LibManifestMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
    private File outputDirectory;

    @Override
    public void execute() throws MojoExecutionException {
        if ("pom".equals(project.getPackaging())) {
            return;
        }
        List<PluginLib.Entry> entries = new ArrayList<>();
        try {
            for (Artifact a : project.getArtifacts()) {
                if (!Artifact.SCOPE_COMPILE.equals(a.getScope())
                        && !Artifact.SCOPE_RUNTIME.equals(a.getScope())) {
                    continue;
                }
                File file = a.getFile();
                if (file == null || !file.isFile()) {
                    throw new MojoExecutionException(a + " is not packaged (" + file
                            + "); the lib list needs jars, so build through 'package'");
                }
                entries.add(new PluginLib.Entry(a.getGroupId(), a.getArtifactId(),
                        a.getArtifactHandler().getExtension(), a.getClassifier(),
                        a.getBaseVersion(), PluginZip.sha256(file.toPath())));
            }
            Path out = outputDirectory.toPath().resolve(PluginLib.PATH);
            Files.createDirectories(out.getParent());
            Files.writeString(out, PluginLib.format(entries), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MojoExecutionException("could not write " + PluginLib.PATH, e);
        }
    }
}
