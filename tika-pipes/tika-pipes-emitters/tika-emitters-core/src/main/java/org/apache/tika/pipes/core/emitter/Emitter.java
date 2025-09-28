package org.apache.tika.pipes.core.emitter;

import java.io.IOException;
import java.util.List;

import org.pf4j.ExtensionPoint;

public interface Emitter extends ExtensionPoint {
    <T extends EmitterConfig> void init(T emitterConfig);
    String getPluginId();
    void emit(List<EmitOutput> emitOutputs) throws IOException;
}
