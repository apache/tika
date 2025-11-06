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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.config.Field;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.pipesiterator.TotalCountResult;
import org.apache.tika.pipes.api.reporter.PipesReporter;
import org.apache.tika.plugins.PluginConfig;

public class CompositePipesReporter implements PipesReporter {

    private List<PipesReporter> pipesReporters = new ArrayList<>();

    @Override
    public void report(FetchEmitTuple t, PipesResult result, long elapsed) {
        for (PipesReporter reporter : pipesReporters) {
            reporter.report(t, result, elapsed);
        }

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
        for (PipesReporter reporter : pipesReporters) {
            reporter.error(t);
        }
    }

    @Override
    public void error(String msg) {
        for (PipesReporter reporter : pipesReporters) {
            reporter.error(msg);
        }
    }

    @Field
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
    public PluginConfig getPluginConfig() {
        return null;
    }
}
