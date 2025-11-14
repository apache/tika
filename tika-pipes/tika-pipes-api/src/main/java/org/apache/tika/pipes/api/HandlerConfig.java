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
package org.apache.tika.pipes.api;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

import org.apache.tika.sax.BasicContentHandlerFactory;

//TODO -- convert this back to a record
public class HandlerConfig implements Serializable {

    /**
     * {@link PARSE_MODE#RMETA} "recursive metadata" is the same as the -J option
     * in tika-app and the /rmeta endpoint in tika-server.  Each embedded file is represented as
     * its own metadata object.
     *
     * {@link PARSE_MODE#CONCATENATE} is similar
     * to the legacy tika-app behavior and the /tika endpoint (accept: application/json) in
     * tika-server.  This concatenates the
     * contents of embedded files and returns a single metadata object for the file no
     * matter how many embedded objects there are; this option throws away metadata from
     * embedded objects and silently skips exceptions in embedded objects.
     */
    public enum PARSE_MODE {
        RMETA,
        CONCATENATE;

        public static PARSE_MODE parseMode(String modeString) {
            for (PARSE_MODE m : PARSE_MODE.values()) {
                if (m.name().equalsIgnoreCase(modeString)) {
                    return m;
                }
            }
            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (PARSE_MODE m : PARSE_MODE.values()) {
                if (i++ > 0) {
                    sb.append(", ");
                }
                sb.append(m.name().toLowerCase(Locale.US));
            }
            throw new IllegalArgumentException("mode must be one of: (" + sb +
                    "). I regret I do not understand: " + modeString);
        }
    }
    BasicContentHandlerFactory.HANDLER_TYPE type = BasicContentHandlerFactory.HANDLER_TYPE.TEXT;
    PARSE_MODE parseMode = PARSE_MODE.RMETA;
    int writeLimit = -1;
    int maxEmbeddedResources = -1;
    boolean throwOnWriteLimitReached = true;

    public HandlerConfig() {

    }

    public HandlerConfig(BasicContentHandlerFactory.HANDLER_TYPE type, PARSE_MODE parseMode, int writeLimit, int maxEmbeddedResources, boolean throwOnWriteLimitReached) {
        this.type = type;
        this.parseMode = parseMode;
        this.writeLimit = writeLimit;
        this.maxEmbeddedResources = maxEmbeddedResources;
        this.throwOnWriteLimitReached = throwOnWriteLimitReached;
    }

    public BasicContentHandlerFactory.HANDLER_TYPE getType() {
        return type;
    }

    public void setType(BasicContentHandlerFactory.HANDLER_TYPE type) {
        this.type = type;
    }

    public void setType(String typeString) {
        this.type = BasicContentHandlerFactory.HANDLER_TYPE.valueOf(typeString);
    }

    public PARSE_MODE getParseMode() {
        return parseMode;
    }

    public void setParseMode(PARSE_MODE parseMode) {
        this.parseMode = parseMode;
    }

    public void setParseMode(String parseMode) {
        this.parseMode = PARSE_MODE.valueOf(parseMode);
    }

    public int getWriteLimit() {
        return writeLimit;
    }

    public void setWriteLimit(int writeLimit) {
        this.writeLimit = writeLimit;
    }

    public int getMaxEmbeddedResources() {
        return maxEmbeddedResources;
    }

    public void setMaxEmbeddedResources(int maxEmbeddedResources) {
        this.maxEmbeddedResources = maxEmbeddedResources;
    }

    public boolean isThrowOnWriteLimitReached() {
        return throwOnWriteLimitReached;
    }

    public void setThrowOnWriteLimitReached(boolean throwOnWriteLimitReached) {
        this.throwOnWriteLimitReached = throwOnWriteLimitReached;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof HandlerConfig that)) {
            return false;
        }

        return writeLimit == that.writeLimit && maxEmbeddedResources == that.maxEmbeddedResources && throwOnWriteLimitReached == that.throwOnWriteLimitReached &&
                type == that.type && parseMode == that.parseMode;
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(type);
        result = 31 * result + Objects.hashCode(parseMode);
        result = 31 * result + writeLimit;
        result = 31 * result + maxEmbeddedResources;
        result = 31 * result + Boolean.hashCode(throwOnWriteLimitReached);
        return result;
    }
}
