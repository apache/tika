package org.apache.tika.pipes.async;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.fetcher.Fetcher;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MockFetcher implements Fetcher {

    private static byte[] BYTES = new String("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"+
            "<mock>"+
            "<metadata action=\"add\" name=\"dc:creator\">Nikolai Lobachevsky</metadata>"+
            "<write element=\"p\">main_content</write>"+
        "</mock>").getBytes(StandardCharsets.UTF_8);

    @Override
    public String getName() {
        return "mock";
    }

    @Override
    public InputStream fetch(String fetchKey, Metadata metadata) throws TikaException, IOException {
        return new ByteArrayInputStream(BYTES);
    }
}
