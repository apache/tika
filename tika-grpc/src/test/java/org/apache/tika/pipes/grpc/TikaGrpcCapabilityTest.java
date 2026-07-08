/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.pipes.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import com.asarkar.grpc.test.GrpcCleanupExtension;
import com.asarkar.grpc.test.Resources;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.apache.tika.DeleteFetcherRequest;
import org.apache.tika.FetchAndParseRequest;
import org.apache.tika.GetFetcherReply;
import org.apache.tika.GetFetcherRequest;
import org.apache.tika.SaveFetcherReply;
import org.apache.tika.SaveFetcherRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.pipes.fetcher.fs.FileSystemFetcher;

/**
 * Verifies the deny-by-default grpc capabilities (the {@code <grpc>} config section) and that
 * fetchers declared in the {@code <fetchers>} section are loaded at startup even when runtime
 * component modifications are denied.
 */
@ExtendWith(GrpcCleanupExtension.class)
public class TikaGrpcCapabilityTest {

    private static final String PIPES_SECTION =
            "  <pipes><params><numClients>1</numClients><timeoutMillis>60000</timeoutMillis>" +
                    "<maxForEmitBatchBytes>-1</maxForEmitBatchBytes></params></pipes>\n";

    private static File writeConfig(String grpcSection, String fetchersSection) throws IOException {
        File f = new File("target", "grpc-cap-" + UUID.randomUUID() + ".xml");
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<properties>\n" +
                PIPES_SECTION + grpcSection + fetchersSection + "</properties>\n";
        FileUtils.writeStringToFile(f, xml, StandardCharsets.UTF_8);
        f.deleteOnExit();
        return f;
    }

    private static TikaGrpc.TikaBlockingStub startServer(Resources resources, File config) throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        Server server = InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(new TikaGrpcServerImpl(config.getAbsolutePath()))
                .build()
                .start();
        resources.register(server, Duration.ofSeconds(10));
        ManagedChannel channel = InProcessChannelBuilder
                .forName(serverName)
                .directExecutor()
                .build();
        resources.register(channel, Duration.ofSeconds(10));
        return TikaGrpc.newBlockingStub(channel);
    }

    private static void assertPermissionDenied(Executable call) {
        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, call::run);
        assertEquals(Status.PERMISSION_DENIED.getCode(), ex.getStatus().getCode());
    }

    @FunctionalInterface
    private interface Executable {
        void run() throws Exception;
    }

    @Test
    public void lockedByDefaultDeniesSaveFetcher(Resources resources) throws Exception {
        //no <grpc> section -> deny by default
        TikaGrpc.TikaBlockingStub stub = startServer(resources, writeConfig("", "<fetchers></fetchers>\n"));
        assertPermissionDenied(() -> stub.saveFetcher(SaveFetcherRequest
                .newBuilder()
                .setFetcherId("f")
                .setFetcherClass(FileSystemFetcher.class.getName())
                .setFetcherConfigJson("{}")
                .build()));
    }

    @Test
    public void lockedByDefaultDeniesDeleteFetcher(Resources resources) throws Exception {
        TikaGrpc.TikaBlockingStub stub = startServer(resources, writeConfig("", "<fetchers></fetchers>\n"));
        assertPermissionDenied(() -> stub.deleteFetcher(DeleteFetcherRequest
                .newBuilder()
                .setFetcherId("f")
                .build()));
    }

    @Test
    public void lockedByDefaultDeniesPerRequestConfig(Resources resources) throws Exception {
        //the per-request-config guard runs before the fetcher-existence check, so a non-blank
        //additional_fetch_config_json is denied regardless of whether the fetcher exists
        TikaGrpc.TikaBlockingStub stub = startServer(resources, writeConfig("", "<fetchers></fetchers>\n"));
        assertPermissionDenied(() -> stub.fetchAndParse(FetchAndParseRequest
                .newBuilder()
                .setFetcherId("f")
                .setFetchKey("k")
                .setAdditionalFetchConfigJson("{\"basePath\":\"/etc\"}")
                .build()));
    }

    @Test
    public void configDeclaredFetcherLoadsAndIsUsableWhenLocked(Resources resources) throws Exception {
        String basePath = new File("target").getAbsolutePath();
        String fetchers = "<fetchers><fetcher class=\"" + FileSystemFetcher.class.getName() + "\">" +
                "<name>fsf</name><basePath>" + basePath + "</basePath></fetcher></fetchers>\n";
        //no <grpc> section -> runtime modifications denied, but the config-declared fetcher must load
        TikaGrpc.TikaBlockingStub stub = startServer(resources, writeConfig("", fetchers));

        GetFetcherReply reply = stub.getFetcher(GetFetcherRequest
                .newBuilder()
                .setFetcherId("fsf")
                .build());
        assertEquals("fsf", reply.getFetcherId());
        assertEquals(FileSystemFetcher.class.getName(), reply.getFetcherClass());

        //...but runtime SaveFetcher is still denied
        assertPermissionDenied(() -> stub.saveFetcher(SaveFetcherRequest
                .newBuilder()
                .setFetcherId("other")
                .setFetcherClass(FileSystemFetcher.class.getName())
                .setFetcherConfigJson("{\"basePath\":\"" + basePath + "\"}")
                .build()));
    }

    @Test
    public void unlockedAllowsSaveFetcher(Resources resources) throws Exception {
        String grpc = "<grpc><allowComponentModifications>true</allowComponentModifications>" +
                "<allowPerRequestConfig>true</allowPerRequestConfig></grpc>\n";
        TikaGrpc.TikaBlockingStub stub = startServer(resources, writeConfig(grpc, "<fetchers></fetchers>\n"));
        String basePath = new File("target").getAbsolutePath();
        SaveFetcherReply reply = stub.saveFetcher(SaveFetcherRequest
                .newBuilder()
                .setFetcherId("f")
                .setFetcherClass(FileSystemFetcher.class.getName())
                .setFetcherConfigJson("{\"basePath\":\"" + basePath + "\"}")
                .build());
        assertEquals("f", reply.getFetcherId());
    }
}
