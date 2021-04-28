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
import java.util.Objects;

import org.apache.tika.sax.BasicContentHandlerFactory;

public class HandlerConfig implements Serializable {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -3861669115439125268L;

    public static HandlerConfig DEFAULT_HANDLER_CONFIG =
            new HandlerConfig(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1, -1);

    private BasicContentHandlerFactory.HANDLER_TYPE type =
            BasicContentHandlerFactory.HANDLER_TYPE.TEXT;
    int writeLimit = -1;
    int maxEmbeddedResources = -1;

    public HandlerConfig(BasicContentHandlerFactory.HANDLER_TYPE type, int writeLimit,
                         int maxEmbeddedResources) {
        this.type = type;
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

    @Override
    public String toString() {
        return "HandlerConfig{" + "type=" + type + ", writeLimit=" + writeLimit +
                ", maxEmbeddedResources=" + maxEmbeddedResources + '}';
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
                type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, writeLimit, maxEmbeddedResources);
    }
}
