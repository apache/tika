package org.apache.tika.pipes.fetcher.fs.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        FileSystemFetcherConfig config = FileSystemFetcherConfig.load(json);
        assertEquals("/some/base/path", config.getBasePath());
        assertTrue(config.isExtractFileSystemMetadata());
    }
}
