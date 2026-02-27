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
package org.apache.tika.config;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.tika.parser.ParseContext;

/**
 * Tracks parse progress for the two-tier timeout system.
 * <p>
 * Parsers call {@link #update(ParseContext)} after completing a unit of work
 * (e.g., finishing an external process for one page of OCR). The monitoring loop
 * reads {@link #getLastProgressMillis()} to detect stalled parsers.
 * <p>
 * This is runtime-only state (NOT Serializable) — it is placed into
 * {@link ParseContext} on the server side before submitting a parse task
 * and is never sent over the wire.
 *
 * @since Apache Tika 4.0
 */
public class TikaProgressTracker {

    private final AtomicLong lastProgressMillis;

    /**
     * Creates a tracker initialized to the current time.
     */
    public TikaProgressTracker() {
        this.lastProgressMillis = new AtomicLong(System.currentTimeMillis());
    }

    /**
     * Signals progress from a ParseContext. If no tracker is present, this is a no-op.
     * <p>
     * This is the preferred way for parsers to signal progress:
     * <pre>
     * TikaProgressTracker.update(context);
     * </pre>
     *
     * @param context the ParseContext (may be null)
     */
    public static void update(ParseContext context) {
        if (context == null) {
            return;
        }
        TikaProgressTracker tracker = context.get(TikaProgressTracker.class);
        if (tracker != null) {
            tracker.update();
        }
    }

    /**
     * Signals progress directly on this tracker instance.
     */
    public void update() {
        lastProgressMillis.set(System.currentTimeMillis());
    }

    /**
     * Returns the epoch millis of the last progress update.
     *
     * @return epoch millis of last progress
     */
    public long getLastProgressMillis() {
        return lastProgressMillis.get();
    }
}
