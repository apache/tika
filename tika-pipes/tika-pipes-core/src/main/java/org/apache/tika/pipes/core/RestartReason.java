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
 * Why a forked pipes server was (or will be) restarted. Bounded on purpose: these
 * become metric tag values.
 */
public enum RestartReason {
    OOM,
    TIMEOUT,
    CRASH,
    MAX_FILES,
    CONNECTION_ABANDONED,
    /** The worker exited on its own (exit 24) after sitting idle for socketTimeoutMillis. */
    IDLE,
    /**
     * The worker exited cleanly (exit 0) because the parent sent it SHUT_DOWN -- typically after a
     * failed health check prompted a reconnect. The replacement is deliberate, not a failure.
     */
    SHUTDOWN
}
