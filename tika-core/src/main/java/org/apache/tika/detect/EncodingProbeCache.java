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
package org.apache.tika.detect;

/**
 * Caches the raw encoding-detection probe (the leading bytes read for detection)
 * so that multiple detectors in a chain do not each re-read and re-tag-strip the
 * same bytes. For example a statistical detector and a downstream meta detector
 * that re-reads the bytes for arbitration can share one probe.
 * <p>
 * An instance is held by {@link EncodingDetectorContext}, so it inherits that
 * context's per-detection lifecycle: created fresh per detection and discarded
 * with the context immediately afterwards. That matters because a
 * {@link org.apache.tika.parser.ParseContext} flows on into recursive
 * (attachment/embedded) parsing — a probe must never outlive the single detection
 * it was read for.
 * <p>
 * Not thread-safe: a single detection runs its detectors sequentially on one
 * thread. The cache is keyed by the probe parameters — {@link #get} returns the
 * cached probe only when both {@code contentTarget} and {@code rawCap} match what
 * it was stored with, so a detector that wants a differently-sized probe
 * transparently reads (and caches) its own.
 * <p>
 * The cached array is shared read-only state; callers must not mutate it in place.
 */
public class EncodingProbeCache {

    private byte[] probe;
    private int contentTarget = -1;
    private int rawCap = -1;

    /**
     * @return the cached probe if one was stored with the same {@code contentTarget} and
     * {@code rawCap}; otherwise {@code null}
     */
    public byte[] get(int contentTarget, int rawCap) {
        if (probe != null && this.contentTarget == contentTarget && this.rawCap == rawCap) {
            return probe;
        }
        return null;
    }

    /**
     * Stores the probe bytes read with the given parameters.
     */
    public void put(byte[] probe, int contentTarget, int rawCap) {
        this.probe = probe;
        this.contentTarget = contentTarget;
        this.rawCap = rawCap;
    }
}
