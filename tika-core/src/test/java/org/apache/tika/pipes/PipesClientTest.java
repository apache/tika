package org.apache.tika.pipes;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.fetcher.fs.FileSystemFetcher;

class PipesClientTest {
    String fetcherName = "fs";
    String testPdfFile = "testOverlappingText.pdf";

    private PipesClient pipesClient;
    @BeforeEach
    public void init()
            throws TikaConfigException, IOException, ParserConfigurationException, SAXException {
        Path tikaConfigPath = Paths.get("src", "test", "resources", "org", "apache", "tika",
                "pipes", "tika-sample-config.xml");
        PipesConfig pipesConfig = PipesConfig.load(tikaConfigPath);
        pipesClient = new PipesClient(pipesConfig);
    }

    @Test
    void process() throws IOException, InterruptedException {
        PipesResult pipesResult = pipesClient.process(new FetchEmitTuple(testPdfFile,
                new FetchKey(fetcherName,
                testPdfFile), new EmitKey(), FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
        Assertions.assertNotNull(pipesResult.getEmitData().getMetadataList());
        Assertions.assertEquals(1, pipesResult.getEmitData().getMetadataList().size());
        Metadata metadata = pipesResult.getEmitData().getMetadataList().get(0);
        Assertions.assertEquals("testOverlappingText.pdf", metadata.get("resourceName"));
    }
}