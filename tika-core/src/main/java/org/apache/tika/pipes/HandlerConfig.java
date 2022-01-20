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
package org.apache.tika.pipes;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

import org.apache.tika.sax.BasicContentHandlerFactory;

public class HandlerConfig implements Serializable {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -3861669115439125268L;

    public static final HandlerConfig DEFAULT_HANDLER_CONFIG =
            new HandlerConfig(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, PARSE_MODE.RMETA,
                    -1, -1);

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

    private BasicContentHandlerFactory.HANDLER_TYPE type =
            BasicContentHandlerFactory.HANDLER_TYPE.TEXT;

    int writeLimit = -1;
    int maxEmbeddedResources = -1;
    PARSE_MODE parseMode = PARSE_MODE.RMETA;


    public HandlerConfig(BasicContentHandlerFactory.HANDLER_TYPE type, PARSE_MODE parseMode,
                         int writeLimit,
                         int maxEmbeddedResources) {
        this.type = type;
        this.parseMode = parseMode;
        this.writeLimit = writeLimit;
        this.maxEmbeddedResources = maxEmbeddedResources;
    }

    public BasicContentHandlerFactory.HANDLER_TYPE getType() {
        return type;
    }

    public int getWriteLimit() {
        return writeLimit;
    }

    public int getMaxEmbeddedResources() {
        return maxEmbeddedResources;
    }

    public PARSE_MODE getParseMode() {
        return parseMode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HandlerConfig that = (HandlerConfig) o;
        return writeLimit == that.writeLimit && maxEmbeddedResources == that.maxEmbeddedResources &&
                type == that.type && parseMode == that.parseMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, writeLimit, maxEmbeddedResources, parseMode);
    }

    @Override
    public String toString() {
        return "HandlerConfig{" + "type=" + type + ", writeLimit=" + writeLimit +
                ", maxEmbeddedResources=" + maxEmbeddedResources + ", mode=" + parseMode + '}';
    }
}
