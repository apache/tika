package org.apache.tika.pipes.fetcher.fs.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

public class FileSystemFetcherConfigTest {

    @Test
    public void testBasic() throws Exception {
        String json = """
                {
                    "basePath":"/some/base/path",
                    "extractFileSystemMetadata":true
                }
                """;

        FileSystemFetcherConfig config = FileSystemFetcherConfig.load(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        assertEquals("/some/base/path", config.getBasePath());
        assertTrue(config.isExtractFileSystemMetadata());
    }
}
