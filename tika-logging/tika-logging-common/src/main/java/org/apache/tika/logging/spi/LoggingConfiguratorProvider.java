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
package org.apache.tika.logging.spi;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.tika.config.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface LoggingConfiguratorProvider {
  String getRootLoggerName();

  String getLoggerLevel(String loggerName);

  void setLoggerLevel(String loggerName, String level);

  class Holder {
    private static final Logger LOG = LoggerFactory.getLogger(Holder.class);

    private static final ServiceLoader LOADER = new ServiceLoader();

    public static final LoggingConfiguratorProvider INSTANCE;

    static {
      List<LoggingConfiguratorProvider> configurators = LOADER.loadStaticServiceProviders(LoggingConfiguratorProvider.class);
      if (configurators.isEmpty()) {
        INSTANCE = NoOpLoggingConfiguratorProvider.INSTANCE;
      } else {
        INSTANCE = configurators.get(0);
      }

      if (configurators.size() > 1) {
        LOG.warn("Found several {} instances: {}",
            LoggingConfiguratorProvider.class.getCanonicalName(),
            configurators.stream().map(c -> c.getClass().getCanonicalName()).collect(Collectors.joining(", ")));
      }
    }
  }

  class NoOpLoggingConfiguratorProvider implements LoggingConfiguratorProvider {
    public static final LoggingConfiguratorProvider INSTANCE = new NoOpLoggingConfiguratorProvider();

    private NoOpLoggingConfiguratorProvider() {
    }

    @Override
    public String getRootLoggerName() {
      return "";
    }

    @Override
    public String getLoggerLevel(String loggerName) {
      return "INFO";
    }

    @Override
    public void setLoggerLevel(String loggerName, String level) {
    }
  }
}
