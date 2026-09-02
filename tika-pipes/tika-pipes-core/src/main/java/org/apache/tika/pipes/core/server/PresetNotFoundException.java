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
package org.apache.tika.pipes.core.server;

import org.apache.tika.exception.TikaConfigException;

/**
 * A request named a preset this server's config does not activate: a caller error,
 * answered with a {@code PRESET_NOT_FOUND} result rather than the crash path.
 *
 * @since Apache Tika 4.1.0
 */
public class PresetNotFoundException extends TikaConfigException {

    public PresetNotFoundException(String msg) {
        super(msg);
    }
}
