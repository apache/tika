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
package org.apache.tika.parser.inference;

import java.io.Closeable;
import java.io.IOException;

/**
 * A model or service an inference binding calls: one endpoint or one local binary with its
 * settings, named in the {@code "engines"} map. An engine knows nothing about documents; a
 * binding says what it is fed and what to ask. An engine lives as long as the config that
 * loaded it: {@link EngineRegistry#close()} closes every engine once at shutdown.
 *
 * @since Apache Tika 4.1
 */
public interface Engine extends Closeable {

    /** Releases what the engine holds (a client, a native handle); nothing by default. */
    @Override
    default void close() throws IOException {
    }
}
