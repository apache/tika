package org.apache.tika.pipes.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetAddress;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tika.DeleteFetcherReply;
import org.apache.tika.DeleteFetcherRequest;
import org.apache.tika.GetFetcherReply;
import org.apache.tika.GetFetcherRequest;
import org.apache.tika.ListFetchersReply;
import org.apache.tika.ListFetchersRequest;
import org.apache.tika.SaveFetcherReply;
import org.apache.tika.SaveFetcherRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.pipes.TikaPipesIntegrationTestBase;
import org.apache.tika.pipes.fetcher.fs.config.FileSystemFetcherConfig;

class TikaGrpcServerFetchersCrudTest extends TikaPipesIntegrationTestBase {
    @Test
    void fetchersCrud() throws Exception {
        String fetcherId1 = "filesystem-fetcher-example1";
        String fetcherId2 = "filesystem-fetcher-example2";
        String pluginId = "filesystem-fetcher";
        ManagedChannel channel =
                ManagedChannelBuilder.forAddress(InetAddress.getLocalHost().getHostAddress(),
                                port) // Ensure the port is correct
                        .usePlaintext().build();
        TikaGrpc.TikaBlockingStub tikaBlockingStub = TikaGrpc.newBlockingStub(channel);

        saveFetcher(tikaBlockingStub, fetcherId1, pluginId);
        saveFetcher(tikaBlockingStub, fetcherId2, pluginId);

        ListFetchersReply listFetchersReply =
                tikaBlockingStub.listFetchers(ListFetchersRequest.newBuilder().build());
        Assertions.assertTrue(listFetchersReply.getGetFetcherRepliesCount() > 0);

        DeleteFetcherReply deleteFetcherReply = tikaBlockingStub.deleteFetcher(
                DeleteFetcherRequest.newBuilder().setFetcherId(fetcherId1).build());
        Assertions.assertTrue(deleteFetcherReply.getSuccess());

        deleteFetcherReply = tikaBlockingStub.deleteFetcher(
                DeleteFetcherRequest.newBuilder().setFetcherId(fetcherId2).build());
        Assertions.assertTrue(deleteFetcherReply.getSuccess());

        listFetchersReply = tikaBlockingStub.listFetchers(ListFetchersRequest.newBuilder().build());
        Assertions.assertEquals(0, listFetchersReply.getGetFetcherRepliesCount(),
                "Not supposed to have any fetchers but found " +
                        listFetchersReply.getGetFetcherRepliesList());

        deleteFetcherReply = tikaBlockingStub.deleteFetcher(
                DeleteFetcherRequest.newBuilder().setFetcherId("asdfasdfasdfas").build());
        Assertions.assertFalse(deleteFetcherReply.getSuccess());
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
