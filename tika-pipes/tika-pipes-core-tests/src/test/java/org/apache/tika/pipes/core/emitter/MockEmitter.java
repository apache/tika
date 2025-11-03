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
package org.apache.tika.pipes.core.emitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.pf4j.Extension;

import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.emitter.EmitData;
import org.apache.tika.pipes.api.emitter.Emitter;
import org.apache.tika.plugins.PluginConfig;

@Extension
public class MockEmitter implements Initializable, Emitter {

    public static ArrayBlockingQueue<EmitData> EMIT_DATA = new ArrayBlockingQueue<>(10000);

    public static List<EmitData> getData() {
        return new ArrayList<>(EMIT_DATA);
    }

    public MockEmitter() throws IOException {
    }

    private static record MockEmitterConfig(boolean throwOnCheck) {

    }

    private MockEmitterConfig config = new MockEmitterConfig(true);

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        //no-op
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {

        if (config.throwOnCheck()) {
            throw new TikaConfigException("throw on check");
        }

    }

    @Override
    public void configure(PluginConfig pluginConfig) throws TikaConfigException, IOException {
        config = new ObjectMapper().readValue(pluginConfig.jsonConfig(), MockEmitterConfig.class);
    }

    @Override
    public String getPluginId() {
        return "mock-emitter";
    }

    @Override
    public void emit(String emitKey, List<Metadata> metadataList, ParseContext parseContext)
            throws IOException, TikaEmitterException {
        emit(
                Collections.singletonList(new EmitDataImpl(emitKey,
                        metadataList, null, parseContext)));
    }
    @Override
    public void emit(List<? extends EmitData> emitData) throws IOException, TikaEmitterException {

        for (EmitData d : emitData) {
            EMIT_DATA.offer(d);
        }
    }
}
