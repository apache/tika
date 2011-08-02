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

import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.jersey.spi.container.servlet.ServletContainer;
import org.apache.commons.cli.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.io.IOException;
import java.util.Properties;

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

      Server server = new Server(port);
      ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
      context.setContextPath("/");
      server.setHandler(context);

      context.addServlet(new ServletHolder(new ServletContainer(new PackagesResourceConfig("org.apache.tika.server"))), "/*");

      server.start();

      logger.info("Started");

      server.join();
    } catch (Exception ex) {
      logger.fatal("Can't start", ex);
      System.exit(-1);
    }
  }
}
