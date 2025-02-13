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

package org.apache.tika.parser.gdal;

//JDK imports

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ProcessLoggerThread extends Thread {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessLoggerThread.class);
    private final InputStream inputStream;
    private final StringBuilder stringBuilder;

    public interface ProcessLogger {
        StringBuilder stdout();
        StringBuilder stderr();
    }

    private ProcessLoggerThread(InputStream inputStreamArg, StringBuilder stringBuilderArg) {
        this.inputStream    = inputStreamArg;
        this.stringBuilder  = stringBuilderArg;

        this.setName(this.getClass().getSimpleName());
        this.setDaemon(true);
    }

    public static ProcessLogger startFor(Process process) {
        var stdoutStringBuilder = new StringBuilder();
        new ProcessLoggerThread(process.getInputStream(), stdoutStringBuilder).start();

        var stderrStringBuilder = new StringBuilder();
        new ProcessLoggerThread(process.getErrorStream(), stderrStringBuilder).start();

        return new ProcessLogger() {
            @Override
            public StringBuilder stdout() {
                return stdoutStringBuilder;
            }

            @Override
            public StringBuilder stderr() {
                return stderrStringBuilder;
            }
        };
    }

    @Override
    public void run() {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(inputStream, UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
                stringBuilder.append("\n");
            }
        } catch (IOException exception) {
            LOG.error(exception.getMessage(), exception);
        } finally {
            try {
                reader.close();
            } catch (IOException exception) {
                LOG.error(exception.getMessage(), exception);
            }
        }
    }

}
