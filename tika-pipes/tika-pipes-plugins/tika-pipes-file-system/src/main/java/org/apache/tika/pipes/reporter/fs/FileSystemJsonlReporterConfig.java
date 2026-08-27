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
package org.apache.tika.pipes.reporter.fs;

import java.nio.file.Path;
import java.util.Set;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.plugins.PluginJson;

public record FileSystemJsonlReporterConfig(Path path, Set<String> includes, Set<String> excludes, ON_EXISTS onExists, int maxMessageLength) {

    public enum ON_EXISTS {
        EXCEPTION, APPEND, REPLACE
    }

    public static final int DEFAULT_MAX_MESSAGE_LENGTH = 10_000;

    public FileSystemJsonlReporterConfig {
        if (onExists == null) {
            onExists = ON_EXISTS.EXCEPTION;
        }
        if (maxMessageLength <= 0) {
            maxMessageLength = DEFAULT_MAX_MESSAGE_LENGTH;
        }
    }

    public static FileSystemJsonlReporterConfig load(final String json) throws TikaConfigException {
        return PluginJson.read(json, FileSystemJsonlReporterConfig.class);
    }
}
