package org.apache.tika.metadata.serialization;

import java.io.IOException;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import org.apache.tika.parser.ParseContext;

public class ParseContextDeserializer extends JsonDeserializer<ParseContext> {
    @Override
    public ParseContext deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
        ParseContext parseContext = new ParseContext();
        String className = jsonParser.nextFieldName();
        Class clazz = null;
        try {
            clazz = Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        Object object = jsonParser.readValueAs(clazz);
        parseContext.set(clazz, object);
        return parseContext;
    }
}
