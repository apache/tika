package org.apache.tika.eval.app;

public class EvalConfig {

    long minExtractLength = 0;
    long maxExtractLength = 10_000_000;
    String jdbcString = null;
    int maxFilesToAdd = -1;
    int maxTokens = 200000;
    int maxContentLength = 5_000_000;
    int numThreads = 4;

}
