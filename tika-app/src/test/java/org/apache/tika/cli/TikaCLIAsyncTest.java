package org.apache.tika.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TikaCLIAsyncTest extends TikaCLITest {

    private static Path ASYNC_CONFIG;
    @TempDir
    private static Path ASYNC_OUTPUT_DIR;

    @BeforeAll
    public static void setUpClass() throws Exception {
        ASYNC_CONFIG = Files.createTempFile(ASYNC_OUTPUT_DIR, "async-config-", ".xml");
        String xml = "<properties>" + "<async>" + "<numClients>3</numClients>" +
                "<tikaConfig>" + ASYNC_CONFIG.toAbsolutePath() + "</tikaConfig>" +
                "</async>" + "<fetchers>" +
                "<fetcher class=\"org.apache.tika.pipes.fetcher.fs.FileSystemFetcher\">" +
                "<name>fsf</name>" + "<basePath>" + TEST_DATA_FILE.getAbsolutePath() +
                "</basePath>" + "</fetcher>" + "</fetchers>" + "<emitters>" +
                "<emitter class=\"org.apache.tika.pipes.emitter.fs.FileSystemEmitter\">" +
                "<name>fse</name>" + "<basePath>" + ASYNC_OUTPUT_DIR.toAbsolutePath() +
                "</basePath>" + "<prettyPrint>true</prettyPrint>" + "</emitter>" + "</emitters>" +
                "<pipesIterator class=\"org.apache.tika.pipes.pipesiterator.fs.FileSystemPipesIterator\">" +
                "<basePath>" + TEST_DATA_FILE.getAbsolutePath() + "</basePath>" +
                "<fetcherName>fsf</fetcherName>" + "<emitterName>fse</emitterName>" +
                "</pipesIterator>" + "</properties>";
        Files.write(ASYNC_CONFIG, xml.getBytes(UTF_8));
    }

    @Test
    public void testAsync() throws Exception {
        String content = getParamOutContent("-a", "--config=" + ASYNC_CONFIG.toAbsolutePath());

        int json = 0;
        for (File f : ASYNC_OUTPUT_DIR.toFile().listFiles()) {
            if (f.getName().endsWith(".json")) {
                //check one file for pretty print
                if (f.getName().equals("coffee.xls.json")) {
                    checkForPrettyPrint(f);
                }
                json++;
            }
        }
        assertEquals(17, json);
    }

    private void checkForPrettyPrint(File f) throws IOException {
        String json = FileUtils.readFileToString(f, UTF_8);
        int previous = json.indexOf("Content-Length");
        assertTrue(previous > -1);
        for (String k : new String[]{"Content-Type", "dc:creator",
                "dcterms:created", "dcterms:modified", "X-TIKA:content\""}) {
            int i = json.indexOf(k);
            assertTrue( i > -1, "should have found " + k);
            assertTrue(i > previous, "bad order: " + k + " at " + i + " not less than " + previous);
            previous = i;
        }
    }


}
