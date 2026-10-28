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
package org.apache.tika.pipes.core;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A hold on a fetcher or emitter instance from {@link AbstractComponentManager}. While any lease
 * is open the instance is not closed, even if a runtime save or delete has replaced it. Hold it
 * for as long as anything the component returned is in use (a fetched stream is read long after
 * {@code fetch()} returns), then close it. Closing twice is a no-op.
 *
 * @param <T> the component type
 */
public final class Lease<T> implements AutoCloseable {

    private final T component;
    private final Runnable release;
    private final AtomicBoolean released = new AtomicBoolean(false);

    Lease(T component, Runnable release) {
        this.component = component;
        this.release = release;
    }

    /**
     * @return the leased component; do not use it after {@link #close()}
     */
    public T get() {
        return component;
    }

    @Override
    public void close() {
        if (released.compareAndSet(false, true)) {
            release.run();
        }
    }
}
