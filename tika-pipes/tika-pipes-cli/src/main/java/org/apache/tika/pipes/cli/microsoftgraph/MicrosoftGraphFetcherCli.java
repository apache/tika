package org.apache.tika.pipes.cli.microsoftgraph;

import static org.apache.tika.pipes.cli.mapper.ObjectMapperProvider.OBJECT_MAPPER;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.LineIterator;
import org.jetbrains.annotations.NotNull;

import org.apache.tika.FetchAndParseReply;
import org.apache.tika.FetchAndParseRequest;
import org.apache.tika.SaveFetcherReply;
import org.apache.tika.SaveFetcherRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.pipes.fetchers.microsoftgraph.config.MicrosoftGraphFetcherConfig;

@Slf4j
public class MicrosoftGraphFetcherCli {
    public static final String TIKA_SERVER_GRPC_DEFAULT_HOST = "localhost";
    public static final int TIKA_SERVER_GRPC_DEFAULT_PORT = 9090;
    @Parameter(names = {"--fetch-urls"}, description = "File of URLs to fetch", help = true)
    private File urlsToFetchFile;
    @Parameter(names = {"--grpcHost"}, description = "The grpc host", help = true)
    private String host = TIKA_SERVER_GRPC_DEFAULT_HOST;
    @Parameter(names = {"--grpcPort"}, description = "The grpc server port", help = true)
    private Integer port = TIKA_SERVER_GRPC_DEFAULT_PORT;
    @Parameter(names = {"--fetcher-id"}, description = "What fetcher ID should we use? By default will use http-fetcher", help = true)
    private String fetcherId = "microsoft-graph-fetcher";
    @Parameter(names = {"-h", "-H", "--help"}, description = "Display help menu", help = true)
    private boolean help;

    @Parameter(names = {"--tenant-id"}, description = "The tenant ID for Microsoft Graph", help = true)
    protected String tenantId;

    @Parameter(names = {"--client-id"}, description = "The client ID for Microsoft Graph", help = true)
    protected String clientId;

    @Parameter(names = {"--client-secret"}, description = "The client secret for Microsoft Graph", password = true, help = true)
    private String clientSecret;

    @Parameter(names = {"--client-certificate-base64"}, description = "The client certificate bytes in Base64 for Microsoft Graph API access", password = true, help = true)
    private String certificateCertificateBase64;

    @Parameter(names = {"--certificate-password"}, description = "The client certificate password for the certificate specified in --client-certificate-base64", password = true, help = true)
    private String certificatePassword;

    public static int getRandomAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Could not find an available port", e);
        }
    }
    static int httpServerPort;
    public static void main(String[] args) throws Exception {
        httpServerPort = getRandomAvailablePort();
        MicrosoftGraphFetcherCli bulkParser = new MicrosoftGraphFetcherCli();
        JCommander commander = JCommander
                .newBuilder()
                .addObject(bulkParser)
                .build();
        commander.parse(args);
        if (bulkParser.help) {
            commander.usage();
            return;
        }
        bulkParser.runFetch();
    }

    private void runFetch() throws IOException {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .directExecutor()
                .maxInboundMessageSize(160 * 1024 * 1024) // 160 MB
                .build();
        TikaGrpc.TikaBlockingStub blockingStub = TikaGrpc.newBlockingStub(channel);
        TikaGrpc.TikaStub tikaStub = TikaGrpc.newStub(channel);

        MicrosoftGraphFetcherConfig microsoftGraphFetcherConfig = newMicrosoftGraphFetcherConfig();

        SaveFetcherReply reply = blockingStub.saveFetcher(SaveFetcherRequest
                .newBuilder()
                .setFetcherId(fetcherId)
                .setPluginId("microsoft-graph-fetcher")
                .setFetcherConfigJson(OBJECT_MAPPER.writeValueAsString(microsoftGraphFetcherConfig))
                .build());
        log.info("Saved fetcher with ID {}", reply.getFetcherId());

        List<FetchAndParseReply> successes = Collections.synchronizedList(new ArrayList<>());
        List<FetchAndParseReply> errors = Collections.synchronizedList(new ArrayList<>());

        CountDownLatch countDownLatch = new CountDownLatch(1);
        StreamObserver<FetchAndParseRequest> requestStreamObserver = tikaStub.fetchAndParseBiDirectionalStreaming(new StreamObserver<>() {
            @Override
            public void onNext(FetchAndParseReply fetchAndParseReply) {
                log.debug("Reply from fetch-and-parse - key={}, metadata={}", fetchAndParseReply.getFetchKey(), fetchAndParseReply.getMetadataList());
                if ("FETCH_AND_PARSE_EXCEPTION".equals(fetchAndParseReply.getStatus())) {
                    errors.add(fetchAndParseReply);
                } else {
                    successes.add(fetchAndParseReply);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                log.error("Received an error", throwable);
                countDownLatch.countDown();
            }

            @Override
            public void onCompleted() {
                log.info("Finished streaming fetch and parse replies");
                countDownLatch.countDown();
            }
        });

        try (LineIterator lineIterator = new LineIterator(new FileReader(urlsToFetchFile, StandardCharsets.UTF_8))) {
            while (lineIterator.hasNext()) {
                String fetchKey = lineIterator.nextLine();
                requestStreamObserver.onNext(FetchAndParseRequest
                        .newBuilder()
                        .setFetcherId(fetcherId)
                        .setFetchKey(fetchKey)
                        .setAddedMetadataJson(OBJECT_MAPPER.writeValueAsString(Map.of("X-LUMEN-DOCID", "atd_13Nxtj6o5UY3hmM9PnZnWBPNsHrGz32SeYE", "X-LUMEN-XID", "atolio:microsoft:default:onedrive:file/01MQP73ULBYILLGFFLV5BIMVF2CFR55CO3")))
                        .setFetchMetadataJson("{}")
                        .build());
            }
        }

        log.info("Done submitting URLs to {}", fetcherId);
        requestStreamObserver.onCompleted();

        try {
            if (!countDownLatch.await(3, TimeUnit.MINUTES)) {
                log.error("Timed out waiting for parse to complete");
            }
        } catch (InterruptedException e) {
            Thread
                    .currentThread()
                    .interrupt();
        }
        log.info("Fetched: success={}", successes);
    }

    @NotNull
    private MicrosoftGraphFetcherConfig newMicrosoftGraphFetcherConfig() {
        MicrosoftGraphFetcherConfig microsoftGraphFetcherConfig = new MicrosoftGraphFetcherConfig();
        microsoftGraphFetcherConfig.setFetcherId(fetcherId);
        microsoftGraphFetcherConfig.setTenantId(tenantId);
        microsoftGraphFetcherConfig.setClientId(clientId);
        microsoftGraphFetcherConfig.setClientSecret(clientSecret);
        microsoftGraphFetcherConfig.setCertificateBytesBase64(certificateCertificateBase64);
        microsoftGraphFetcherConfig.setCertificatePassword(certificatePassword);
        return microsoftGraphFetcherConfig;
    }
}
