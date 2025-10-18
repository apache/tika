package org.apache.tika.pipes.core.async;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.core.PipesReporter;

public class AsyncConfigTest {

    @Test
    public void testOne() throws Exception {
        AsyncConfig asyncConfig = AsyncConfig.load(
                Paths.get(AsyncConfigTest.class.getResource("/configs/TIKA-3865-params.xml").toURI()));

        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator
                .builder()
                .allowIfSubType(PipesReporter.class)
                .allowIfSubType(List.class)
                .allowIfSubType(Path.class)
                .allowIfSubType(Fetcher.class).allowIfSubType(AsyncConfig.class)
                //.allowIfSubType("org.tallison")
//                .allowIfSubType(List.class)// or allow subclasses of this type
                .build();
        ObjectMapper mapper = new ObjectMapper();
        mapper.activateDefaultTyping(ptv,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        MockReporter mockReporter = new MockReporter();
        mockReporter.setEndpoint("my end point");
        Wrapper wrapper = new Wrapper();
        wrapper.setReporter(mockReporter);

        String json = mapper.writeValueAsString(asyncConfig);
        System.out.println(json);

        AsyncConfig rebuilt = mapper.readValue(json, AsyncConfig.class);
        System.out.println(rebuilt);
    }

    static class Wrapper {
        private PipesReporter reporter;
        public Wrapper() {

        }
        public PipesReporter getReporter() {
            return reporter;
        }

        public void setReporter(PipesReporter reporter) {
            this.reporter = reporter;
        }
    }
}
