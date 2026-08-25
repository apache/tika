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

/**
 * Read-only view of a set of forked pipes workers, for monitoring.
 * <p>
 * A process can own more than one pool -- tika-server runs a {@link PipesParser} for the
 * sync endpoints and an {@link org.apache.tika.pipes.core.async.AsyncProcessor} for
 * {@code /async}, each with its own forks -- so a consumer that reads only one of them
 * reports a fraction of the fleet. Implementations are polled; they must not block on a
 * worker or take a lock a parse thread holds for the length of a parse.
 */
public interface PipesWorkerPool {

    /** Worker slots configured for this pool. Under shared-server mode these share one fork. */
    int getNumClients();

    /** Restarts performed so far for {@code reason}, summed over this pool's workers. */
    long getRestartCount(RestartReason reason);
}
