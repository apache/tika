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
package org.apache.tika.parser.xmp;

import org.apache.tika.utils.TikaDates;

/**
 * XMP date string -&gt; canonical ISO-8601 UTC (seconds), or null if not a full-precision date.
 * Partial dates (YYYY, YYYY-MM) return null so callers keep the raw value and never promote it.
 */
public final class XmpDates {

    private XmpDates() {
    }

    public static String normalize(String raw) {
        return TikaDates.normalize(raw);
    }
}
