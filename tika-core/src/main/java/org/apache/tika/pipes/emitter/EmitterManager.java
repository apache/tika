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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class that will apply the appropriate fetcher
 * to the fetcherString based on the prefix.
 *
 * This does not allow multiple fetchers supporting the same prefix.
 */
public class EmitterManager {

    private final Map<String, Emitter> emitterMap = new ConcurrentHashMap<>();


    public EmitterManager(List<Emitter> emitters) {
        for (Emitter emitter : emitters) {
                if (emitterMap.containsKey(emitter.getName())) {
                    throw new IllegalArgumentException(
                            "Multiple emitters cannot support the same name: "
                            + emitter.getName());
                }
                emitterMap.put(emitter.getName(), emitter);

        }
    }

    public Set<String> getSupported() {
        return emitterMap.keySet();
    }


    public Emitter getEmitter(String emitterName) {
        Emitter emitter = emitterMap.get(emitterName);
        if (emitter == null) {
            throw new IllegalArgumentException("Can't find emitter for prefix: "+
                    emitterName);
        }
        return emitter;
    }
}
