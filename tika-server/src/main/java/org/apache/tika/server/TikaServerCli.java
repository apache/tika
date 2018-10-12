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

package org.apache.tika.server;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.jaxrs.JAXRSBindingFactory;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.lifecycle.ResourceProvider;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.cxf.rs.security.cors.CrossOriginResourceSharingFilter;
import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.parser.DigestingParser;
import org.apache.tika.parser.utils.BouncyCastleDigester;
import org.apache.tika.parser.utils.CommonsDigester;
import org.apache.tika.server.resource.DetectorResource;
import org.apache.tika.server.resource.LanguageResource;
import org.apache.tika.server.resource.MetadataResource;
import org.apache.tika.server.resource.RecursiveMetadataResource;
import org.apache.tika.server.resource.TikaDetectors;
import org.apache.tika.server.resource.TikaMimeTypes;
import org.apache.tika.server.resource.TikaParsers;
import org.apache.tika.server.resource.TikaResource;
import org.apache.tika.server.resource.TikaVersion;
import org.apache.tika.server.resource.TikaWelcome;
import org.apache.tika.server.resource.TranslateResource;
import org.apache.tika.server.resource.UnpackerResource;
import org.apache.tika.server.writer.CSVMessageBodyWriter;
import org.apache.tika.server.writer.JSONMessageBodyWriter;
import org.apache.tika.server.writer.MetadataListMessageBodyWriter;
import org.apache.tika.server.writer.TarWriter;
import org.apache.tika.server.writer.TextMessageBodyWriter;
import org.apache.tika.server.writer.XMPMessageBodyWriter;
import org.apache.tika.server.writer.ZipWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TikaServerCli {


    //used in spawn-child mode
    private static final long DEFAULT_MAX_FILES = 100000;


    public static final int DEFAULT_PORT = 9998;
    private static final int DEFAULT_DIGEST_MARK_LIMIT = 20*1024*1024;
    public static final String DEFAULT_HOST = "localhost";
    public static final Set<String> LOG_LEVELS = new HashSet<>(Arrays.asList("debug", "info"));
    private static final Logger LOG = LoggerFactory.getLogger(TikaServerCli.class);

    private static final String FILE_URL_WARNING =
            "WARNING: You have chosen to run tika-server with fileUrl enabled.\n"+
            "Whoever has access to your service now has the same read permissions\n"+
            "as tika-server. Users could request and receive a sensitive file from your\n" +
            "drive or a webpage from your intranet.  See CVE-2015-3271.\n"+
            "Please make sure you know what you are doing.";

    private static final List<String> ONLY_IN_SPAWN_CHILD_MODE =
            Arrays.asList(new String[] { "taskTimeoutMillis", "taskPulseMillis",
            "pingTimeoutMillis", "pingPulseMillis", "maxFiles", "javaHome"});

    private static Options getOptions() {
        Options options = new Options();
        options.addOption("C", "cors", true, "origin allowed to make CORS requests (default=NONE)\nall allowed if \"all\"");
        options.addOption("h", "host", true, "host name (default = " + DEFAULT_HOST + ", use * for all)");
        options.addOption("p", "port", true, "listen port (default = " + DEFAULT_PORT + ')');
        options.addOption("c", "config", true, "Tika Configuration file to override default config with.");
        options.addOption("d", "digest", true, "include digest in metadata, e.g. md5,sha1:32,sha256");
        options.addOption("dml", "digestMarkLimit", true, "max number of bytes to mark on stream for digest");
        options.addOption("l", "log", true, "request URI log level ('debug' or 'info')");
        options.addOption("s", "includeStack", false, "whether or not to return a stack trace\nif there is an exception during 'parse'");
        options.addOption("?", "help", false, "this help message");
        options.addOption("enableUnsecureFeatures", false, "this is required to enable fileUrl.");
        options.addOption("enableFileUrl", false, "allows user to pass in fileUrl instead of InputStream.");
        options.addOption("spawnChild", false, "whether or not to spawn a child process for robustness");
        options.addOption("taskTimeoutMillis", true, "Only in spawn child mode: how long to wait for a task (e.g. parse) to finish");
        options.addOption("taskPulseMillis", true, "Only in spawn child mode: how often to check if a task has timed out.");
        options.addOption("pingTimeoutMillis", true, "Only in spawn child mode: how long to wait to wait for a ping and/or ping response.");
        options.addOption("pingPulseMillis", true, "Only in spawn child mode: how often to check if a ping has timed out.");

        options.addOption("maxFiles", true, "Only in spawn child mode: shutdown server after this many files -- use only in 'spawnChild' mode");
        options.addOption("javaHome", true, "Override system property JAVA_HOME for calling java for the child process");
        options.addOption("child", false, "this process is a child process -- EXPERT -- " +
                "should normally only be invoked by parent process");
        return options;
    }

    public static void main(String[] args) {
        LOG.info("Starting {} server", new Tika());
        try {
            execute(args);
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("Can't start: ", e);
            System.exit(-1);
        }
    }

    private static void execute(String[] args) throws Exception {
        Options options = getOptions();

        CommandLineParser cliParser = new GnuParser();

        //need to strip out -J (child jvm opts) from this parse
        //they'll be processed correctly in args in the watch dog
        //and they won't be needed in legacy.
        CommandLine line = cliParser.parse(options, stripChildArgs(args));
        if (line.hasOption("spawnChild")) {
            TikaServerWatchDog watchDog = new TikaServerWatchDog();
            watchDog.execute(args, configureServerTimeouts(line));
        } else {
            if (! line.hasOption("child")) {
                //make sure the user didn't misunderstand the options
                for (String childOnly : ONLY_IN_SPAWN_CHILD_MODE) {
                    if (line.hasOption(childOnly)) {
                        System.err.println("The option '" + childOnly +
                                "' can only be used with '-spawnChild'");
                        usage(options);
                    }
                }
            }
            executeLegacy(line, options);
        }
    }

    private static String[] stripChildArgs(String[] args) {
        List<String> ret = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (! args[i].startsWith("-J")) {
                ret.add(args[i]);
            }
        }
        return ret.toArray(new String[ret.size()]);
    }

    private static void executeLegacy(CommandLine line, Options options) throws Exception {
            if (line.hasOption("help")) {
                usage(options);
            }

            String host = DEFAULT_HOST;

            if (line.hasOption("host")) {
                host = line.getOptionValue("host");
                if ("*".equals(host)) {
                    host = "0.0.0.0";
                }
            }

            int port = DEFAULT_PORT;

            if (line.hasOption("port")) {
                port = Integer.valueOf(line.getOptionValue("port"));
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
                List<String> origins = new ArrayList<String>();
                if (!url.equals("*")) origins.add(url);         // Empty list allows all origins.
                corsFilter.setAllowOrigins(origins);
            }
            
            // The Tika Configuration to use throughout            
            TikaConfig tika;
            
            if (line.hasOption("config")){
                String configFilePath = line.getOptionValue("config");
                LOG.info("Using custom config: {}", configFilePath);
                tika = new TikaConfig(configFilePath);
            } else{
                tika = TikaConfig.getDefaultConfig();
            }

            DigestingParser.Digester digester = null;
            if (line.hasOption("digest")){
                int digestMarkLimit = DEFAULT_DIGEST_MARK_LIMIT;
                if (line.hasOption("dml")) {
                    String dmlS = line.getOptionValue("dml");
                    try {
                        digestMarkLimit = Integer.parseInt(dmlS);
                    } catch (NumberFormatException e) {
                        throw new RuntimeException("Must have parseable int after digestMarkLimit(dml): "+dmlS);
                    }
                }
                try {
                    digester = new CommonsDigester(digestMarkLimit, line.getOptionValue("digest"));
                } catch (IllegalArgumentException commonsException) {
                    try {
                        digester = new BouncyCastleDigester(digestMarkLimit, line.getOptionValue("digest"));
                    } catch (IllegalArgumentException bcException) {
                        throw new IllegalArgumentException("Tried both CommonsDigester ("+commonsException.getMessage()+
                                ") and BouncyCastleDigester ("+bcException.getMessage()+")", bcException);
                    }
                }
            }

            if (line.hasOption("enableFileUrl") &&
                    !line.hasOption("enableUnsecureFeatures")) {
                System.err.println("If you want to enable fileUrl, you must also acknowledge the security risks\n"+
                "by including --enableUnsecureFeatures.  See CVE-2015-3271.");
                System.exit(-1);
            }
            InputStreamFactory inputStreamFactory = null;
            if (line.hasOption("enableFileUrl") &&
                    line.hasOption("enableUnsecureFeatures")) {
                inputStreamFactory = new URLEnabledInputStreamFactory();
                System.out.println(FILE_URL_WARNING);
            } else {
                inputStreamFactory = new DefaultInputStreamFactory();
            }

            ServerStatus serverStatus = new ServerStatus();
            //if this is a child process
            if (line.hasOption("child")) {
                long maxFiles = DEFAULT_MAX_FILES;
                if (line.hasOption("maxFiles")) {
                    maxFiles = Long.parseLong(line.getOptionValue("maxFiles"));
                }

                ServerTimeouts serverTimeouts = configureServerTimeouts(line);
                Thread serverThread =
                new Thread(new ServerStatusWatcher(serverStatus, System.in,
                        System.out, maxFiles, serverTimeouts));
                serverThread.start();
                System.setIn(new ByteArrayInputStream(new byte[0]));
                System.setOut(System.err);
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
            List<ResourceProvider> rAllProviders = new ArrayList<>(rCoreProviders);
            rAllProviders.add(new SingletonResourceProvider(new TikaWelcome(rCoreProviders)));
            sf.setResourceProviders(rAllProviders);

            List<Object> providers = new ArrayList<>();
            providers.add(new TarWriter());
            providers.add(new ZipWriter());
            providers.add(new CSVMessageBodyWriter());
            providers.add(new MetadataListMessageBodyWriter());
            providers.add(new JSONMessageBodyWriter());
            providers.add(new XMPMessageBodyWriter());
            providers.add(new TextMessageBodyWriter());
            providers.add(new TikaServerParseExceptionMapper(returnStackTrace));
            if (logFilter != null) {
                providers.add(logFilter);
            }
            if (corsFilter != null) {
                providers.add(corsFilter);
            }
            sf.setProviders(providers);


            String url = "http://" + host + ":" + port + "/";
            sf.setAddress(url);
            BindingFactoryManager manager = sf.getBus().getExtension(BindingFactoryManager.class);
            JAXRSBindingFactory factory = new JAXRSBindingFactory();
            factory.setBus(sf.getBus());
            manager.registerBindingFactory(JAXRSBindingFactory.JAXRS_BINDING_ID, factory);
            sf.create();
            LOG.info("Started Apache Tika server at {}", url);
    }

    private static void usage(Options options) {
        HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.printHelp("tikaserver", options);
        System.exit(-1);
    }

    private static ServerTimeouts configureServerTimeouts(CommandLine line) {
        ServerTimeouts serverTimeouts = new ServerTimeouts();
        /*TODO -- add these in
        if (line.hasOption("childProcessStartupMillis")) {
            serverTimeouts.setChildProcessStartupMillis(
                    Long.parseLong(line.getOptionValue("childProcessStartupMillis")));
        }
        if (line.hasOption("childProcessShutdownMillis")) {
            serverTimeouts.setChildProcessShutdownMillis(
                    Long.parseLong(line.getOptionValue("childProcesShutdownMillis")));
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

        return serverTimeouts;
    }

}
