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
package org.apache.tika.parser.external;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.parser.Parser;

/**
 * Configuration for {@link ExternalParser}.
 * <p>
 * This config is immutable after construction. ExternalParser does NOT
 * support runtime configuration changes via ParseContext for security reasons.
 */
public class ExternalParserConfig implements Serializable {

    private static final long serialVersionUID = 2L;

    private List<String> supportedTypes = new ArrayList<>();
    private List<String> commandLine = new ArrayList<>();
    private Parser stdoutHandler;
    private Parser stderrHandler;
    private Parser outputFileHandler;
    private String contentSource;
    private List<String> checkCommandLine;
    private List<Integer> checkErrorCodes;
    private boolean returnStdout = false;
    private boolean returnStderr = true;
    private long timeoutMs = ExternalParser.DEFAULT_TIMEOUT_MS;
    private int maxStdErr = 10000;
    private int maxStdOut = 10000;

    public ExternalParserConfig() {
    }

    public List<String> getSupportedTypes() {
        return supportedTypes;
    }

    public void setSupportedTypes(List<String> supportedTypes) {
        this.supportedTypes = supportedTypes;
    }

    public List<String> getCommandLine() {
        return commandLine;
    }

    public void setCommandLine(List<String> commandLine) {
        this.commandLine = commandLine;
    }

    public Parser getStdoutHandler() {
        return stdoutHandler;
    }

    public void setStdoutHandler(Parser stdoutHandler) {
        this.stdoutHandler = stdoutHandler;
    }

    public Parser getStderrHandler() {
        return stderrHandler;
    }

    public void setStderrHandler(Parser stderrHandler) {
        this.stderrHandler = stderrHandler;
    }

    public Parser getOutputFileHandler() {
        return outputFileHandler;
    }

    public void setOutputFileHandler(Parser outputFileHandler) {
        this.outputFileHandler = outputFileHandler;
    }

    /**
     * Which stream provides the XHTML content output.
     * <p>
     * Valid values: {@code "stdout"}, {@code "stderr"}, {@code "outputFile"}, {@code "none"}.
     * <p>
     * If {@code null}, defaults to {@code "stdout"} when no {@code ${OUTPUT_FILE}} token
     * is in the command, or {@code "outputFile"} when it is.
     */
    public String getContentSource() {
        return contentSource;
    }

    public void setContentSource(String contentSource) {
        this.contentSource = contentSource;
    }

    public List<String> getCheckCommandLine() {
        return checkCommandLine;
    }

    public void setCheckCommandLine(List<String> checkCommandLine) {
        this.checkCommandLine = checkCommandLine;
    }

    public List<Integer> getCheckErrorCodes() {
        return checkErrorCodes;
    }

    public void setCheckErrorCodes(List<Integer> checkErrorCodes) {
        this.checkErrorCodes = checkErrorCodes;
    }

    public boolean isReturnStdout() {
        return returnStdout;
    }

    public void setReturnStdout(boolean returnStdout) {
        this.returnStdout = returnStdout;
    }

    public boolean isReturnStderr() {
        return returnStderr;
    }

    public void setReturnStderr(boolean returnStderr) {
        this.returnStderr = returnStderr;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getMaxStdErr() {
        return maxStdErr;
    }

    public void setMaxStdErr(int maxStdErr) {
        this.maxStdErr = maxStdErr;
    }

    public int getMaxStdOut() {
        return maxStdOut;
    }

    public void setMaxStdOut(int maxStdOut) {
        this.maxStdOut = maxStdOut;
    }
}
