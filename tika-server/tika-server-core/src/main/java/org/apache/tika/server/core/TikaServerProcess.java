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
package org.apache.tika.server.core;

import java.io.IOException;
import java.net.BindException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.configuration.jsse.TLSParameterJaxBUtils;
import org.apache.cxf.configuration.jsse.TLSServerParameters;
import org.apache.cxf.configuration.security.ClientAuthentication;
import org.apache.cxf.configuration.security.FiltersType;
import org.apache.cxf.configuration.security.KeyManagersType;
import org.apache.cxf.configuration.security.KeyStoreType;
import org.apache.cxf.configuration.security.TrustManagersType;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSBindingFactory;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.lifecycle.ResourceProvider;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.cxf.jaxrs.utils.JAXRSServerFactoryCustomizationUtils;
import org.apache.cxf.rs.security.cors.CrossOriginResourceSharingFilter;
import org.apache.cxf.service.factory.ServiceConstructionException;
import org.apache.cxf.transport.common.gzip.GZIPInInterceptor;
import org.apache.cxf.transport.common.gzip.GZIPOutInterceptor;
import org.apache.cxf.transport.http_jetty.JettyHTTPServerEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import org.apache.tika.Tika;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.pipes.core.EmitStrategy;
import org.apache.tika.pipes.core.EmitStrategyConfig;
import org.apache.tika.pipes.core.PipesConfig;
import org.apache.tika.pipes.core.PipesParser;
import org.apache.tika.server.core.resource.AsyncResource;
import org.apache.tika.server.core.resource.DetectorResource;
import org.apache.tika.server.core.resource.LanguageResource;
import org.apache.tika.server.core.resource.MetadataResource;
import org.apache.tika.server.core.resource.PipesParsingHelper;
import org.apache.tika.server.core.resource.PipesResource;
import org.apache.tika.server.core.resource.RecursiveMetadataResource;
import org.apache.tika.server.core.resource.TikaDetectors;
import org.apache.tika.server.core.resource.TikaMimeTypes;
import org.apache.tika.server.core.resource.TikaParsers;
import org.apache.tika.server.core.resource.TikaResource;
import org.apache.tika.server.core.resource.TikaServerResource;
import org.apache.tika.server.core.resource.TikaServerStatus;
import org.apache.tika.server.core.resource.TikaVersion;
import org.apache.tika.server.core.resource.TikaWelcome;
import org.apache.tika.server.core.resource.TranslateResource;
import org.apache.tika.server.core.resource.UnpackerResource;
import org.apache.tika.server.core.writer.CSVMessageBodyWriter;
import org.apache.tika.server.core.writer.JSONMessageBodyWriter;
import org.apache.tika.server.core.writer.JSONObjWriter;
import org.apache.tika.server.core.writer.MetadataListMessageBodyWriter;
import org.apache.tika.server.core.writer.TarWriter;
import org.apache.tika.server.core.writer.TextMessageBodyWriter;
import org.apache.tika.server.core.writer.ZipWriter;
import org.apache.tika.utils.StringUtils;

public class TikaServerProcess {


    public static final Set<String> LOG_LEVELS = new HashSet<>(Arrays.asList("debug", "info"));
    public static final int BIND_EXCEPTION = 42;
    private static final Logger LOG = LoggerFactory.getLogger(TikaServerProcess.class);
    public static int DO_NOT_RESTART_EXIT_VALUE = -100;

    private static Options getOptions() {
        Options options = new Options();
        options.addOption("h", "host", true, "host name, use * for all)");
        options.addOption("p", "port", true, "listen port");
        options.addOption("c", "config", true, "Tika Configuration xml file to override default config with.");
        options.addOption("a", "pluginsConfig", true, "Tika Configuration json for pluginscomponents");
        options.addOption("i", "id", true, "id to use for server in server status endpoint");
        options.addOption("?", "help", false, "this help message");
        options.addOption("noFork", "noFork", false, "if launched in no fork mode");
        options.addOption("forkedStatusFile", true,
                "Not allowed in -noFork: temporary file used to communicate " + "with forking process -- do not use this! " + "Should only be invoked by forking process.");
        options.addOption("tmpFilePrefix", true, "Not allowed in -noFork: prefix for temp file - for debugging only");
        options.addOption("numRestarts", true, "Not allowed in -noFork: number of times that " + "the forked server has had to be restarted.");
        return options;
    }

    public static void main(String[] args) throws Exception {
        LOG.info("Starting {} server", Tika.getString());
        try {
            Options options = getOptions();
            CommandLineParser cliParser = new DefaultParser();
            CommandLine line = cliParser.parse(options, args);
            TikaServerConfig tikaServerConfig = TikaServerConfig.load(line);
            LOG.debug("forked config: {}", tikaServerConfig);

            ServerDetails serverDetails = initServer(tikaServerConfig);
            startServer(serverDetails);

        } catch (Exception e) {
            LOG.error("Can't start: ", e);
            System.exit(-1);
        }
    }

    private static boolean isBindException(Throwable e) {
        if (e == null) {
            return false;
        }
        if (e instanceof BindException) {
            return true;
        }
        return isBindException(e.getCause());
    }

    private static void startServer(ServerDetails serverDetails) {
        try {
            //start the server
            Server server = serverDetails.sf.create();
        } catch (ServiceConstructionException e) {
            LOG.warn("exception starting server", e);
            if (isBindException(e)) {
                System.exit(BIND_EXCEPTION);
            }
            System.exit(DO_NOT_RESTART_EXIT_VALUE);
        }
        LOG.info("Started Apache Tika server {} at {}", serverDetails.serverId, serverDetails.url);
    }

    //This returns the server, configured and ready to be started.
    private static ServerDetails initServer(TikaServerConfig tikaServerConfig) throws Exception {
        String host = tikaServerConfig.getHost();
        int port = tikaServerConfig.getPort();

        // The Tika Configuration to use throughout
        TikaLoader tikaLoader;

        if (tikaServerConfig.hasConfigFile()) {
            LOG.info("Using custom config: {}", tikaServerConfig.getConfigPath());
            tikaLoader = TikaLoader.load(tikaServerConfig.getConfigPath());
        } else {
            tikaLoader = TikaLoader.loadDefault();
        }

        ServerStatus serverStatus = new ServerStatus();

        // Initialize pipes-based parsing only if /tika or /rmeta endpoints are enabled
        PipesParsingHelper pipesParsingHelper = null;
        if (needsPipesParsingHelper(tikaServerConfig)) {
            pipesParsingHelper = initPipesParsingHelper(tikaServerConfig);
            LOG.info("Pipes-based parsing enabled for /tika and /rmeta endpoints");
        }

        TikaResource.init(tikaLoader, serverStatus, pipesParsingHelper);
        JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();

        List<ResourceProvider> resourceProviders = new ArrayList<>();
        List<Object> providers = new ArrayList<>();
        loadAllProviders(tikaServerConfig, serverStatus, resourceProviders, providers);

        sf.setResourceProviders(resourceProviders);

        sf.setProviders(providers);

        //set compression interceptors
        sf.setOutInterceptors(Collections.singletonList(new GZIPOutInterceptor()));
        sf.setInInterceptors(Collections.singletonList(new GZIPInInterceptor()));

        String protocol = tikaServerConfig
                .getTlsConfig()
                .isActive() ? "https" : "http";
        String url = protocol + "://" + host + ":" + port + "/";
        sf.setAddress(url);
        sf.setResourceComparator(new ProduceTypeResourceComparator());
        BindingFactoryManager manager = sf
                .getBus()
                .getExtension(BindingFactoryManager.class);
        if (tikaServerConfig
                .getTlsConfig()
                .isActive()) {
            LOG.warn("The TLS configuration is in BETA and might change " + "dramatically in future releases.");
            // Check for expiring certificates and log warnings
            tikaServerConfig.getTlsConfig().checkCertificateExpiration();
            TLSServerParameters tlsParams = getTlsParams(tikaServerConfig.getTlsConfig());
            JettyHTTPServerEngineFactory factory = new JettyHTTPServerEngineFactory();
            factory.setBus(sf.getBus());
            factory.setTLSServerParametersForPort(host, port, tlsParams);
            JAXRSServerFactoryCustomizationUtils.customize(sf);
        } else {
            JAXRSBindingFactory factory = new JAXRSBindingFactory();
            factory.setBus(sf.getBus());
            manager.registerBindingFactory(JAXRSBindingFactory.JAXRS_BINDING_ID, factory);
        }
        ServerDetails details = new ServerDetails();
        details.sf = sf;
        details.url = url;
        details.serverStatus = serverStatus;
        return details;
    }

    private static TLSServerParameters getTlsParams(TlsConfig tlsConfig) throws GeneralSecurityException, IOException {
        KeyStoreType keyStore = new KeyStoreType();
        keyStore.setType(tlsConfig.getKeyStoreType());
        keyStore.setPassword(tlsConfig.getKeyStorePassword());
        keyStore.setFile(tlsConfig.getKeyStoreFile());
        KeyManagersType kmt = new KeyManagersType();
        kmt.setKeyStore(keyStore);
        kmt.setKeyPassword(tlsConfig.getKeyStorePassword());
        TLSServerParameters parameters = new TLSServerParameters();
        parameters.setKeyManagers(TLSParameterJaxBUtils.getKeyManagers(kmt));

        if (tlsConfig.hasTrustStore()) {
            KeyStoreType trustKeyStore = new KeyStoreType();
            trustKeyStore.setType(tlsConfig.getTrustStoreType());
            trustKeyStore.setPassword(tlsConfig.getTrustStorePassword());
            trustKeyStore.setFile(tlsConfig.getTrustStoreFile());
            TrustManagersType tmt = new TrustManagersType();
            tmt.setKeyStore(trustKeyStore);
            parameters.setTrustManagers(TLSParameterJaxBUtils.getTrustManagers(tmt, true));
        }
        ClientAuthentication clientAuthentication = new ClientAuthentication();
        clientAuthentication.setRequired(tlsConfig.isClientAuthenticationRequired());
        clientAuthentication.setWant(tlsConfig.isClientAuthenticationWanted());
        parameters.setClientAuthentication(clientAuthentication);

        // Configure TLS protocols
        if (tlsConfig.getIncludedProtocols() != null && !tlsConfig.getIncludedProtocols().isEmpty()) {
            parameters.setIncludeProtocols(tlsConfig.getIncludedProtocols());
        }
        if (tlsConfig.getExcludedProtocols() != null && !tlsConfig.getExcludedProtocols().isEmpty()) {
            parameters.setExcludeProtocols(tlsConfig.getExcludedProtocols());
        }

        // Configure cipher suites
        if ((tlsConfig.getIncludedCipherSuites() != null && !tlsConfig.getIncludedCipherSuites().isEmpty()) ||
                (tlsConfig.getExcludedCipherSuites() != null && !tlsConfig.getExcludedCipherSuites().isEmpty())) {
            FiltersType cipherSuitesFilter = new FiltersType();
            if (tlsConfig.getIncludedCipherSuites() != null) {
                cipherSuitesFilter.getInclude().addAll(tlsConfig.getIncludedCipherSuites());
            }
            if (tlsConfig.getExcludedCipherSuites() != null) {
                cipherSuitesFilter.getExclude().addAll(tlsConfig.getExcludedCipherSuites());
            }
            parameters.setCipherSuitesFilter(cipherSuitesFilter);
        }

        return parameters;
    }

    private static void loadAllProviders(TikaServerConfig tikaServerConfig, ServerStatus serverStatus, List<ResourceProvider> resourceProviders, List<Object> writers)
            throws TikaException, SAXException, IOException {
        List<ResourceProvider> tmpCoreProviders = loadCoreProviders(tikaServerConfig, serverStatus);

        resourceProviders.addAll(tmpCoreProviders);
        resourceProviders.add(new SingletonResourceProvider(new TikaWelcome(tmpCoreProviders)));

        //for now, just load everything
        //TODO figure out which ones to turn off
        writers.add(new TarWriter());
        writers.add(new ZipWriter());
        writers.add(new CSVMessageBodyWriter());
        writers.add(new MetadataListMessageBodyWriter());
        writers.add(new JSONMessageBodyWriter());
        writers.add(new TextMessageBodyWriter());
        writers.addAll(loadWriterServices());
        writers.add(new TikaServerParseExceptionMapper(tikaServerConfig.isReturnStackTrace()));
        writers.add(new JSONObjWriter());

        // Add ConfigEndpointSecurityFilter to gate /config endpoints
        writers.add(new ConfigEndpointSecurityFilter(tikaServerConfig.isEnableUnsecureFeatures()));

        TikaLoggingFilter logFilter = null;
        if (!StringUtils.isBlank(tikaServerConfig.getLogLevel())) {
            String logLevel = tikaServerConfig.getLogLevel();
            if (LOG_LEVELS.contains(logLevel)) {
                boolean isInfoLevel = "info".equals(logLevel);
                logFilter = new TikaLoggingFilter(isInfoLevel);
                writers.add(logFilter);
            } else {
                LOG.warn("Unsupported request URI log level: {}", logLevel);
            }
        }

        CrossOriginResourceSharingFilter corsFilter = null;
        if (!StringUtils.isBlank(tikaServerConfig.getCors())) {
            corsFilter = new CrossOriginResourceSharingFilter();
            String url = tikaServerConfig.getCors();
            List<String> origins = new ArrayList<>();
            if (!url.equals("*")) {
                origins.add(url);         // Empty list allows all origins.
            }
            corsFilter.setAllowOrigins(origins);
            writers.add(corsFilter);
        }

    }

    private static List<ResourceProvider> loadCoreProviders(TikaServerConfig tikaServerConfig, ServerStatus serverStatus) throws TikaException, IOException, SAXException {
        List<ResourceProvider> resourceProviders = new ArrayList<>();
        boolean addAsyncResource = false;
        boolean addPipesResource = false;
        if (tikaServerConfig
                .getEndpoints()
                .size() == 0) {
            resourceProviders.add(new SingletonResourceProvider(new MetadataResource()));
            resourceProviders.add(new SingletonResourceProvider(new RecursiveMetadataResource()));
            resourceProviders.add(new SingletonResourceProvider(new DetectorResource(serverStatus)));
            resourceProviders.add(new SingletonResourceProvider(new LanguageResource()));
            resourceProviders.add(new SingletonResourceProvider(new TranslateResource(serverStatus)));
            resourceProviders.add(new SingletonResourceProvider(new TikaResource()));
            resourceProviders.add(new SingletonResourceProvider(new UnpackerResource()));
            resourceProviders.add(new SingletonResourceProvider(new TikaMimeTypes()));
            resourceProviders.add(new SingletonResourceProvider(new TikaDetectors()));
            resourceProviders.add(new SingletonResourceProvider(new TikaParsers()));
            resourceProviders.add(new SingletonResourceProvider(new TikaVersion()));
            if (tikaServerConfig.isEnableUnsecureFeatures()) {
                addAsyncResource = true;
                addPipesResource = true;
                resourceProviders.add(new SingletonResourceProvider(new TikaServerStatus(serverStatus)));
            }
        } else {
            for (String endPoint : tikaServerConfig.getEndpoints()) {
                if ("meta".equals(endPoint)) {
                    resourceProviders.add(new SingletonResourceProvider(new MetadataResource()));
                } else if ("rmeta".equals(endPoint)) {
                    resourceProviders.add(new SingletonResourceProvider(new RecursiveMetadataResource()));
                } else if ("detect".equals(endPoint)) {
                    resourceProviders.add(new SingletonResourceProvider(new DetectorResource(serverStatus)));
                } else if ("language".equals(endPoint)) {
                    resourceProviders.add(new SingletonResourceProvider(new LanguageResource()));
                } else if ("translate".equals(endPoint)) {
                    resourceProviders.add(new SingletonResourceProvider(new TranslateResource(serverStatus)));
                } else if ("tika".equals(endPoint)) {
                    resourceProviders.add(new SingletonResourceProvider(new TikaResource()));
                } else if ("unpack".equals(endPoint)) {
                    resourceProviders.add(new SingletonResourceProvider(new UnpackerResource()));
                } else if ("mime".equals(endPoint)) {
                    resourceProviders.add(new SingletonResourceProvider(new TikaMimeTypes()));
                } else if ("detectors".equals(endPoint)) {
                    resourceProviders.add(new SingletonResourceProvider(new TikaDetectors()));
                } else if ("parsers".equals(endPoint)) {
                    resourceProviders.add(new SingletonResourceProvider(new TikaParsers()));
                } else if ("version".equals(endPoint)) {
                    resourceProviders.add(new SingletonResourceProvider(new TikaVersion()));
                } else if ("pipes".equals(endPoint)) {
                    addPipesResource = true;
                } else if ("async".equals(endPoint)) {
                    addAsyncResource = true;
                } else if ("status".equals(endPoint)) {
                    resourceProviders.add(new SingletonResourceProvider(new TikaServerStatus(serverStatus)));
                }
            }
        }

        if (addAsyncResource) {
            final AsyncResource localAsyncResource = new AsyncResource(tikaServerConfig.getConfigPath());
            Runtime
                    .getRuntime()
                    .addShutdownHook(new Thread(() -> {
                        try {
                            localAsyncResource.shutdownNow();
                        } catch (Exception e) {
                            LOG.warn("problem shutting down local async resource", e);
                        }
                    }));
            resourceProviders.add(new SingletonResourceProvider(localAsyncResource));
        }
        if (addPipesResource) {
            final PipesResource localPipesResource = new PipesResource(tikaServerConfig.getConfigPath());
            Runtime
                    .getRuntime()
                    .addShutdownHook(new Thread(() -> {
                        try {
                            localPipesResource.close();
                        } catch (Exception e) {
                            LOG.warn("exception closing local pipes resource", e);
                        }
                    }));
            resourceProviders.add(new SingletonResourceProvider(localPipesResource));
        }
        resourceProviders.addAll(loadResourceServices(serverStatus));
        return resourceProviders;
    }

    private static Collection<? extends ResourceProvider> loadResourceServices(ServerStatus serverStatus) {
        List<TikaServerResource> resources = new ServiceLoader(TikaServerProcess.class.getClassLoader()).loadServiceProviders(TikaServerResource.class);
        List<ResourceProvider> providers = new ArrayList<>();
        for (TikaServerResource r : resources) {
            LOG.info("loading resource from SPI: " + r.getClass());
            if (r instanceof ServerStatusResource) {
                ((ServerStatusResource) r).setServerStatus(serverStatus);
            }
            providers.add(new SingletonResourceProvider(r));
        }
        return providers;
    }

    private static Collection<?> loadWriterServices() {
        return new ServiceLoader(TikaServerProcess.class.getClassLoader()).loadServiceProviders(org.apache.tika.server.core.writer.TikaServerWriter.class);
    }

    /**
     * Determines if PipesParsingHelper is needed based on configured endpoints.
     * It's needed when /tika or /rmeta endpoints are enabled (either explicitly or by default).
     */
    private static boolean needsPipesParsingHelper(TikaServerConfig tikaServerConfig) {
        List<String> endpoints = tikaServerConfig.getEndpoints();
        // If no endpoints specified, all default endpoints are loaded (including tika and rmeta)
        if (endpoints == null || endpoints.isEmpty()) {
            return true;
        }
        // Check if tika or rmeta are in the configured endpoints
        return endpoints.contains("tika") || endpoints.contains("rmeta");
    }

    /**
     * Determines if the /unpack endpoint is enabled based on configured endpoints.
     */
    private static boolean isUnpackEndpointEnabled(TikaServerConfig tikaServerConfig) {
        List<String> endpoints = tikaServerConfig.getEndpoints();
        // If no endpoints specified, all default endpoints are loaded (including unpack)
        if (endpoints == null || endpoints.isEmpty()) {
            return true;
        }
        return endpoints.contains("unpack");
    }

    /**
     * Initializes the PipesParsingHelper for pipes-based parsing with process isolation.
     * <p>
     * The PipesParser will be configured with PASSBACK_ALL emit strategy so that
     * parsed results are returned through the socket connection.
     * <p>
     * If no config file is provided, a minimal default configuration will be created.
     * The plugin-roots will default to a "plugins" directory at the same level as the server jar.
     * <p>
     * A dedicated temp directory is created for input files, and a file-system-fetcher
     * is configured with basePath pointing to that directory. This ensures child processes
     * can only access files in the designated temp directory (security boundary).
     *
     * @param tikaServerConfig the server configuration
     * @return the PipesParsingHelper
     * @throws Exception if pipes initialization fails
     */
    private static PipesParsingHelper initPipesParsingHelper(TikaServerConfig tikaServerConfig) throws Exception {
        // Create dedicated temp directory for input files
        Path inputTempDirectory = Files.createTempDirectory("tika-server-input-");
        LOG.info("Created input temp directory: {}", inputTempDirectory);

        // Only create unpack temp directory if /unpack endpoint is enabled
        Path unpackTempDirectory = null;
        if (isUnpackEndpointEnabled(tikaServerConfig)) {
            unpackTempDirectory = Files.createTempDirectory("tika-server-unpack-");
            LOG.info("Created unpack temp directory: {}", unpackTempDirectory);
        }

        // Load or create config, adding the fetcher (and emitter if unpack is enabled)
        Path configPath;
        if (tikaServerConfig.hasConfigFile()) {
            configPath = tikaServerConfig.getConfigPath();
        } else {
            configPath = createDefaultConfig(inputTempDirectory, unpackTempDirectory);
        }

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(configPath);

        // Ensure fetcher (and emitter if unpack is enabled) are configured with correct basePaths
        configPath = ensureServerComponents(configPath, tikaJsonConfig,
                inputTempDirectory, unpackTempDirectory);
        tikaJsonConfig = TikaJsonConfig.load(configPath);

        // Load or create PipesConfig with defaults
        PipesConfig pipesConfig = tikaJsonConfig.deserialize("pipes", PipesConfig.class);
        if (pipesConfig == null) {
            pipesConfig = new PipesConfig();
        }

        // Use PASSBACK_ALL strategy: results are returned through the socket
        pipesConfig.setEmitStrategy(new EmitStrategyConfig(EmitStrategy.PASSBACK_ALL));

        // Create PipesParser
        PipesParser pipesParser = PipesParser.load(tikaJsonConfig, pipesConfig, configPath);

        // Create and return the helper
        PipesParsingHelper helper = new PipesParsingHelper(pipesParser, pipesConfig,
                inputTempDirectory, unpackTempDirectory);

        // Register shutdown hook to clean up PipesParser and temp directories
        final Path inputDirToClean = inputTempDirectory;
        final Path unpackDirToClean = unpackTempDirectory;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                LOG.info("Shutting down PipesParser");
                pipesParser.close();
            } catch (Exception e) {
                LOG.warn("Error closing PipesParser", e);
            }
            // Clean up temp directories
            cleanupTempDirectory(inputDirToClean);
            if (unpackDirToClean != null) {
                cleanupTempDirectory(unpackDirToClean);
            }
        }));

        return helper;
    }

    private static void cleanupTempDirectory(Path tempDir) {
        try {
            if (Files.exists(tempDir)) {
                Files.walk(tempDir)
                        .sorted((a, b) -> -a.compareTo(b)) // Delete files before directories
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException e) {
                                LOG.warn("Failed to delete: {}", p);
                            }
                        });
            }
        } catch (IOException e) {
            LOG.warn("Error cleaning up temp directory: {}", tempDir, e);
        }
    }

    /**
     * Default plugins directory name, relative to current working directory.
     */
    private static final String DEFAULT_PLUGINS_DIR = "plugins";

    /**
     * Creates a default configuration file with plugin-roots set to the "plugins" directory
     * relative to the current working directory, the tika-server-fetcher configured
     * with basePath pointing to the input temp directory, and optionally the unpack-emitter
     * configured with basePath pointing to the unpack temp directory.
     *
     * @param inputTempDirectory the temp directory for input files
     * @param unpackTempDirectory the temp directory for unpack output files (may be null)
     */
    private static Path createDefaultConfig(Path inputTempDirectory,
                                            Path unpackTempDirectory) throws IOException {
        Path pluginsDir = Path.of(DEFAULT_PLUGINS_DIR).toAbsolutePath();

        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode rootNode = mapper.createObjectNode();

        // Create fetchers section
        com.fasterxml.jackson.databind.node.ObjectNode fetchersNode = mapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ObjectNode fetcherNode = mapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ObjectNode fetcherTypeConfig = mapper.createObjectNode();
        fetcherTypeConfig.put("basePath", inputTempDirectory.toAbsolutePath().toString());
        fetcherNode.set("file-system-fetcher", fetcherTypeConfig);
        fetchersNode.set(PipesParsingHelper.DEFAULT_FETCHER_ID, fetcherNode);
        rootNode.set("fetchers", fetchersNode);

        // Create emitters section if unpack is enabled
        if (unpackTempDirectory != null) {
            com.fasterxml.jackson.databind.node.ObjectNode emittersNode = mapper.createObjectNode();
            com.fasterxml.jackson.databind.node.ObjectNode emitterNode = mapper.createObjectNode();
            com.fasterxml.jackson.databind.node.ObjectNode emitterTypeConfig = mapper.createObjectNode();
            emitterTypeConfig.put("basePath", unpackTempDirectory.toAbsolutePath().toString());
            emitterTypeConfig.put("onExists", "REPLACE");
            emitterNode.set("file-system-emitter", emitterTypeConfig);
            emittersNode.set(PipesParsingHelper.UNPACK_EMITTER_ID, emitterNode);
            rootNode.set("emitters", emittersNode);
        }

        // Create pipes section
        com.fasterxml.jackson.databind.node.ObjectNode pipesNode = mapper.createObjectNode();
        pipesNode.put("numClients", 4);
        pipesNode.put("timeoutMillis", 60000);
        rootNode.set("pipes", pipesNode);

        // Set plugin-roots
        rootNode.put("plugin-roots", pluginsDir.toString());

        Path tempConfig = Files.createTempFile("tika-server-default-config-", ".json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(tempConfig.toFile(), rootNode);
        tempConfig.toFile().deleteOnExit();

        LOG.info("Created default config with plugin-roots: {}", pluginsDir);
        return tempConfig;
    }

    /**
     * Ensures the tika-server-fetcher exists in the config with basePath pointing to
     * the input temp directory. If unpackTempDirectory is provided, also ensures the
     * unpack-emitter exists.
     * <p>
     * The fetcher is used by legacy endpoints (/tika, /rmeta, etc.) to read uploaded files
     * that have been spooled to the input temp directory.
     * <p>
     * The emitter is used by /unpack endpoints to write unpacked files that are then
     * streamed back to the client.
     * <p>
     * Both components are configured with basePath (not allowAbsolutePaths) so child processes
     * can only access files within their designated temp directories (security boundary).
     *
     * @param originalConfigPath the original config file path
     * @param tikaJsonConfig the parsed Tika JSON config
     * @param inputTempDirectory the temp directory for input files
     * @param unpackTempDirectory the temp directory for unpack output files (may be null)
     * @return the config path to use (always a new merged config with fetcher and optionally emitter)
     */
    private static Path ensureServerComponents(Path originalConfigPath, TikaJsonConfig tikaJsonConfig,
                                               Path inputTempDirectory,
                                               Path unpackTempDirectory) throws IOException {
        LOG.info("Configuring {} with basePath={}", PipesParsingHelper.DEFAULT_FETCHER_ID, inputTempDirectory);

        // Read original config as a mutable tree
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode rootNode =
                (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(originalConfigPath.toFile());

        // Get or create the fetchers section
        com.fasterxml.jackson.databind.node.ObjectNode fetchersNode;
        if (rootNode.has("fetchers") && rootNode.get("fetchers").isObject()) {
            fetchersNode = (com.fasterxml.jackson.databind.node.ObjectNode) rootNode.get("fetchers");
        } else {
            fetchersNode = mapper.createObjectNode();
            rootNode.set("fetchers", fetchersNode);
        }

        // Create the fetcher config with basePath
        // Structure: "tika-server-fetcher": { "file-system-fetcher": { "basePath": "/tmp/..." } }
        com.fasterxml.jackson.databind.node.ObjectNode fetcherTypeConfig = mapper.createObjectNode();
        fetcherTypeConfig.put("basePath", inputTempDirectory.toAbsolutePath().toString());

        com.fasterxml.jackson.databind.node.ObjectNode fetcherNode = mapper.createObjectNode();
        fetcherNode.set("file-system-fetcher", fetcherTypeConfig);

        fetchersNode.set(PipesParsingHelper.DEFAULT_FETCHER_ID, fetcherNode);

        // Only add unpack-emitter if unpack endpoint is enabled
        if (unpackTempDirectory != null) {
            LOG.info("Configuring {} with basePath={}", PipesParsingHelper.UNPACK_EMITTER_ID, unpackTempDirectory);

            // Get or create the emitters section
            com.fasterxml.jackson.databind.node.ObjectNode emittersNode;
            if (rootNode.has("emitters") && rootNode.get("emitters").isObject()) {
                emittersNode = (com.fasterxml.jackson.databind.node.ObjectNode) rootNode.get("emitters");
            } else {
                emittersNode = mapper.createObjectNode();
                rootNode.set("emitters", emittersNode);
            }

            // Create the emitter config with basePath
            // Structure: "unpack-emitter": { "file-system-emitter": { "basePath": "/tmp/...", "onExists": "REPLACE" } }
            com.fasterxml.jackson.databind.node.ObjectNode emitterTypeConfig = mapper.createObjectNode();
            emitterTypeConfig.put("basePath", unpackTempDirectory.toAbsolutePath().toString());
            emitterTypeConfig.put("onExists", "REPLACE");

            com.fasterxml.jackson.databind.node.ObjectNode emitterNode = mapper.createObjectNode();
            emitterNode.set("file-system-emitter", emitterTypeConfig);

            emittersNode.set(PipesParsingHelper.UNPACK_EMITTER_ID, emitterNode);
        }

        // Ensure plugin-roots is set (required for child processes)
        if (!rootNode.has("plugin-roots")) {
            Path pluginsDir = Path.of(DEFAULT_PLUGINS_DIR).toAbsolutePath();
            rootNode.put("plugin-roots", pluginsDir.toString());
            LOG.info("Added default plugin-roots: {}", pluginsDir);
        }

        // Write merged config to temp file
        Path mergedConfig = Files.createTempFile("tika-server-merged-config-", ".json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(mergedConfig.toFile(), rootNode);
        mergedConfig.toFile().deleteOnExit();

        LOG.debug("Created merged config: {}", mergedConfig);
        return mergedConfig;
    }

    private static class ServerDetails {
        JAXRSServerFactoryBean sf;
        String serverId;
        String url;
        ServerStatus serverStatus;
    }
}
