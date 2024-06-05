package org.apache.tika.metadata.serialization;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import org.apache.tika.metadata.Metadata;

public class MetadataSerializer extends JsonSerializer<Metadata> {
    private boolean prettyPrint = false;
    @Override
    public void serialize(Metadata metadata, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        //JsonMetadata.serializeMetadata(metadata, jsonGenerator, prettyPrint);
    }
}
