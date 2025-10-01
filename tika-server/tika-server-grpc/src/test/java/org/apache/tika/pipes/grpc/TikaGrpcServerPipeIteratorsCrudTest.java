package org.apache.tika.pipes.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetAddress;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tika.DeletePipeIteratorReply;
import org.apache.tika.DeletePipeIteratorRequest;
import org.apache.tika.GetPipeIteratorReply;
import org.apache.tika.GetPipeIteratorRequest;
import org.apache.tika.ListPipeIteratorsReply;
import org.apache.tika.ListPipeIteratorsRequest;
import org.apache.tika.SavePipeIteratorReply;
import org.apache.tika.SavePipeIteratorRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.pipes.TikaPipesIntegrationTestBase;
import org.apache.tika.pipes.core.iterators.DefaultPipeIteratorConfig;

class TikaGrpcServerPipeIteratorsCrudTest extends TikaPipesIntegrationTestBase {
    @Test
    void pipeIteratorsCrud() throws Exception {
        String pipeIteratorId1 = "pipe-iterator-example1";
        String pipeIteratorId2 = "pipe-iterator-example2";
        String pluginId = "csv-pipe-iterator";
        ManagedChannel channel =
                ManagedChannelBuilder.forAddress(InetAddress.getLocalHost().getHostAddress(), port)
                        .usePlaintext().build();
        TikaGrpc.TikaBlockingStub tikaBlockingStub = TikaGrpc.newBlockingStub(channel);

        savePipeIterator(tikaBlockingStub, pipeIteratorId1, pluginId);
        savePipeIterator(tikaBlockingStub, pipeIteratorId2, pluginId);

        ListPipeIteratorsReply listPipeIteratorsReply =
                tikaBlockingStub.listPipeIterators(ListPipeIteratorsRequest.newBuilder().build());
        Assertions.assertTrue(listPipeIteratorsReply.getGetPipeIteratorRepliesCount() > 0);

        DeletePipeIteratorReply deletePipeIteratorReply = tikaBlockingStub.deletePipeIterator(
                DeletePipeIteratorRequest.newBuilder().setPipeIteratorId(pipeIteratorId1).build());
        Assertions.assertTrue(deletePipeIteratorReply.getSuccess());

        deletePipeIteratorReply = tikaBlockingStub.deletePipeIterator(
                DeletePipeIteratorRequest.newBuilder().setPipeIteratorId(pipeIteratorId2).build());
        Assertions.assertTrue(deletePipeIteratorReply.getSuccess());

        listPipeIteratorsReply =
                tikaBlockingStub.listPipeIterators(ListPipeIteratorsRequest.newBuilder().build());
        Assertions.assertEquals(0, listPipeIteratorsReply.getGetPipeIteratorRepliesCount(),
                "Not supposed to have any pipe iterators but found " +
                        listPipeIteratorsReply.getGetPipeIteratorRepliesList());

        deletePipeIteratorReply = tikaBlockingStub.deletePipeIterator(
                DeletePipeIteratorRequest.newBuilder()
                        .setPipeIteratorId("nonexistent-pipe-iterator").build());
        Assertions.assertFalse(deletePipeIteratorReply.getSuccess());
    }

    private void savePipeIterator(TikaGrpc.TikaBlockingStub tikaBlockingStub, String pipeIteratorId,
                                  String pluginId) throws JsonProcessingException {
        DefaultPipeIteratorConfig pipeIteratorConfig = new DefaultPipeIteratorConfig();
        pipeIteratorConfig.setPipeIteratorId(pipeIteratorId);
        pipeIteratorConfig.setPluginId(pluginId);
        pipeIteratorConfig.setConfigJson(new ObjectMapper().writeValueAsString(Map.of("key", "value")));

        SavePipeIteratorReply savePipeIteratorReply = tikaBlockingStub.savePipeIterator(
                SavePipeIteratorRequest.newBuilder().setPipeIteratorId(pipeIteratorId)
                        .setPluginId(pluginId).setPipeIteratorConfigJson(
                                objectMapper.writeValueAsString(pipeIteratorConfig)).build());
        assertEquals(pipeIteratorId, savePipeIteratorReply.getPipeIteratorId());
        GetPipeIteratorReply getPipeIteratorReply = tikaBlockingStub.getPipeIterator(
                GetPipeIteratorRequest.newBuilder().setPipeIteratorId(pipeIteratorId).build());
        assertEquals(pipeIteratorId, getPipeIteratorReply.getPipeIteratorId());
    }
}
