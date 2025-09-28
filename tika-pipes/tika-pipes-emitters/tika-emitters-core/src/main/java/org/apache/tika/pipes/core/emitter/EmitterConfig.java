package org.apache.tika.pipes.core.emitter;

import org.pf4j.ExtensionPoint;

public interface EmitterConfig extends ExtensionPoint {
    String getPluginId();
    EmitterConfig setPluginId(String pluginId);
    String getEmitterId();
    EmitterConfig setEmitterId(String fetcherId);
    String getConfigJson();
    EmitterConfig setConfigJson(String configJson);
}
