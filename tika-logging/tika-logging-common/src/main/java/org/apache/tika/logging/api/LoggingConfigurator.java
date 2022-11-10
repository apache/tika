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
package org.apache.tika.logging.api;

import org.apache.tika.logging.spi.LoggingConfiguratorProvider;

public class LoggingConfigurator {
  private static final LoggingConfiguratorProvider INSTANCE = LoggingConfiguratorProvider.Holder.INSTANCE;

  private static final String ROOT_LOGGER_NAME = INSTANCE.getRootLoggerName();

  private LoggingConfigurator() {
  }

  public static String getLoggerLevel(String loggerName) {
    return INSTANCE.getLoggerLevel(loggerName);
  }

  public static String getLoggerLevel(Class<?> clazz) {
    return getLoggerLevel(clazz.getCanonicalName());
  }

  public static String getRootLoggerLevel() {
    return getLoggerLevel(ROOT_LOGGER_NAME);
  }

  public static String setLoggerLevel(String loggerName, String level) {
    String originalLevel = INSTANCE.getLoggerLevel(loggerName);
    INSTANCE.setLoggerLevel(loggerName, level);
    return originalLevel;
  }

  public static String setLoggerLevel(Class<?> clazz, String level) {
    return setLoggerLevel(clazz.getCanonicalName(), level);
  }

  public static String setRootLoggerLevel(String level) {
    return setLoggerLevel(ROOT_LOGGER_NAME, level);
  }

  public static LoggerLevelChangeContext withLoggerLevel(String loggerName, String level) {
    return new LoggerLevelChangeContext(loggerName, level);
  }

  public static LoggerLevelChangeContext withLoggerLevel(Class<?> clazz, String level) {
    return new LoggerLevelChangeContext(clazz.getCanonicalName(), level);
  }

  public static LoggerLevelChangeContext withRootLoggerLevel(String level) {
    return new LoggerLevelChangeContext(ROOT_LOGGER_NAME, level);
  }
}
