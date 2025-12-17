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
package org.apache.tika.pipes.pipesiterator;

import java.util.Objects;

/**
 * Abstract base class for pipes iterator configurations.
 * Provides the common fetcherId and emitterId fields that all iterators need.
 * <p>
 * ContentHandlerFactory, ParseMode, and other parsing settings should be loaded
 * from tika-config.json via TikaLoader and set in PipesConfig.
 */
public abstract class PipesIteratorConfig {

    private String fetcherId;
    private String emitterId;

    public String getFetcherId() {
        return fetcherId;
    }

    public void setFetcherId(String fetcherId) {
        this.fetcherId = fetcherId;
    }

    public String getEmitterId() {
        return emitterId;
    }

    public void setEmitterId(String emitterId) {
        this.emitterId = emitterId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PipesIteratorConfig that)) return false;
        return Objects.equals(fetcherId, that.fetcherId) &&
                Objects.equals(emitterId, that.emitterId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fetcherId, emitterId);
    }
}
