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
package org.apache.tika.pipes.api.emitter;

import java.io.IOException;
import java.util.List;

import org.apache.tika.config.ComponentConfigs;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.plugins.TikaExtension;

/**
 * Interface for emitting parsed content and metadata.
 * <p>
 * The {@code componentConfigs} parameter contains component-specific JSON
 * configuration strings that can be used for runtime configuration overrides.
 * Emitters can extract their specific configuration using their component name.
 */
public interface Emitter extends TikaExtension {

    /**
     * Emits metadata list for a given emit key.
     *
     * @param emitKey the key identifying where to emit
     * @param metadataList list of metadata to emit
     * @param componentConfigs component-specific configurations for runtime overrides
     * @throws IOException if there's an I/O error during emission
     */
    void emit(String emitKey, List<Metadata> metadataList, ComponentConfigs componentConfigs) throws IOException;

    /**
     * Emits a batch of emit data.
     *
     * @param emitData list of data to emit
     * @param componentConfigs component-specific configurations for runtime overrides
     * @throws IOException if there's an I/O error during emission
     */
    void emit(List<? extends EmitData> emitData, ComponentConfigs componentConfigs) throws IOException;

    //TODO -- add this later for xhtml?
    //void emit(String txt, Metadata metadata) throws IOException, TikaException;

}
