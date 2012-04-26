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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSBindingFactory;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.tika.Tika;

public class TikaServerCli {
  private static final Log logger = LogFactory.getLog(TikaServerCli.class);
  public static final int DEFAULT_PORT = 9998;

  private static Options getOptions() {
    Options options = new Options();
    options.addOption("p", "port", true, "listen port (default = "+DEFAULT_PORT+ ')');
    options.addOption("h", "help", false, "this help message");

    return options;
  }

  public static void main(String[] args) {
    Properties properties = new Properties();
    try {
      properties.load(ClassLoader.getSystemResourceAsStream("tikaserver-version.properties"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    logger.info("Starting Tikaserver "+properties.getProperty("tikaserver.version"));
    logger.info("Starting Tika Server " + new Tika().toString());

    try {
      Options options = getOptions();

      CommandLineParser cliParser = new GnuParser();
      CommandLine line = cliParser.parse(options, args);

      int port = DEFAULT_PORT;

      if (line.hasOption("port")) {
        port = Integer.valueOf(line.getOptionValue("port"));
      }
      if (line.hasOption("help")) {
        HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.printHelp("tikaserver", options);
        System.exit(-1);
      }

      JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
      sf.setResourceClasses(MetadataResource.class, TikaResource.class, UnpackerResource.class, TikaVersion.class);

      List providers = new ArrayList();
      providers.add(new TarWriter());
      providers.add(new ZipWriter());
      providers.add(new TikaExceptionMapper());
      providers.add(new SingletonResourceProvider(new MetadataResource()));
      providers.add(new SingletonResourceProvider(new TikaResource()));
      providers.add(new SingletonResourceProvider(new UnpackerResource()));
      providers.add(new SingletonResourceProvider(new TikaVersion()));
      sf.setProviders(providers);
      sf.setAddress("http://localhost:" + TikaServerCli.DEFAULT_PORT + "/");
      BindingFactoryManager manager = sf.getBus().getExtension(
				BindingFactoryManager.class);
      JAXRSBindingFactory factory = new JAXRSBindingFactory();
      factory.setBus(sf.getBus());
      manager.registerBindingFactory(JAXRSBindingFactory.JAXRS_BINDING_ID,
				factory);
      Server server = sf.create();
      logger.info("Started");
    } catch (Exception ex) {
      logger.fatal("Can't start", ex);
      System.exit(-1);
    }
  }
}
