package org.apache.tika.pipes.api.emitter;

import java.util.List;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

public interface EmitData {
    String getEmitKey();

    List<Metadata> getMetadataList();

    String getContainerStackTrace();

    long getEstimatedSizeBytes();

    ParseContext getParseContext();

}
