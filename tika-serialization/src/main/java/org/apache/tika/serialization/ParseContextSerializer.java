package org.apache.tika.serialization;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import org.apache.tika.parser.ParseContext;

public class ParseContextSerializer extends JsonSerializer<ParseContext> {


    @Override
    public void serialize(ParseContext parseContext, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeStartObject("parseContext");
        for (String className : parseContext.keySet()) {
            try {
                Class clazz = Class.forName(className);
                TikaJsonSerializer.serialize(className, parseContext.get(clazz), clazz, jsonGenerator);
            } catch (TikaSerializationException e) {
                throw new IOException(e);
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException(e);
            }
        }
        jsonGenerator.writeEndObject();
    }
}
