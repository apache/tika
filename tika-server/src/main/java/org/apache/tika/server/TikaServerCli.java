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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.jaxrs.JAXRSBindingFactory;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.lifecycle.ResourceProvider;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.cxf.rs.security.cors.CrossOriginResourceSharingFilter;
import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.server.resource.DetectorResource;
import org.apache.tika.server.resource.MetadataResource;
import org.apache.tika.server.resource.RecursiveMetadataResource;
import org.apache.tika.server.writer.TarWriter;
import org.apache.tika.server.resource.TikaDetectors;
import org.apache.tika.server.resource.TikaMimeTypes;
import org.apache.tika.server.resource.TikaParsers;
import org.apache.tika.server.resource.TikaResource;
import org.apache.tika.server.resource.TikaVersion;
import org.apache.tika.server.resource.TikaWelcome;
import org.apache.tika.server.resource.UnpackerResource;
import org.apache.tika.server.writer.CSVMessageBodyWriter;
import org.apache.tika.server.writer.JSONMessageBodyWriter;
import org.apache.tika.server.writer.MetadataListMessageBodyWriter;
import org.apache.tika.server.writer.TextMessageBodyWriter;
import org.apache.tika.server.writer.XMPMessageBodyWriter;
import org.apache.tika.server.writer.ZipWriter;

public class TikaServerCli {
    public static final int DEFAULT_PORT = 9998;
    public static final String DEFAULT_HOST = "localhost";
    public static final Set<String> LOG_LEVELS =
            new HashSet<String>(Arrays.asList("debug", "info"));
    private static final Log logger = LogFactory.getLog(TikaServerCli.class);

    private static Options getOptions() {
        Options options = new Options();
        options.addOption("C", "cors", true, "origin allowed to make CORS requests (default=NONE)\nall allowed if \"all\"");
        options.addOption("h", "host", true, "host name (default = " + DEFAULT_HOST + ')');
        options.addOption("p", "port", true, "listen port (default = " + DEFAULT_PORT + ')');
        options.addOption("l", "log", true, "request URI log level ('debug' or 'info')");
        options.addOption("s", "includeStack", false, "whether or not to return a stack trace\nif there is an exception during 'parse'");
        options.addOption("?", "help", false, "this help message");

        return options;
    }

    public static void main(String[] args) {

        logger.info("Starting " + new Tika().toString() + " server");

        try {
            Options options = getOptions();

            CommandLineParser cliParser = new GnuParser();
            CommandLine line = cliParser.parse(options, args);

            if (line.hasOption("help")) {
                HelpFormatter helpFormatter = new HelpFormatter();
                helpFormatter.printHelp("tikaserver", options);
                System.exit(-1);
            }

            String host = DEFAULT_HOST;

            if (line.hasOption("host")) {
                host = line.getOptionValue("host");
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
                    logger.info("Unsupported request URI log level: " + logLevel);
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
            TikaConfig tika = TikaConfig.getDefaultConfig();

            JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();

            List<ResourceProvider> rCoreProviders = new ArrayList<ResourceProvider>();
            rCoreProviders.add(new SingletonResourceProvider(new MetadataResource(tika)));
            rCoreProviders.add(new SingletonResourceProvider(new RecursiveMetadataResource(tika)));
            rCoreProviders.add(new SingletonResourceProvider(new DetectorResource(tika)));
            rCoreProviders.add(new SingletonResourceProvider(new TikaResource(tika)));
            rCoreProviders.add(new SingletonResourceProvider(new UnpackerResource(tika)));
            rCoreProviders.add(new SingletonResourceProvider(new TikaMimeTypes(tika)));
            rCoreProviders.add(new SingletonResourceProvider(new TikaDetectors(tika)));
            rCoreProviders.add(new SingletonResourceProvider(new TikaParsers(tika)));
            rCoreProviders.add(new SingletonResourceProvider(new TikaVersion(tika)));
            List<ResourceProvider> rAllProviders = new ArrayList<ResourceProvider>(rCoreProviders);
            rAllProviders.add(new SingletonResourceProvider(new TikaWelcome(tika, rCoreProviders)));
            sf.setResourceProviders(rAllProviders);

            List<Object> providers = new ArrayList<Object>();
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

            sf.setAddress("http://" + host + ":" + port + "/");
            BindingFactoryManager manager = sf.getBus().getExtension(
                    BindingFactoryManager.class);
            JAXRSBindingFactory factory = new JAXRSBindingFactory();
            factory.setBus(sf.getBus());
            manager.registerBindingFactory(JAXRSBindingFactory.JAXRS_BINDING_ID,
                    factory);
            sf.create();
            logger.info("Started");
        } catch (Exception ex) {
            logger.fatal("Can't start", ex);
            System.exit(-1);
        }
    }
}
