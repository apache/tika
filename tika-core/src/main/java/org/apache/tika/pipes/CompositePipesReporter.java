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
package org.apache.tika.pipes;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.pipesiterator.TotalCountResult;

public class CompositePipesReporter extends PipesReporter implements Initializable {

    private List<PipesReporter> pipesReporters;

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
    public void setPipesReporters(List<PipesReporter> pipesReporters) {
        this.pipesReporters = pipesReporters;
    }


    public List<PipesReporter> getPipesReporters() {
        return pipesReporters;
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        //no-op
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        if (pipesReporters == null) {
            throw new TikaConfigException("must specify 'pipesReporters'");
        }
        if (pipesReporters.size() == 0) {
            throw new TikaConfigException("must specify at least one pipes reporter");
        }
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
}
