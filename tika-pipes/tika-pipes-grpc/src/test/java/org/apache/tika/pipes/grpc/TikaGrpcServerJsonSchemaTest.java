package org.apache.tika.pipes.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetAddress;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tika.GetFetcherConfigJsonSchemaReply;
import org.apache.tika.GetFetcherConfigJsonSchemaRequest;
import org.apache.tika.GetFetcherReply;
import org.apache.tika.GetFetcherRequest;
import org.apache.tika.SaveFetcherReply;
import org.apache.tika.SaveFetcherRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.pipes.TikaPipesIntegrationTestBase;
import org.apache.tika.pipes.fetcher.fs.config.FileSystemFetcherConfig;

class TikaGrpcServerJsonSchemaTest extends TikaPipesIntegrationTestBase {
    @Test
    void testJsonSchema() throws Exception {
        String fetcherId1 = "filesystem-fetcher-example1";
        String pluginId = "filesystem-fetcher";
        ManagedChannel channel =
                ManagedChannelBuilder.forAddress(InetAddress.getLocalHost().getHostAddress(),
                                port) // Ensure the port is correct
                        .usePlaintext().build();
        TikaGrpc.TikaBlockingStub tikaBlockingStub = TikaGrpc.newBlockingStub(channel);

        saveFetcher(tikaBlockingStub, fetcherId1, pluginId);

        GetFetcherConfigJsonSchemaRequest getFetcherConfigJsonSchemaRequest =
                GetFetcherConfigJsonSchemaRequest.newBuilder().setPluginId(pluginId).build();

        GetFetcherConfigJsonSchemaReply getFetcherConfigJsonSchemaReply = tikaBlockingStub.getFetcherConfigJsonSchema(getFetcherConfigJsonSchemaRequest);
        String jsonSchema = getFetcherConfigJsonSchemaReply.getFetcherConfigJsonSchema();

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode schemaNode = JsonLoader.fromString(jsonSchema);
        JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
        JsonSchema schema = factory.getJsonSchema(schemaNode);

        FileSystemFetcherConfig fileSystemFetcherConfig = new FileSystemFetcherConfig();
        fileSystemFetcherConfig.setExtractFileSystemMetadata(true);
        fileSystemFetcherConfig.setBasePath("target");
        JsonNode configNode = objectMapper.valueToTree(fileSystemFetcherConfig);

        ProcessingReport report = schema.validate(configNode);
        Assertions.assertTrue(report.isSuccess(), "JSON schema validation failed: " + report);
    }

    private void saveFetcher(TikaGrpc.TikaBlockingStub tikaBlockingStub, String fetcherId,
                             String pluginId) throws JsonProcessingException {
        FileSystemFetcherConfig fileSystemFetcherConfig = new FileSystemFetcherConfig();
        fileSystemFetcherConfig.setExtractFileSystemMetadata(true);
        fileSystemFetcherConfig.setBasePath("target");

        SaveFetcherReply saveFetcherReply = tikaBlockingStub.saveFetcher(
                SaveFetcherRequest.newBuilder().setFetcherId(fetcherId).setPluginId(pluginId)
                        .setFetcherConfigJson(
                                objectMapper.writeValueAsString(fileSystemFetcherConfig)).build());
        assertEquals(fetcherId, saveFetcherReply.getFetcherId());
        GetFetcherReply getFetcherReply = tikaBlockingStub.getFetcher(
                GetFetcherRequest.newBuilder().setFetcherId(fetcherId).build());
        assertEquals(fetcherId, getFetcherReply.getFetcherId());
    }
}
