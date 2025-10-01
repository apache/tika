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

import org.apache.tika.DeleteEmitterReply;
import org.apache.tika.DeleteEmitterRequest;
import org.apache.tika.GetEmitterReply;
import org.apache.tika.GetEmitterRequest;
import org.apache.tika.ListEmittersReply;
import org.apache.tika.ListEmittersRequest;
import org.apache.tika.SaveEmitterReply;
import org.apache.tika.SaveEmitterRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.pipes.TikaPipesIntegrationTestBase;
import org.apache.tika.pipes.core.emitter.DefaultEmitterConfig;

class TikaGrpcServerEmittersCrudTest extends TikaPipesIntegrationTestBase {
    @Test
    void emittersCrud() throws Exception {
        String emitterId1 = "emitter-example1";
        String emitterId2 = "emitter-example2";
        String pluginId = "filesystem-emitter";
        ManagedChannel channel =
                ManagedChannelBuilder.forAddress(InetAddress.getLocalHost().getHostAddress(), port)
                        .usePlaintext().build();
        TikaGrpc.TikaBlockingStub tikaBlockingStub = TikaGrpc.newBlockingStub(channel);

        saveEmitter(tikaBlockingStub, emitterId1, pluginId);
        saveEmitter(tikaBlockingStub, emitterId2, pluginId);

        ListEmittersReply listEmittersReply =
                tikaBlockingStub.listEmitters(ListEmittersRequest.newBuilder().build());
        Assertions.assertTrue(listEmittersReply.getGetEmitterRepliesCount() > 0);

        DeleteEmitterReply deleteEmitterReply = tikaBlockingStub.deleteEmitter(
                DeleteEmitterRequest.newBuilder().setEmitterId(emitterId1).build());
        Assertions.assertTrue(deleteEmitterReply.getSuccess());

        deleteEmitterReply = tikaBlockingStub.deleteEmitter(
                DeleteEmitterRequest.newBuilder().setEmitterId(emitterId2).build());
        Assertions.assertTrue(deleteEmitterReply.getSuccess());

        listEmittersReply = tikaBlockingStub.listEmitters(ListEmittersRequest.newBuilder().build());
        Assertions.assertEquals(0, listEmittersReply.getGetEmitterRepliesCount(),
                "Not supposed to have any emitters but found " +
                        listEmittersReply.getGetEmitterRepliesList());

        deleteEmitterReply = tikaBlockingStub.deleteEmitter(
                DeleteEmitterRequest.newBuilder().setEmitterId("nonexistent-emitter").build());
        Assertions.assertFalse(deleteEmitterReply.getSuccess());
    }

    private void saveEmitter(TikaGrpc.TikaBlockingStub tikaBlockingStub, String emitterId,
                             String pluginId) throws JsonProcessingException {
        DefaultEmitterConfig emitterConfig = new DefaultEmitterConfig();
        emitterConfig.setEmitterId(emitterId);
        emitterConfig.setPluginId(pluginId);
        emitterConfig.setConfigJson(new ObjectMapper().writeValueAsString(Map.of("foo", "bar")));

        SaveEmitterReply saveEmitterReply = tikaBlockingStub.saveEmitter(
                SaveEmitterRequest.newBuilder().setEmitterId(emitterId).setPluginId(pluginId)
                        .setEmitterConfigJson(objectMapper.writeValueAsString(emitterConfig))
                        .build());
        assertEquals(emitterId, saveEmitterReply.getEmitterId());
        GetEmitterReply getEmitterReply = tikaBlockingStub.getEmitter(
                GetEmitterRequest.newBuilder().setEmitterId(emitterId).build());
        assertEquals(emitterId, getEmitterReply.getEmitterId());
    }
}
