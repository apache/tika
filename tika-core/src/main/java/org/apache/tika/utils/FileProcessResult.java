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
                '}';
    }
}