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
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.artifact.DefaultArtifact;

/**
 * Builds Tika pipes plugin zips from jars resolved through Maven: each plugin jar names the
 * jars its {@code lib/} holds, and those are fetched and checked against its checksums.
 * Runs without a project:
 * {@code mvn org.apache.tika:tika-plugin-maven-plugin:<version>:assemble
 * -Dtika.pipesPlugins=tika-pipes-s3 -Dtika.plugins.outputDirectory=plugins}.
 */
@Mojo(name = "assemble", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, requiresProject = false,
        threadSafe = true)
public class AssembleMojo extends AbstractPluginZipMojo {

    private static final String TIKA_GROUP = "org.apache.tika";

    /**
     * Plugins to build, each {@code artifactId}, {@code groupId:artifactId} or
     * {@code groupId:artifactId:version}. The group defaults to {@code org.apache.tika} and
     * the version to {@link #tikaVersion}.
     */
    @Parameter(property = "tika.pipesPlugins", required = true)
    private List<String> pipesPlugins;

    /**
     * Defaults to this Maven plugin's own version.
     */
    @Parameter(property = "tika.version")
    private String tikaVersion;

    @Parameter(property = "tika.plugins.outputDirectory",
            defaultValue = "${project.build.directory}/tika-plugins", required = true)
    private File outputDirectory;

    @Parameter(defaultValue = "${plugin}", readonly = true, required = true)
    private PluginDescriptor plugin;

    @Override
    public void execute() throws MojoExecutionException {
        String defaultVersion = tikaVersion == null ? plugin.getVersion() : tikaVersion;
        Path out = outputDirectory.toPath();
        for (String spec : pipesPlugins) {
            String[] c = spec.strip().split(":");
            if (c.length < 1 || c.length > 3 || c[c.length - 1].isEmpty()) {
                throw new MojoExecutionException("expected artifactId, groupId:artifactId or "
                        + "groupId:artifactId:version, got: " + spec);
            }
            String groupId = c.length == 1 ? TIKA_GROUP : c[0];
            String artifactId = c.length == 1 ? c[0] : c[1];
            String version = c.length == 3 ? c[2] : defaultVersion;
            Path jar = resolve(new DefaultArtifact(groupId, artifactId, "jar", version));
            buildZip(jar, artifactId, version, out);
        }
    }
}
