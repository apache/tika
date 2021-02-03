package org.apache.tika.pipes.emitter;

import org.apache.tika.metadata.Metadata;

import java.util.List;

public class EmitData {

    private final EmitKey emitKey;
    private final List<Metadata> metadataList;

    public EmitData(EmitKey emitKey, List<Metadata> metadataList) {
        this.emitKey = emitKey;
        this.metadataList = metadataList;
    }

    public EmitKey getEmitKey() {
        return emitKey;
    }

    public List<Metadata> getMetadataList() {
        return metadataList;
    }

    @Override
    public String toString() {
        return "EmitData{" +
                "emitKey=" + emitKey +
                ", metadataList=" + metadataList +
                '}';
    }
}
