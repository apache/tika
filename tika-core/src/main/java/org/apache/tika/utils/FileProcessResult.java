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
package org.apache.tika.utils;

public class FileProcessResult {

    String stderr = "";
    String stdout = "";
    int exitValue = -1;
    long processTimeMillis = -1;
    boolean isTimeout = false;
    long stdoutLength = -1;
    long stderrLength = -1;
    boolean stderrTruncated = false;
    boolean stdoutTruncated = false;

    public String getStderr() {
        return stderr;
    }

    public String getStdout() {
        return stdout;
    }

    public int getExitValue() {
        return exitValue;
    }

    public long getProcessTimeMillis() {
        return processTimeMillis;
    }

    public boolean isTimeout() {
        return isTimeout;
    }

    public long getStdoutLength() {
        return stdoutLength;
    }

    public long getStderrLength() {
        return stderrLength;
    }

    public boolean isStderrTruncated() {
        return stderrTruncated;
    }

    public boolean isStdoutTruncated() {
        return stdoutTruncated;
    }

    public void setStderr(String stderr) {
        this.stderr = stderr;
    }

    public void setStdout(String stdout) {
        this.stdout = stdout;
    }

    public void setExitValue(int exitValue) {
        this.exitValue = exitValue;
    }

    public void setProcessTimeMillis(long processTimeMillis) {
        this.processTimeMillis = processTimeMillis;
    }

    public void setTimeout(boolean timeout) {
        isTimeout = timeout;
    }

    public void setStdoutLength(long stdoutLength) {
        this.stdoutLength = stdoutLength;
    }

    public void setStderrLength(long stderrLength) {
        this.stderrLength = stderrLength;
    }

    public void setStderrTruncated(boolean stderrTruncated) {
        this.stderrTruncated = stderrTruncated;
    }

    public void setStdoutTruncated(boolean stdoutTruncated) {
        this.stdoutTruncated = stdoutTruncated;
    }

    @Override
    public String toString() {
        return "FileProcessResult{"
                + "stderr='"
                + stderr
                + '\''
                + ", stdout='"
                + stdout
                + '\''
                + ", exitValue="
                + exitValue
                + ", processTimeMillis="
                + processTimeMillis
                + ", isTimeout="
                + isTimeout
                + ", stdoutLength="
                + stdoutLength
                + ", stderrLength="
                + stderrLength
                + ", stderrTruncated="
                + stderrTruncated
                + ", stdoutTruncated="
                + stdoutTruncated
                + '}';
    }
}
