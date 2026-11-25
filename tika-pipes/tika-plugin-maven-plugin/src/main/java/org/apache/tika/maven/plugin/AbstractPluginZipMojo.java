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
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;

abstract class AbstractPluginZipMojo extends AbstractMojo {

    @Component
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    private RepositorySystemSession repositorySession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    private List<RemoteRepository> remoteRepositories;

    Path resolve(Artifact artifact) throws MojoExecutionException {
        try {
            return repositorySystem.resolveArtifact(repositorySession,
                            new ArtifactRequest(artifact, remoteRepositories, null))
                    .getArtifact().getFile().toPath();
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException("could not resolve " + artifact, e);
        }
    }

    /**
     * Resolves exactly the jars the plugin jar's lib list names, with no transitive
     * resolution, and writes {@code <artifactId>-<version>.zip} into {@code outputDirectory}.
     */
    Path buildZip(Path pluginJar, String artifactId, String version, Path outputDirectory)
            throws MojoExecutionException {
        Path zip = outputDirectory.resolve(artifactId + "-" + version + ".zip");
        try {
            Map<String, Path> libs = new TreeMap<>();
            for (PluginLib.Entry entry : PluginZip.readLib(pluginJar)) {
                Path file = resolve(new DefaultArtifact(entry.groupId(), entry.artifactId(),
                        entry.classifier(), entry.extension(), entry.version()));
                // a snapshot is rebuilt between the plugin's build and this one
                if (!entry.isSnapshot() && !entry.sha256().equals(PluginZip.sha256(file))) {
                    throw new MojoExecutionException(entry.coordinates() + " at " + file
                            + " does not match the sha256 in " + pluginJar.getFileName()
                            + " (" + entry.sha256() + ")");
                }
                if (libs.put(entry.fileName(), file) != null) {
                    throw new MojoExecutionException("two lib entries named "
                            + entry.fileName() + " in " + pluginJar.getFileName());
                }
            }
            PluginZip.write(pluginJar, PluginLib.libFileName(artifactId, version, "", "jar"),
                    libs, zip);
        } catch (IOException e) {
            throw new MojoExecutionException("could not build " + zip, e);
        }
        getLog().info("Built " + zip);
        return zip;
    }
}
