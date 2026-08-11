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
    long requestedTimeoutMillis = -1;
    long grantedTimeoutMillis = -1;

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

    /**
     * @return the timeout the caller's own configuration requested, or {@code -1} if
     * this result was not produced by a context-aware {@code ProcessUtils.execute} call
     */
    public long getRequestedTimeoutMillis() {
        return requestedTimeoutMillis;
    }

    public void setRequestedTimeoutMillis(long requestedTimeoutMillis) {
        this.requestedTimeoutMillis = requestedTimeoutMillis;
    }

    /**
     * @return the budget actually granted (after {@code ParseTimeout.budgetFor}
     * clipping), or {@code -1} if this result was not produced by a context-aware
     * {@code ProcessUtils.execute} call
     */
    public long getGrantedTimeoutMillis() {
        return grantedTimeoutMillis;
    }

    public void setGrantedTimeoutMillis(long grantedTimeoutMillis) {
        this.grantedTimeoutMillis = grantedTimeoutMillis;
    }

    /**
     * @return true if the granted budget was clipped below the requested timeout by the
     * task's remaining time (task's total timeout was binding, not the process's own).
     * False if not clipped, or if no requested/granted info is present.
     */
    public boolean isClippedByRemaining() {
        return requestedTimeoutMillis >= 0 && grantedTimeoutMillis >= 0
                && grantedTimeoutMillis < requestedTimeoutMillis;
    }

    @Override
    public String toString() {
        return "FileProcessResult{" +
                "stderr='" + stderr + '\'' +
                ", stdout='" + stdout + '\'' +
                ", exitValue=" + exitValue +
                ", processTimeMillis=" + processTimeMillis +
                ", isTimeout=" + isTimeout +
                ", stdoutLength=" + stdoutLength +
                ", stderrLength=" + stderrLength +
                ", stderrTruncated=" + stderrTruncated +
                ", stdoutTruncated=" + stdoutTruncated +
                ", requestedTimeoutMillis=" + requestedTimeoutMillis +
                ", grantedTimeoutMillis=" + grantedTimeoutMillis +
                '}';
    }
}
