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

import com.sun.grizzly.http.SelectorThread;
import com.sun.jersey.api.container.grizzly.GrizzlyWebContainerFactory;
import org.apache.commons.cli.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

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
    try {
      TikaServerCli cli = new TikaServerCli();

      Map<String, String> params = new HashMap<String, String>();

      params.put("com.sun.jersey.config.property.packages", "org.apache.tika.server");

      Options options = cli.getOptions();

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

      String baseUri = "http://localhost/";
      URI buildUri = UriBuilder.fromUri(baseUri).port(port).build();
      SelectorThread threadSelector = GrizzlyWebContainerFactory.create(buildUri, params);

      logger.info("Started at " + buildUri);
    } catch (Exception ex) {
      logger.fatal("Can't start", ex);
      System.exit(-1);
    }
  }
}
