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

import java.io.Closeable;
import java.io.IOException;

/**
 * This is called asynchronously by the AsyncProcessor. This
 * is not thread safe, and implementers must be careful to implement
 * {@link #report(FetchEmitTuple, PipesResult, long)} in a thread safe
 * way.
 * <p/>
 * Note, however, that this is not called in the forked processes.
 * Implementers do not have to worry about synchronizing across processes;
 * for example, one could use an in-memory h2 database as a target.
 */
public abstract class PipesReporter implements Closeable {

    public static final PipesReporter NO_OP_REPORTER = new PipesReporter() {

        @Override
        public void report(FetchEmitTuple t, PipesResult result, long elapsed) {

        }
    };

    public abstract void report(FetchEmitTuple t, PipesResult result, long elapsed);

    /**
     * No-op implementation.  Override for custom behavior
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        //no-op
    }
}
