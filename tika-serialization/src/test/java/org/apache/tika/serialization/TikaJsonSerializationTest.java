package org.apache.tika.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.apache.tika.parser.ParseContext;
import org.apache.tika.serialization.mocks.ClassA;
import org.apache.tika.serialization.mocks.ClassB;
import org.apache.tika.serialization.mocks.ClassC;

public class TikaJsonSerializationTest {

    @Test
    public void testBasic() throws Exception {
        StringWriter sw = new StringWriter();
        ClassC classA = new ClassC();
        try(JsonGenerator jsonGenerator = new ObjectMapper().createGenerator(sw)) {
            TikaJsonSerializer.serialize(classA, jsonGenerator);
        }
        JsonNode root = new ObjectMapper().readTree(new StringReader(sw.toString()));
        Optional opt = TikaJsonDeserializer.deserializeObject(root);
        assertTrue(opt.isPresent());
        assertEquals(classA, opt.get());

    }

}
