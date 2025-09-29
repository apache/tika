package org.apache.tika.pipes.cli.s3;

import static org.apache.tika.pipes.cli.mapper.ObjectMapperProvider.OBJECT_MAPPER;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
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

import org.apache.tika.FetchAndParseReply;
import org.apache.tika.FetchAndParseRequest;
import org.apache.tika.SaveFetcherReply;
import org.apache.tika.SaveFetcherRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.pipes.fetchers.s3.config.S3FetcherConfig;

@Slf4j
public class S3FetcherCli {
    public static final String TIKA_SERVER_GRPC_DEFAULT_HOST = "localhost";
    public static final int TIKA_SERVER_GRPC_DEFAULT_PORT = 9090;
    @Parameter(names = {"--fetch-urls"}, description = "File of URLs to fetch", help = true)
    private File urlsToFetchFile;
    @Parameter(names = {"--grpcHost"}, description = "The grpc host", help = true)
    private String host = TIKA_SERVER_GRPC_DEFAULT_HOST;
    @Parameter(names = {"--grpcPort"}, description = "The grpc server port", help = true)
    private Integer port = TIKA_SERVER_GRPC_DEFAULT_PORT;
    @Parameter(names = {"--fetcher-id"}, description = "What fetcher ID should we use? By default will use http-fetcher")
    private String fetcherId = "s3-fetcher";

    @Parameter(names = {"--bucket"}, description = "S3 bucket name")
    private String bucket;

    @Parameter(names = {"--maxLength"}, description = "Maximum length of the file to fetch")
    private long maxLength = -1;

    @Parameter(names = {"--extractUserMetadata"}, description = "Flag to extract user metadata")
    private boolean extractUserMetadata = false;

    @Parameter(names = {"--spoolToTemp"}, description = "Flag to spool to temp")
    private boolean spoolToTemp = false;

    @Parameter(names = {"--region"}, description = "AWS region")
    private String region;

    @Parameter(names = {"--accessKey"}, description = "AWS access key")
    private String accessKey;

    @Parameter(names = {"--secretKey"}, description = "AWS secret key")
    private String secretKey;

    @Parameter(names = {"--sessionToken"}, description = "AWS session token")
    private String sessionToken;

    @Parameter(names = {"--endpoint"}, description = "S3 endpoint")
    private String endpoint;

    @Parameter(names = {"--pathStyleAccess"}, description = "Flag for path style access")
    private boolean pathStyleAccess = false;

    @Parameter(names = {"--connectionTimeout"}, description = "Connection timeout in milliseconds")
    private int connectionTimeout = 5000;

    @Parameter(names = {"--socketTimeout"}, description = "Socket timeout in milliseconds")
    private int socketTimeout = 5000;

    @Parameter(names = {"--maxConnections"}, description = "Maximum number of connections")
    private int maxConnections = 50;

    @Parameter(names = {"--useInstanceProfile"}, description = "Flag to use instance profile")
    private boolean useInstanceProfile = false;

    @Parameter(names = {"--roleArn"}, description = "Role ARN")
    private String roleArn;

    @Parameter(names = {"--roleSessionName"}, description = "Role session name")
    private String roleSessionName;

    @Parameter(names = {"--roleSessionDurationSeconds"}, description = "Role session duration in seconds")
    private int roleSessionDurationSeconds = 3600;

    @Parameter(names = {"--roleExternalId"}, description = "Role external ID")
    private String roleExternalId;

    @Parameter(names = {"--rolePolicy"}, description = "Role policy")
    private String rolePolicy;

    @Parameter(names = {"--roleRegion"}, description = "Role region")
    private String roleRegion;

    @Parameter(names = {"--roleEndpoint"}, description = "Role endpoint")
    private String roleEndpoint;

    @Parameter(names = {"--useArnRegion"}, description = "Flag to use ARN region")
    private boolean useArnRegion = false;

    @Parameter(names = {"--useArnEndpoint"}, description = "Flag to use ARN endpoint")
    private boolean useArnEndpoint = false;

    @Parameter(names = {"--useArnPathStyleAccess"}, description = "Flag to use ARN path style access")
    private boolean useArnPathStyleAccess = false;

    @Parameter(names = {"--useArnInstanceProfile"}, description = "Flag to use ARN instance profile")
    private boolean useArnInstanceProfile = false;

    @Parameter(names = {"--arnAccessKey"}, description = "ARN access key")
    private String arnAccessKey;

    @Parameter(names = {"--arnSecretKey"}, description = "ARN secret key")
    private String arnSecretKey;

    @Parameter(names = {"--arnSessionToken"}, description = "ARN session token")
    private String arnSessionToken;

    @Parameter(names = {"-h", "-H", "--help"}, description = "Display help menu")
    private boolean help;

    @Parameter(names = {"--credentialsProvider"}, description = "Credentials provider - must be key_secret, profile or instance")
    private String credentialsProvider = "key_secret";

    public static void main(String[] args) throws Exception {
        S3FetcherCli bulkParser = new S3FetcherCli();
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

        S3FetcherConfig s3FetcherConfig = new S3FetcherConfig();
        s3FetcherConfig.setFetcherId(fetcherId);
        s3FetcherConfig.setPluginId("s3-fetcher");
        s3FetcherConfig.setCredentialsProvider(credentialsProvider);
        s3FetcherConfig.setBucket(bucket);
        s3FetcherConfig.setMaxLength(maxLength);
        s3FetcherConfig.setExtractUserMetadata(extractUserMetadata);
        s3FetcherConfig.setSpoolToTemp(spoolToTemp);
        s3FetcherConfig.setRegion(region);
        s3FetcherConfig.setAccessKey(accessKey);
        s3FetcherConfig.setSecretKey(secretKey);
        s3FetcherConfig.setMaxConnections(maxConnections);
        s3FetcherConfig.setSessionToken(sessionToken);

        SaveFetcherReply reply = blockingStub.saveFetcher(SaveFetcherRequest
                .newBuilder()
                .setFetcherId(fetcherId)
                .setPluginId("s3-fetcher")
                .setFetcherConfigJson(OBJECT_MAPPER.writeValueAsString(s3FetcherConfig))
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
                String nextS3Key = lineIterator.nextLine();
                requestStreamObserver.onNext(FetchAndParseRequest
                        .newBuilder()
                        .setFetcherId(fetcherId)
                        .setFetchKey(nextS3Key)
                        .setFetchMetadataJson(OBJECT_MAPPER.writeValueAsString(Map.of()))
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
}
