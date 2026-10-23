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
package org.apache.tika.pipes.emitter.fs;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.plugins.PluginJson;

public record FileSystemEmitterConfig(String basePath, String fileExtension, ON_EXISTS onExists, boolean prettyPrint, boolean allowAbsolutePaths,
        Boolean atomicWrites, COMPRESSION compression) {

    enum ON_EXISTS {
        SKIP, EXCEPTION, REPLACE
    }

    enum COMPRESSION {
        NONE, GZIP
    }

    /**
     * onExists absent means EXCEPTION, atomicWrites absent means true, compression absent means
     * NONE -- the documented defaults.
     */
    public FileSystemEmitterConfig {
        if (onExists == null) {
            onExists = ON_EXISTS.EXCEPTION;
        }
        if (atomicWrites == null) {
            atomicWrites = Boolean.TRUE;
        }
        if (compression == null) {
            compression = COMPRESSION.NONE;
        }
    }

    boolean gzip() {
        return compression == COMPRESSION.GZIP;
    }

    public static FileSystemEmitterConfig load(final String json)
            throws TikaConfigException {
        return PluginJson.read(json, FileSystemEmitterConfig.class);
    }

}
