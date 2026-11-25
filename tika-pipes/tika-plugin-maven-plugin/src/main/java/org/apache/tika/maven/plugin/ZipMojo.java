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
import java.nio.file.Path;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.DefaultArtifact;

/**
 * Builds this plugin module's zip from its packaged jar, the same way {@code assemble} builds
 * it for a consumer.
 */
@Mojo(name = "zip", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class ZipMojo extends AbstractPluginZipMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}", required = true)
    private File outputDirectory;

    /**
     * Also put the zip in the local repository as {@code groupId:artifactId:zip:version}.
     * The zip is not attached to the project, so {@code install} and {@code deploy} skip it;
     * this is for modules of the same build that depend on the zip.
     */
    @Parameter(property = "tika.plugin.installZip", defaultValue = "false")
    private boolean installZip;

    @Override
    public void execute() throws MojoExecutionException {
        if ("pom".equals(project.getPackaging())) {
            return;
        }
        File jar = project.getArtifact().getFile();
        if (jar == null || !jar.isFile()) {
            throw new MojoExecutionException(
                    "the plugin jar is not built yet; run 'zip' after the jar goal");
        }
        Path zip = buildZip(jar.toPath(), project.getArtifactId(), project.getVersion(),
                outputDirectory.toPath());
        if (installZip) {
            installLocally(new DefaultArtifact(project.getGroupId(), project.getArtifactId(),
                    "zip", project.getVersion()).setFile(zip.toFile()));
        }
    }
}
