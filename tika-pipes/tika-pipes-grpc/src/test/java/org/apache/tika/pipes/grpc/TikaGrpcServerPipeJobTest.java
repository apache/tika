package org.apache.tika.pipes.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pf4j.util.FileUtils;

import org.apache.tika.GetEmitterReply;
import org.apache.tika.GetEmitterRequest;
import org.apache.tika.GetFetcherReply;
import org.apache.tika.GetFetcherRequest;
import org.apache.tika.GetPipeIteratorReply;
import org.apache.tika.GetPipeIteratorRequest;
import org.apache.tika.GetPipeJobReply;
import org.apache.tika.GetPipeJobRequest;
import org.apache.tika.RunPipeJobReply;
import org.apache.tika.RunPipeJobRequest;
import org.apache.tika.SaveEmitterReply;
import org.apache.tika.SaveEmitterRequest;
import org.apache.tika.SaveFetcherReply;
import org.apache.tika.SaveFetcherRequest;
import org.apache.tika.SavePipeIteratorReply;
import org.apache.tika.SavePipeIteratorRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.pipes.TikaPipesIntegrationTestBase;
import org.apache.tika.pipes.fetchers.filesystem.CsvPipeIteratorConfig;
import org.apache.tika.pipes.fetchers.filesystem.FileSystemEmitterConfig;
import org.apache.tika.pipes.fetchers.filesystem.FileSystemFetcherConfig;

@Slf4j
class TikaGrpcServerPipeJobTest extends TikaPipesIntegrationTestBase {
    ObjectMapper objectMapper = new ObjectMapper();
    String pipeIteratorId = "pipe-iterator-example1";
    String pipeIteratorPluginId = "csv-pipe-iterator";
    String emitterId = "emitter-example1";
    String emitterPluginId = "filesystem-emitter";
    String fetcherPluginId = "filesystem-fetcher";
    String fetcherId = "filesystem-fetcher-example1";
    File testFilesDir = new File("corpa-files");
    String testFilesDirPath;
    Path pipeIteratorCsvFile;
    Path fileEmitterOutputDir;

    @BeforeEach
    void init() throws Exception {
        testFilesDirPath = testFilesDir.getCanonicalPath();
        log.info("Using test files from {}", testFilesDirPath);
        List<String> files = new ArrayList<>();
        for (File f : Objects.requireNonNull(testFilesDir.listFiles())) {
            files.add(f.getCanonicalPath());
        }
        pipeIteratorCsvFile = Paths.get("target", UUID.randomUUID() + "-corpa-files.csv");
        FileUtils.writeLines(files, pipeIteratorCsvFile);
        fileEmitterOutputDir = Paths.get("target", UUID.randomUUID().toString());
        Files.createDirectories(fileEmitterOutputDir);
    }

    @Test
    void runPipeJob() throws Exception {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(InetAddress.getLocalHost().getHostAddress(), port) // Ensure the port is correct
                .usePlaintext().build();
        TikaGrpc.TikaBlockingStub tikaBlockingStub = TikaGrpc.newBlockingStub(channel);

        saveFetcher(tikaBlockingStub, fetcherId, fetcherPluginId);
        newCsvPipeIterator(tikaBlockingStub, pipeIteratorId, pipeIteratorPluginId, pipeIteratorCsvFile);
        newFileSystemEmitter(tikaBlockingStub, emitterId, emitterPluginId, fileEmitterOutputDir);

        RunPipeJobReply runPipeJobReply = tikaBlockingStub.runPipeJob(RunPipeJobRequest.newBuilder()
                .setFetcherId(fetcherId)
                .setPipeIteratorId(pipeIteratorId)
                .setEmitterId(emitterId)
                .build());

        String pipeJobId = runPipeJobReply.getPipeJobId();
        Assertions.assertNotNull(pipeJobId);
        log.info("Started pipe job: {}", pipeJobId);

        Awaitility.await().atMost(3, TimeUnit.MINUTES)
                .untilAsserted(() -> {
            GetPipeJobReply getPipeJobReply = tikaBlockingStub.getPipeJob(GetPipeJobRequest.newBuilder().setPipeJobId(pipeJobId).build());
            if (getPipeJobReply.getHasError()) {
                Assertions.fail("Pipe job encountered an error");
            }
            Assertions.assertTrue(getPipeJobReply.getIsCompleted(), "Pipe job is not yet completed");
        });

        // Assert that there is a matching JSON file in the output folder for every file in the input folder
        Files.walk(testFilesDir.toPath())
                .filter(Files::isRegularFile)
                .forEach(inputFile -> {
                    Path outputFile = fileEmitterOutputDir.resolve(inputFile.getFileName().toString() + ".json");
                    Assertions.assertTrue(Files.exists(outputFile), "Output file not found for input file: " + inputFile);
                });
    }

    private void saveFetcher(TikaGrpc.TikaBlockingStub tikaBlockingStub, String fetcherId, String pluginId) throws JsonProcessingException {
        FileSystemFetcherConfig fileSystemFetcherConfig = new FileSystemFetcherConfig();
        fileSystemFetcherConfig.setExtractFileSystemMetadata(true);
        fileSystemFetcherConfig.setBasePath(testFilesDir.getAbsolutePath());

        SaveFetcherReply saveFetcherReply = tikaBlockingStub.saveFetcher(
                SaveFetcherRequest.newBuilder().setFetcherId(fetcherId).setPluginId(pluginId).setFetcherConfigJson(objectMapper.writeValueAsString(fileSystemFetcherConfig)).build());
        assertEquals(fetcherId, saveFetcherReply.getFetcherId());
        GetFetcherReply getFetcherReply = tikaBlockingStub.getFetcher(GetFetcherRequest.newBuilder().setFetcherId(fetcherId).build());
        assertEquals(fetcherId, getFetcherReply.getFetcherId());
    }

    private void newCsvPipeIterator(TikaGrpc.TikaBlockingStub tikaBlockingStub, String pipeIteratorId, String pluginId, Path csvPath) throws JsonProcessingException {
        CsvPipeIteratorConfig pipeIteratorConfig = new CsvPipeIteratorConfig();
        pipeIteratorConfig.setPipeIteratorId(pipeIteratorId);
        pipeIteratorConfig.setPluginId(pluginId);
        pipeIteratorConfig.setCsvPath(csvPath.toAbsolutePath().toString());

        SavePipeIteratorReply savePipeIteratorReply = tikaBlockingStub.savePipeIterator(
                SavePipeIteratorRequest.newBuilder()
                        .setPipeIteratorId(pipeIteratorId)
                        .setPluginId(pluginId)
                        .setPipeIteratorConfigJson(objectMapper.writeValueAsString(pipeIteratorConfig))
                        .build());
        assertEquals(pipeIteratorId, savePipeIteratorReply.getPipeIteratorId());
        GetPipeIteratorReply getPipeIteratorReply = tikaBlockingStub.getPipeIterator(GetPipeIteratorRequest.newBuilder().setPipeIteratorId(pipeIteratorId).build());
        assertEquals(pipeIteratorId, getPipeIteratorReply.getPipeIteratorId());
    }

    private void newFileSystemEmitter(TikaGrpc.TikaBlockingStub tikaBlockingStub, String emitterId, String pluginId, Path outputDir) throws JsonProcessingException {
        FileSystemEmitterConfig emitterConfig = new FileSystemEmitterConfig();
        emitterConfig.setEmitterId(emitterId);
        emitterConfig.setPluginId(pluginId);
        emitterConfig.setOutputDir(outputDir.toAbsolutePath().toString());

        SaveEmitterReply saveEmitterReply = tikaBlockingStub.saveEmitter(
                SaveEmitterRequest.newBuilder().setEmitterId(emitterId).setPluginId(pluginId).setEmitterConfigJson(objectMapper.writeValueAsString(emitterConfig)).build());
        assertEquals(emitterId, saveEmitterReply.getEmitterId());
        GetEmitterReply getEmitterReply = tikaBlockingStub.getEmitter(GetEmitterRequest.newBuilder().setEmitterId(emitterId).build());
        assertEquals(emitterId, getEmitterReply.getEmitterId());
    }
}
