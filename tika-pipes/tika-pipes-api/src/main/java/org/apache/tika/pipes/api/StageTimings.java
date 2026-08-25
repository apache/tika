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

/**
 * Server-side per-stage timings attached to a {@link PipesResult} for the
 * structured timing log.
 * <p>
 * Values are nanoseconds; -1 indicates the stage did not run (e.g., emit was
 * skipped for a passback result, or fetch failed before parse started).
 * <p>
 * Failure paths (OOM, TIMEOUT, UNSPECIFIED_CRASH) generally do not produce a
 * normal FINISHED message and therefore carry no server timings — only
 * client-side timings will be available for those parses.
 */
public record StageTimings(long fetchNanos, long parseNanos, long emitNanos,
                           long serverWallNanos) implements Serializable {

    public static final long NOT_RUN = -1L;
}
