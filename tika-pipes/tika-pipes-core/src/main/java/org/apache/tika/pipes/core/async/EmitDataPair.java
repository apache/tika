package org.apache.tika.pipes.core.async;

import org.apache.tika.pipes.api.emitter.EmitData;

public record EmitDataPair(String emitterPluginId, EmitData emitData) {
}
