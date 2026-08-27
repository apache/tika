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
package org.apache.tika.pipes.core.reporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.api.pipesiterator.TotalCountResult;
import org.apache.tika.pipes.api.reporter.PipesReporter;
import org.apache.tika.plugins.ExtensionConfig;

public class CompositePipesReporterTest {

    private static class Recording implements PipesReporter {
        final List<String> seen = new ArrayList<>();
        final boolean throwing;

        Recording(boolean throwing) {
            this.throwing = throwing;
        }

        @Override
        public void report(FetchEmitTuple t, PipesResult result, long elapsed) {
            seen.add(t.getId());
            if (throwing) {
                throw new IllegalStateException("boom " + t.getId());
            }
        }

        @Override
        public void report(TotalCountResult totalCountResult) {
        }

        @Override
        public boolean supportsTotalCount() {
            return false;
        }

        @Override
        public void error(Throwable t) {
            seen.add("error");
            if (throwing) {
                throw new IllegalStateException("boom error");
            }
        }

        @Override
        public void error(String msg) {
            error(new RuntimeException(msg));
        }

        @Override
        public void close() {
        }

        @Override
        public ExtensionConfig getExtensionConfig() {
            return null;
        }
    }

    @Test
    public void testThrowingReporterDoesNotStarveSiblings() {
        Recording first = new Recording(true);
        Recording second = new Recording(false);
        CompositePipesReporter composite = new CompositePipesReporter(List.of(first, second));
        FetchEmitTuple t = new FetchEmitTuple("a", new FetchKey("f", "a"), new EmitKey("e", "a"));
        PipesResult result = new PipesResult(PipesResult.RESULT_STATUS.OOM);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> composite.report(t, result, 1));
        assertEquals("boom a", e.getMessage());
        assertEquals(List.of("a"), second.seen);

        assertThrows(IllegalStateException.class, () -> composite.error("dead"));
        assertEquals(List.of("a", "error"), second.seen);
    }
}
