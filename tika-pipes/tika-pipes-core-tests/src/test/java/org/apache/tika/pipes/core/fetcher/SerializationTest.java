package org.apache.tika.pipes.core.fetcher;

import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.core.PipesReporter;
import org.apache.tika.pipes.core.async.AsyncConfig;
import org.apache.tika.pipes.core.async.MockReporter;
import org.apache.tika.pipes.fetcher.fs.FileSystemFetcher;

public class SerializationTest {

    @Test
    public void testOne() throws Exception {
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator
                .builder()
                .allowIfSubType(PipesReporter.class)
                .allowIfSubType(List.class)
                .allowIfSubType(Path.class)
                .allowIfSubType(Fetcher.class)
                //.allowIfSubType("org.tallison")
//                .allowIfSubType(List.class)// or allow subclasses of this type
                .build();
        ObjectMapper mapper = new ObjectMapper();
        mapper.activateDefaultTyping(ptv,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        MockReporter mockReporter = new MockReporter();
        mockReporter.setEndpoint("my end point");
        Fetcher fetcher = new FileSystemFetcher();
        String json = mapper.writeValueAsString(fetcher);
        System.out.println(json);

        Fetcher rebuilt = mapper.readValue(json, Fetcher.class);
        System.out.println(rebuilt);
    }


}
