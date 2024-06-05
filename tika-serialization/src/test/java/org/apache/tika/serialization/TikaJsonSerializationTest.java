package org.apache.tika.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        System.out.println(sw);
        JsonNode root = new ObjectMapper().readTree(new StringReader(sw.toString()));
        Optional opt = TikaJsonDeserializer.deserializeObject(root);
        System.out.println(opt.get().getClass());

    }

    @Test
    public void test() throws Exception {
        ArrayList<String> list = new ArrayList<>();
        boolean instance = List.class.isAssignableFrom(list.getClass());
        System.out.println("in: " + instance);
    }
}
