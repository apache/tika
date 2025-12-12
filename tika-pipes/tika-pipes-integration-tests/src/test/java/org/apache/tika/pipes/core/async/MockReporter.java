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
package org.apache.tika.pipes.core.async;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.pipesiterator.TotalCountResult;
import org.apache.tika.pipes.api.reporter.PipesReporter;
import org.apache.tika.plugins.ExtensionConfig;

//TODO -- figure out how to add this back in for the AsyncChaosMonkeyTest
public final class MockReporter implements PipesReporter {

    static ArrayBlockingQueue<PipesResult> RESULTS = new ArrayBlockingQueue<>(10000);

    private String endpoint;

    @Override
    public void report(FetchEmitTuple t, PipesResult result, long elapsed) {
        RESULTS.add(result);
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

    }

    @Override
    public void error(String msg) {

    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getEndpoint() {
        return endpoint;
    }

    @Override
    public String toString() {
        return "MockReporter{" + "endpoint='" + endpoint + '\'' + '}';
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public ExtensionConfig getExtensionConfig() {
        return null;
    }
}
