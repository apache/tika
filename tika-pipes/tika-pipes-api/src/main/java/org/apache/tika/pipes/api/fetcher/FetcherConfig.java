package org.apache.tika.pipes.api.fetcher;

public abstract class FetcherConfig {

    public boolean allowRuntimeModifications() {
        return false;
    }
}
