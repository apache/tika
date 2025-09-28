package org.apache.tika.pipes.core.iterators;

import java.util.List;

import org.pf4j.ExtensionPoint;

public interface PipeIterator extends ExtensionPoint, AutoCloseable {
    String getPipeIteratorId();
    <T extends PipeIteratorConfig> void init(T config);
    boolean hasNext();
    List<PipeInput> next();
}
