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
package org.apache.tika.pipes.fetchers.filesystem;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FilenameUtils;
import org.pf4j.Extension;

import org.apache.tika.pipes.core.emitter.EmitOutput;
import org.apache.tika.pipes.core.emitter.Emitter;
import org.apache.tika.pipes.core.emitter.EmitterConfig;
import org.apache.tika.pipes.core.emitter.OnExistBehavior;

@Extension
public class FileSystemEmitter implements Emitter {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private FileSystemEmitterConfig fileSystemEmitterConfig;

    @Override
    public void init(EmitterConfig emitterConfig) {
        this.fileSystemEmitterConfig = (FileSystemEmitterConfig) emitterConfig;
    }

    @Override
    public String getPluginId() {
        return "filesystem-emitter";
    }

    @Override
    public void emit(List<EmitOutput> emitOutputs)
            throws IOException {
        String addFileExtension = fileSystemEmitterConfig.getAddFileExtension();
        OnExistBehavior onExists = OnExistBehavior.valueOf(fileSystemEmitterConfig
                .getOnExists()
                .toUpperCase(Locale.ROOT));
        for (EmitOutput emitOutput : emitOutputs) {
            Path output;
            String emitKey = FilenameUtils.getName(emitOutput.getFetchKey());
            if (addFileExtension != null && !addFileExtension.isEmpty()) {
                emitKey += "." + addFileExtension;
            }
            if (fileSystemEmitterConfig.getOutputDir() != null) {
                output = Paths.get(fileSystemEmitterConfig.getOutputDir()).resolve(emitKey);
            } else {
                output = Paths.get(emitKey);
            }
            if (!Files.isDirectory(output.getParent())) {
                Files.createDirectories(output.getParent());
            }
            if (onExists == OnExistBehavior.SKIP && Files.isRegularFile(output)) {
                continue;
            } else if (onExists == OnExistBehavior.EXCEPTION && Files.isRegularFile(output)) {
                throw new FileAlreadyExistsException(output.toString());
            }
            try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
                OBJECT_MAPPER.writeValue(writer, emitOutput.getMetadata());
            }
        }
    }
}
