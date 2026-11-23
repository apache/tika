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
package org.apache.tika.pipes.core.extractor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;

import org.apache.tika.config.ParseTimeout;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.emitter.Emitter;
import org.apache.tika.pipes.api.emitter.StreamEmitter;
import org.apache.tika.pipes.core.emitter.TikaEmitterException;

public class EmittingUnpackHandler extends AbstractUnpackHandler {
    private final EmitKey containerEmitKey;
    private final UnpackConfig unpackConfig;
    private final StreamEmitter emitter;

    // staged, not emitted during the parse: an emitter cannot take back a failed file's output
    private final List<Staged> staged = new ArrayList<>();
    private Path tempDirectory;

    private record Staged(String emitKey, Path file) {
    }

    private static final Metadata METADATA = new Metadata();
    private static final ParseContext PARSE_CONTEXT = new ParseContext();

    /**
     * @param emitter the caller keeps it open (leased) until this handler is closed
     */
    public EmittingUnpackHandler(FetchEmitTuple fetchEmitTuple, Emitter emitter,
                                 ParseContext parseContext) throws TikaException {

        this.containerEmitKey = fetchEmitTuple.getEmitKey();
        this.unpackConfig = parseContext.get(UnpackConfig.class);
        if (this.unpackConfig == null) {
            throw new TikaConfigException("UnpackConfig must not be null!");
        }
        if (! (emitter instanceof StreamEmitter)) {
            throw new TikaConfigException("Emitter " +
                    unpackConfig.getEmitter()
                    + " must implement a StreamEmitter");
        }
        this.emitter = (StreamEmitter) emitter;
    }

    @Override
    public void add(int id, Metadata metadata, InputStream inputStream) throws IOException {
        //intentionally do not call super.add, because we want the ids list to be empty
        String emitKey = getEmitKey(containerEmitKey.getEmitKey(),
                id, unpackConfig, metadata);
        if (tempDirectory == null) {
            tempDirectory = Files.createTempDirectory("tika-unpack-");
        }
        Path tempFile = tempDirectory.resolve(Integer.toString(staged.size()));
        try (OutputStream os = newStagingOutputStream(tempFile)) {
            inputStream.transferTo(os);
        }
        staged.add(new Staged(emitKey, tempFile));
    }

    /**
     * Emits what the parse staged; call only once the file is known not to have failed.
     *
     * @return how many files were emitted
     */
    public int emitStaged(ParseContext parseContext) throws IOException {
        int emitted = staged.size();
        for (Staged s : staged) {
            // each emit is progress, as it was when they ran between embedded parses
            ParseTimeout.checkpoint(parseContext);
            try (InputStream is = Files.newInputStream(s.file())) {
                emitter.emit(s.emitKey(), is, METADATA, PARSE_CONTEXT);
            } catch (TikaEmitterException e) {
                throw new IOException(e);
            }
            deleteStaged(s.file());
        }
        staged.clear();
        return emitted;
    }

    @Override
    public void close() throws IOException {
        // the emitter is leased by the task, not owned here
        try {
            if (tempDirectory != null) {
                FileUtils.deleteDirectory(tempDirectory.toFile());
                tempDirectory = null;
            }
        } finally {
            releaseStaging();
        }
    }
}
