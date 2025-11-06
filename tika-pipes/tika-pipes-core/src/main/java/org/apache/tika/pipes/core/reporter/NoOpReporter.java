package org.apache.tika.pipes.core.reporter;

import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.reporter.PipesReporter;
import org.apache.tika.plugins.PluginConfig;

public class NoOpReporter extends PipesReporter {

    @Override
    public void report(FetchEmitTuple t, PipesResult result, long elapsed) {

    }

    @Override
    public void error(Throwable t) {

    }

    @Override
    public void error(String msg) {

    }

    @Override
    public PluginConfig getPluginConfig() {
        return null;
    }
}
