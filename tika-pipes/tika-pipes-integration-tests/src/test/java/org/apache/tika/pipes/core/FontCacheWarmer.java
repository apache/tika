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

import org.apache.pdfbox.pdmodel.font.FontMappers;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Builds PDFBox's on-disk font cache once, before any test in this module runs.
 * <p>
 * The first PDF parse in a JVM scans the system's fonts and writes {@code ~/.pdfbox.cache},
 * which takes seconds on a cold machine. In a forked pipes worker that happens inside a
 * parse, where it counts as "no progress" against {@code progressTimeoutMillis} -- so
 * whichever test happens to parse the first PDF fails on a slow host, for a reason that has
 * nothing to do with what it tests. A full reactor build warms the cache in the parser
 * modules' own tests; CI runs this module in a shard where those never run.
 * <p>
 * The driver and the forks resolve the same cache file (PDFBox tries {@code pdfbox.fontcache},
 * then {@code user.home}, then {@code java.io.tmpdir}), so warming it here covers the forks.
 * Auto-registered via {@code META-INF/services} plus
 * {@code junit.jupiter.extensions.autodetection.enabled}.
 */
public class FontCacheWarmer implements BeforeAllCallback {

    private static final AtomicBoolean WARMED = new AtomicBoolean();

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!WARMED.compareAndSet(false, true)) {
            return;
        }
        try {
            FontMappers.instance().getTrueTypeFont("Helvetica", null);
        } catch (Exception | LinkageError e) {
            // warming is an optimization: a failure here must not fail the build
        }
    }
}
