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


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple PipesReporter that logs everything at the debug level.
 */
public class LoggingPipesReporter extends PipesReporter {
    Logger LOGGER = LoggerFactory.getLogger(LoggingPipesReporter.class);

    @Override
    public void report(FetchEmitTuple t, PipesResult result, long elapsed) {
        LOGGER.debug("{} {} {}", t, result, elapsed);
    }

    @Override
    public void error(Throwable t) {
        LOGGER.error("pipes error", t);
    }

    @Override
    public void error(String msg) {
        LOGGER.error("error {}", msg);
    }
}
