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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSBindingFactory;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.lifecycle.ResourceProvider;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.cxf.rs.security.cors.CrossOriginResourceSharingFilter;
import org.apache.cxf.transport.common.gzip.GZIPInInterceptor;
import org.apache.cxf.transport.common.gzip.GZIPOutInterceptor;
import org.apache.tika.Tika;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.parser.DigestingParser;
import org.apache.tika.parser.digestutils.BouncyCastleDigester;
import org.apache.tika.parser.digestutils.CommonsDigester;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.pipes.fetchiterator.FetchEmitTuple;
import org.apache.tika.server.core.resource.AsyncEmitter;
import org.apache.tika.server.core.resource.AsyncParser;
import org.apache.tika.server.core.resource.AsyncResource;
import org.apache.tika.server.core.resource.DetectorResource;
import org.apache.tika.server.core.resource.EmitterResource;
import org.apache.tika.server.core.resource.LanguageResource;
import org.apache.tika.server.core.resource.MetadataResource;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class TikaServerProcess {


    //used in fork mode -- restart after processing this many files
    private static final long DEFAULT_MAX_FILES = 100000;
    private static String ENABLE_UNSECURE_FEATURES = "enableUnsecureFeatures";

    private static final int DEFAULT_DIGEST_MARK_LIMIT = 20 * 1024 * 1024;
    public static final Set<String> LOG_LEVELS = new HashSet<>(Arrays.asList("debug", "info"));
    private static final Logger LOG = LoggerFactory.getLogger(TikaServerProcess.class);


    private static Options getOptions() {
        Options options = new Options();
        options.addOption("C", "cors", true, "origin allowed to make CORS requests (default=NONE)\nall allowed if \"all\"");
        options.addOption("h", "host", true, "host name, use * for all)");
        options.addOption("p", "port", true, "listen port");
        options.addOption("c", "config", true, "Tika Configuration file to override default config with.");
        options.addOption("d", "digest", true, "include digest in metadata, e.g. md5,sha1:32,sha256");
        options.addOption("dml", "digestMarkLimit", true, "max number of bytes to mark on stream for digest");
        options.addOption("l", "log", true, "request URI log level ('debug' or 'info')");
        options.addOption("s", "includeStack", false, "whether or not to return a stack trace\nif there is an exception during 'parse'");
        options.addOption("i", "id", true, "id to use for server in server status endpoint");
        options.addOption("status", false, "enable the status endpoint");
        options.addOption("?", "help", false, "this help message");
        options.addOption("enableUnsecureFeatures", false, "this is required to enable fetchers and emitters. " +
                " The user acknowledges that fetchers and emitters introduce potential security vulnerabilities.");
        options.addOption("noFork", false, "legacy mode, less robust -- this starts up tika-server" +
                " without forking a process.");
        options.addOption("taskTimeoutMillis", true,
                "Not allowed in -noFork: how long to wait for a task (e.g. parse) to finish");
        options.addOption("taskPulseMillis", true,
                "Not allowed in -noFork: how often to check if a task has timed out.");
        options.addOption("pingTimeoutMillis", true,
                "Not allowed in -noFork: how long to wait to wait for a ping and/or ping response.");
        options.addOption("pingPulseMillis", true,
                "Not allowed in -noFork: how often to check if a ping has timed out.");
        options.addOption("maxFiles", true,
                "Not allowed in -noFork: shutdown server after this many files (to handle parsers that might introduce " +
                        "slowly building memory leaks); the default is " + DEFAULT_MAX_FILES + ". Set to -1 to turn this off.");
        options.addOption("javaHome", true,
                "Not allowed in -noFork: override system property JAVA_HOME for calling java for the forked process");
        options.addOption("forkedStatusFile", true,
                "Not allowed in -noFork: temporary file used to communicate " +
                        "with forking process -- do not use this! Should only be invoked by forking process.");
        options.addOption("tmpFilePrefix", true,
                "Not allowed in -noFork: prefix for temp file - for debugging only");
        options.addOption("numRestarts", true,
                "Not allowed in -noFork: number of times that the forked server has had to be restarted.");
        return options;
    }

    public static void main(String[] args) {
        LOG.info("Starting {} server", new Tika());
        try {
            Options options = getOptions();
            CommandLineParser cliParser = new DefaultParser();
            CommandLine line = cliParser.parse(options, args);
            mainLoop(line, options);
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("Can't start: ", e);
            System.exit(-1);
        }
    }

    private static void mainLoop(CommandLine commandLine, Options options) throws Exception {
        AsyncResource asyncResource = null;
        ArrayBlockingQueue<FetchEmitTuple> asyncFetchEmitQueue = null;
        ArrayBlockingQueue<EmitData> asyncEmitData = null;
        int numAsyncParserThreads = 10;
        if (commandLine.hasOption(ENABLE_UNSECURE_FEATURES)) {
            asyncResource = new AsyncResource();
            asyncFetchEmitQueue = asyncResource.getFetchEmitQueue(10000);
            asyncEmitData = asyncResource.getEmitDataQueue(1000);
        }

        ServerDetails serverDetails = initServer(commandLine, asyncResource);
        ExecutorService executorService = Executors.newFixedThreadPool(numAsyncParserThreads+1);
        ExecutorCompletionService<Integer> executorCompletionService = new ExecutorCompletionService<>(executorService);

        if (asyncFetchEmitQueue != null) {
            executorCompletionService.submit(new AsyncEmitter(asyncEmitData));
            for (int i = 0; i < numAsyncParserThreads; i++) {
                executorCompletionService.submit(new AsyncParser(asyncFetchEmitQueue, asyncEmitData));
            }
        }
        //start the server
        Server server = serverDetails.sf.create();
        LOG.info("Started Apache Tika server {} at {}",
                serverDetails.serverId,
                serverDetails.url);

        while (true) {
            Future<Integer> future = executorCompletionService.poll(1, TimeUnit.MINUTES);
            if (future != null) {
                LOG.warn("future val: " + future.get());
            }
        }
    }

    //This returns the server, configured and ready to be started.
    private static ServerDetails initServer(CommandLine line,
                                     AsyncResource asyncResource) throws Exception {
        String host = null;

        if (line.hasOption("host")) {
            host = line.getOptionValue("host");
            if ("*".equals(host)) {
                host = "0.0.0.0";
            }
        } else {
            throw new IllegalArgumentException("Must specify 'host'");
        }

        int port = -1;

        if (line.hasOption("port")) {
            port = Integer.valueOf(line.getOptionValue("port"));
        } else {
            throw new IllegalArgumentException("Must specify port");
        }

        boolean returnStackTrace = false;
        if (line.hasOption("includeStack")) {
            returnStackTrace = true;
        }

        TikaLoggingFilter logFilter = null;
        if (line.hasOption("log")) {
            String logLevel = line.getOptionValue("log");
            if (LOG_LEVELS.contains(logLevel)) {
                boolean isInfoLevel = "info".equals(logLevel);
                logFilter = new TikaLoggingFilter(isInfoLevel);
            } else {
                LOG.info("Unsupported request URI log level: {}", logLevel);
            }
        }

        CrossOriginResourceSharingFilter corsFilter = null;
        if (line.hasOption("cors")) {
            corsFilter = new CrossOriginResourceSharingFilter();
            String url = line.getOptionValue("cors");
            List<String> origins = new ArrayList<>();
            if (!url.equals("*")) origins.add(url);         // Empty list allows all origins.
            corsFilter.setAllowOrigins(origins);
        }

        // The Tika Configuration to use throughout
        TikaConfig tika;

        if (line.hasOption("config")) {
            String configFilePath = line.getOptionValue("config");
            LOG.info("Using custom config: {}", configFilePath);
            tika = new TikaConfig(configFilePath);
        } else {
            tika = TikaConfig.getDefaultConfig();
        }

        DigestingParser.Digester digester = null;
        if (line.hasOption("digest")) {
            int digestMarkLimit = DEFAULT_DIGEST_MARK_LIMIT;
            if (line.hasOption("dml")) {
                String dmlS = line.getOptionValue("dml");
                try {
                    digestMarkLimit = Integer.parseInt(dmlS);
                } catch (NumberFormatException e) {
                    throw new RuntimeException("Must have parseable int after digestMarkLimit(dml): " + dmlS);
                }
            }
            try {
                digester = new CommonsDigester(digestMarkLimit, line.getOptionValue("digest"));
            } catch (IllegalArgumentException commonsException) {
                try {
                    digester = new BouncyCastleDigester(digestMarkLimit, line.getOptionValue("digest"));
                } catch (IllegalArgumentException bcException) {
                    throw new IllegalArgumentException("Tried both CommonsDigester (" + commonsException.getMessage() +
                            ") and BouncyCastleDigester (" + bcException.getMessage() + ")", bcException);
                }
            }
        }

        InputStreamFactory inputStreamFactory = null;
        if (line.hasOption(ENABLE_UNSECURE_FEATURES)) {
            inputStreamFactory = new FetcherStreamFactory(tika.getFetcherManager());
        } else {
            inputStreamFactory = new DefaultInputStreamFactory();
        }
        logFetchersAndEmitters(line.hasOption(ENABLE_UNSECURE_FEATURES), tika);
        String serverId = line.hasOption("i") ? line.getOptionValue("i") : UUID.randomUUID().toString();
        LOG.debug("SERVER ID:" + serverId);
        ServerStatus serverStatus;

        if (line.hasOption("noFork")) {
            serverStatus = new ServerStatus(serverId, 0, true);
        } else {
            serverStatus = new ServerStatus(serverId, Integer.parseInt(line.getOptionValue("numRestarts")),
                    false);
            //redirect!!!
            InputStream in = System.in;
            System.setIn(new ByteArrayInputStream(new byte[0]));
            System.setOut(System.err);

            long maxFiles = DEFAULT_MAX_FILES;
            if (line.hasOption("maxFiles")) {
                maxFiles = Long.parseLong(line.getOptionValue("maxFiles"));
            }

            ServerTimeoutConfig serverTimeouts = configureServerTimeouts(line);
            String forkedStatusFile = line.getOptionValue("forkedStatusFile");
            Thread serverThread =
                    new Thread(new ServerStatusWatcher(serverStatus, in,
                            Paths.get(forkedStatusFile), maxFiles, serverTimeouts));

            serverThread.start();
        }
        TikaResource.init(tika, digester, inputStreamFactory, serverStatus);
        JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();

        List<ResourceProvider> rCoreProviders = new ArrayList<>();
        rCoreProviders.add(new SingletonResourceProvider(new MetadataResource()));
        rCoreProviders.add(new SingletonResourceProvider(new RecursiveMetadataResource()));
        rCoreProviders.add(new SingletonResourceProvider(new DetectorResource(serverStatus)));
        rCoreProviders.add(new SingletonResourceProvider(new LanguageResource()));
        rCoreProviders.add(new SingletonResourceProvider(new TranslateResource(serverStatus)));
        rCoreProviders.add(new SingletonResourceProvider(new TikaResource()));
        rCoreProviders.add(new SingletonResourceProvider(new UnpackerResource()));
        rCoreProviders.add(new SingletonResourceProvider(new TikaMimeTypes()));
        rCoreProviders.add(new SingletonResourceProvider(new TikaDetectors()));
        rCoreProviders.add(new SingletonResourceProvider(new TikaParsers()));
        rCoreProviders.add(new SingletonResourceProvider(new TikaVersion()));
        if (line.hasOption(ENABLE_UNSECURE_FEATURES)) {
            rCoreProviders.add(new SingletonResourceProvider(new EmitterResource()));
            rCoreProviders.add(new SingletonResourceProvider(asyncResource));
        }
        rCoreProviders.addAll(loadResourceServices());
        if (line.hasOption("status")) {
            rCoreProviders.add(new SingletonResourceProvider(new TikaServerStatus(serverStatus)));
        }
        List<ResourceProvider> rAllProviders = new ArrayList<>(rCoreProviders);
        rAllProviders.add(new SingletonResourceProvider(new TikaWelcome(rCoreProviders)));
        sf.setResourceProviders(rAllProviders);

        List<Object> providers = new ArrayList<>();
        providers.add(new TarWriter());
        providers.add(new ZipWriter());
        providers.add(new CSVMessageBodyWriter());
        providers.add(new MetadataListMessageBodyWriter());
        providers.add(new JSONMessageBodyWriter());
        providers.add(new TextMessageBodyWriter());
        providers.addAll(loadWriterServices());
        providers.add(new TikaServerParseExceptionMapper(returnStackTrace));
        providers.add(new JSONObjWriter());

        if (logFilter != null) {
            providers.add(logFilter);
        }
        if (corsFilter != null) {
            providers.add(corsFilter);
        }
        sf.setProviders(providers);

        //set compression interceptors
        sf.setOutInterceptors(
                Collections.singletonList(new GZIPOutInterceptor())
        );
        sf.setInInterceptors(
                Collections.singletonList(new GZIPInInterceptor()));

        String url = "http://" + host + ":" + port + "/";
        sf.setAddress(url);
        BindingFactoryManager manager = sf.getBus().getExtension(BindingFactoryManager.class);
        JAXRSBindingFactory factory = new JAXRSBindingFactory();
        factory.setBus(sf.getBus());
        manager.registerBindingFactory(JAXRSBindingFactory.JAXRS_BINDING_ID, factory);
        ServerDetails details = new ServerDetails();
        details.sf = sf;
        details.url = url;
        details.serverId = serverId;
        return details;
    }

    private static void logFetchersAndEmitters(boolean enableUnsecureFeatures, TikaConfig tika) {
        if (enableUnsecureFeatures) {
            StringBuilder sb = new StringBuilder();
            Set<String> supportedFetchers = tika.getFetcherManager().getSupported();
            sb.append("enableSecureFeatures has been selected.\n");
            if (supportedFetchers.size() == 0) {
                sb.append("There are no fetchers specified in the TikaConfig");
            } else {
                sb.append("The following fetchers are available to whomever has access to this server:\n");
                for (String p : supportedFetchers) {
                    sb.append(p).append("\n");
                }
            }
            Set<String> emitters = tika.getEmitterManager().getSupported();
            if (supportedFetchers.size() == 0) {
                sb.append("There are no emitters specified in the TikaConfig");
            } else {
                sb.append("The following emitters are available to whomever has access to this server:\n");
                for (String e : emitters) {
                    sb.append(e).append("\n");
                }
            }
            LOG.info(sb.toString());
        } else {
            if (tika.getEmitterManager().getSupported().size() > 0) {
                String warn = "-enableUnsecureFeatures has not been specified on the commandline.\n" +
                        "The " + tika.getEmitterManager().getSupported().size() + " emitter(s) that you've\n" +
                        "specified in TikaConfig will not be available on the /emit endpoint\n" +
                        "To enable your emitters, start tika-server with the -enableUnsecureFeatures flag\n\n";
                LOG.warn(warn);
            }
            if (tika.getFetcherManager().getSupported().size() > 0) {
                String warn = "-enableUnsecureFeatures has not been specified on the commandline.\n" +
                        "The " + tika.getFetcherManager().getSupported().size() + " fetcher(s) that you've\n" +
                        "specified in TikaConfig will not be available\n" +
                        "To enable your fetchers, start tika-server with the -enableUnsecureFeatures flag\n\n";
                LOG.warn(warn);
            }
        }
    }

    private static Collection<? extends ResourceProvider> loadResourceServices() {
        List<TikaServerResource> resources = new ServiceLoader(TikaServerProcess.class.getClassLoader())
                .loadServiceProviders(TikaServerResource.class);
        List<ResourceProvider> providers = new ArrayList<>();

        for (TikaServerResource r : resources) {
            providers.add(new SingletonResourceProvider(r));
        }
        return providers;
    }

    private static Collection<?> loadWriterServices() {
        return new ServiceLoader(TikaServerProcess.class.getClassLoader())
                .loadServiceProviders(org.apache.tika.server.core.writer.TikaServerWriter.class);
    }

    private static void usage(Options options) {
        HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.printHelp("tikaserver", options);
        System.exit(-1);
    }

    private static ServerTimeoutConfig configureServerTimeouts(CommandLine line) {
        ServerTimeoutConfig serverTimeouts = new ServerTimeoutConfig();
        /*TODO -- add these in
        if (line.hasOption("forkedProcessStartupMillis")) {
            serverTimeouts.setForkedProcessStartupMillis(
                    Long.parseLong(line.getOptionValue("forkedProcessStartupMillis")));
        }
        if (line.hasOption("forkedProcessShutdownMillis")) {
            serverTimeouts.setForkedProcessShutdownMillis(
                    Long.parseLong(line.getOptionValue("forkedProcesShutdownMillis")));
        }*/
        if (line.hasOption("taskTimeoutMillis")) {
            serverTimeouts.setTaskTimeoutMillis(
                    Long.parseLong(line.getOptionValue("taskTimeoutMillis")));
        }
        if (line.hasOption("pingTimeoutMillis")) {
            serverTimeouts.setPingTimeoutMillis(
                    Long.parseLong(line.getOptionValue("pingTimeoutMillis")));
        }
        if (line.hasOption("pingPulseMillis")) {
            serverTimeouts.setPingPulseMillis(
                    Long.parseLong(line.getOptionValue("pingPulseMillis")));
        }

        if (line.hasOption("maxRestarts")) {
            serverTimeouts.setMaxRestarts(Integer.parseInt(line.getOptionValue("maxRestarts")));
        }

        if (line.hasOption("maxForkedStartupMillis")) {
            serverTimeouts.setMaxForkedStartupMillis(
                    Long.parseLong(line.getOptionValue("maxForkedStartupMillis")));
        }

        return serverTimeouts;
    }


    private static class ServerDetails {
        JAXRSServerFactoryBean sf;
        String serverId;
        String url;
    }
}
