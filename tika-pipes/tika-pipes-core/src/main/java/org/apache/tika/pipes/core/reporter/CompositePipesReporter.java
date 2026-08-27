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

import java.io.IOException;
import java.util.List;

import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.pipesiterator.TotalCountResult;
import org.apache.tika.pipes.api.reporter.PipesReporter;
import org.apache.tika.plugins.ExtensionConfig;

public class CompositePipesReporter implements PipesReporter {

    private final List<PipesReporter> pipesReporters;

    public CompositePipesReporter(List<PipesReporter> pipesReporterList) {
        pipesReporters = pipesReporterList;
    }

    /**
     * Every reporter sees every call, even if an earlier one throws; the first
     * exception is rethrown after the loop with the rest suppressed.
     */
    @Override
    public void report(FetchEmitTuple t, PipesResult result, long elapsed) {
        RuntimeException first = null;
        for (PipesReporter reporter : pipesReporters) {
            try {
                reporter.report(t, result, elapsed);
            } catch (RuntimeException e) {
                first = collect(first, e);
            }
        }
        if (first != null) {
            throw first;
        }
    }

    private static RuntimeException collect(RuntimeException first, RuntimeException e) {
        if (first == null) {
            return e;
        }
        first.addSuppressed(e);
        return first;
    }

    @Override
    public void report(TotalCountResult totalCountResult) {
        for (PipesReporter reporter : pipesReporters) {
            reporter.report(totalCountResult);
        }
    }

    @Override
    public boolean supportsTotalCount() {
        for (PipesReporter reporter : pipesReporters) {
            if (reporter.supportsTotalCount()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void error(Throwable t) {
        RuntimeException first = null;
        for (PipesReporter reporter : pipesReporters) {
            try {
                reporter.error(t);
            } catch (RuntimeException e) {
                first = collect(first, e);
            }
        }
        if (first != null) {
            throw first;
        }
    }

    @Override
    public void error(String msg) {
        RuntimeException first = null;
        for (PipesReporter reporter : pipesReporters) {
            try {
                reporter.error(msg);
            } catch (RuntimeException e) {
                first = collect(first, e);
            }
        }
        if (first != null) {
            throw first;
        }
    }

    public void addPipesReporter(PipesReporter pipesReporter) {
        this.pipesReporters.add(pipesReporter);
    }

    public List<PipesReporter> getPipesReporters() {
        return pipesReporters;
    }


    /**
     * Tries to close all resources.  Throws the last encountered IOException
     * if any are thrown by the component reporters.
     *
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        IOException ex = null;
        for (PipesReporter pipesReporter : pipesReporters) {
            try {
                pipesReporter.close();
            } catch (IOException e) {
                ex = e;
            }
        }
        if (ex != null) {
            throw ex;
        }
    }

    @Override
    public ExtensionConfig getExtensionConfig() {
        return null;
    }
}
