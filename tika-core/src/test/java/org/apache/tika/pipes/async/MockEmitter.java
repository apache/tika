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
package org.apache.tika.pipes.async;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.emitter.AbstractEmitter;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.emitter.TikaEmitterException;

public class MockEmitter extends AbstractEmitter {

    static ArrayBlockingQueue<EmitData> EMIT_DATA = new ArrayBlockingQueue<>(10000);

    public MockEmitter() {
    }

    public static List<EmitData> getData() {
        return new ArrayList<>(EMIT_DATA);
    }

    @Override
    public void emit(String emitKey, List<Metadata> metadataList, ParseContext parseContext)
            throws IOException, TikaEmitterException {
        emit(
                Collections.singletonList(new EmitData(new EmitKey(getName(), emitKey),
                 metadataList, null, parseContext)));
    }

    @Override
    public void emit(List<? extends EmitData> emitData) throws IOException, TikaEmitterException {
        int inserted = 0;
        for (EmitData d : emitData) {
            EMIT_DATA.offer(d);
        }
    }

}
