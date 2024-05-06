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
package org.apache.tika.parser;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base class for parsers that call external processes. This adds one more layer of 'hope'
 * that processes won't be orphaned if the jvm has to be restarted. This does not guarantee that the
 * processes won't be orphaned in case of, e.g. kill -9, but this increases the chances that under
 * normal circumstances or if the jvm itself exits, that external processes won't be orphaned.
 *
 * @since Apache Tika 1.27
 */
public abstract class AbstractExternalProcessParser implements Parser {

    /** Serial version UID. */
    private static final long serialVersionUID = 7186985395903074255L;

    private static final ConcurrentHashMap<String, Process> PROCESS_MAP = new ConcurrentHashMap<>();

    static {
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    PROCESS_MAP.forEachValue(1, Process::destroyForcibly);
                                }));
    }

    protected String register(Process p) {
        String id = UUID.randomUUID().toString();
        PROCESS_MAP.put(id, p);
        return id;
    }

    protected Process release(String id) {
        return PROCESS_MAP.remove(id);
    }
}
