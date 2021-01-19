package org.apache.tika.emitter;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public interface Emitter {

    Set<String> getSupported();

    //TODO: do we need a key or can we pass that in metadatalist?
    //If we do need it, how do we populate it?
    void emit(String emitterName, List<Metadata> metadataList) throws IOException, TikaException;
    //TODO we can add this later?
    //void emit(String txt, Metadata metadata) throws IOException, TikaException;

}
