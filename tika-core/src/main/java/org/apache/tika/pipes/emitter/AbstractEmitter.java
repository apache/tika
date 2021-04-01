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
package org.apache.tika.pipes.emitter;

import java.io.IOException;
import java.util.List;

import org.apache.tika.config.Field;
import org.apache.tika.metadata.Metadata;

public abstract class AbstractEmitter implements Emitter {

    private String name;

    public static long estimateSizeInBytes(String id, List<Metadata> metadataList) {
        long sz = 36 + id.length() * 2;
        for (Metadata m : metadataList) {
            for (String n : m.names()) {
                sz += 36 + n.length() * 2;
                for (String v : m.getValues(n)) {
                    sz += 36 + v.length() * 2;
                }
            }
        }
        return sz;
    }

    @Override
    public String getName() {
        return name;
    }

    @Field
    public void setName(String name) {
        this.name = name;
    }

    /**
     * The default behavior is to call {@link #emit(String, List)} on each item.
     * Some implementations, e.g. Solr/ES/vespa, can benefit from subclassing this and
     * emitting a bunch of docs at once.
     *
     * @param emitData
     * @throws IOException
     * @throws TikaEmitterException
     */
    @Override
    public void emit(List<? extends EmitData> emitData) throws IOException, TikaEmitterException {
        for (EmitData d : emitData) {
            emit(d.getEmitKey().getEmitKey(), d.getMetadataList());
        }
    }
}
