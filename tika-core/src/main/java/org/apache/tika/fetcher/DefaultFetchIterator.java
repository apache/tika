package org.apache.tika.fetcher;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class DefaultFetchIterator extends FetchIterator implements Initializable {

    private final Map<String, FetchIterator> fetchIterators = new HashMap<>();
    private String iteratorName = "";
    public DefaultFetchIterator(List<FetchIterator> fetchIterators) {
        super("default");
        for (FetchIterator fetchIterator : fetchIterators) {
            if (this.fetchIterators.containsKey(fetchIterator.getName())) {
                throw new RuntimeException(new TikaConfigException("Multiple fetchIterators cannot have the same name: "
                        + fetchIterator.getName()));
            }
            this.fetchIterators.put(fetchIterator.getName(), fetchIterator);
        }
    }

    @Override
    protected void enqueue() throws IOException, TimeoutException {
        if (fetchIterators.size() == 0) {
            return;
        } else if (fetchIterators.size() == 1) {
            for (FetchIterator fetchIterator : fetchIterators.values()) {
                fetchIterator.enqueue();
            }
        }
    }

    @Field
    public void setIteratorName(String iteratorName) {
        this.iteratorName = iteratorName;
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        //no-op
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler) throws TikaConfigException {
        if (this.fetchIterators.size() > 1 && this.iteratorName == null) {
            throw new TikaConfigException("Must set desired iteratorName, if multiple iterators are defined");
        }
    }
}
