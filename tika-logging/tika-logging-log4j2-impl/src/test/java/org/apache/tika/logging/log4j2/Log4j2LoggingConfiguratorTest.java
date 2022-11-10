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
package org.apache.tika.logging.log4j2;

import java.util.stream.Stream;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.tika.logging.api.LoggerLevelChangeContext;
import org.apache.tika.logging.api.LoggingConfigurator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class Log4j2LoggingConfiguratorTest {
  @BeforeEach
  void initClass() {
    Configurator.setRootLevel(Level.INFO);
    Configurator.setLevel(LoggingConfigurator.class, Level.WARN);
  }

  @ParameterizedTest
  @MethodSource("loggerLevelArgs")
  void getLoggerLevelReturnsCorrectValue(Level expected, Level ignored, String loggerName) {
    assertEquals(expected, LogManager.getLogger(loggerName).getLevel());
    assertEquals(expected.name(), LoggingConfigurator.getLoggerLevel(loggerName));
  }

  @ParameterizedTest
  @MethodSource("loggerLevelArgs")
  void setLoggerLevel(Level before, Level after, String loggerName) {
    String originalLevel = LoggingConfigurator.setLoggerLevel(loggerName, after.name());
    assertEquals(before.name(), originalLevel);
    assertEquals(after, LogManager.getLogger(loggerName).getLevel());
    assertEquals(after.name(), LoggingConfigurator.getLoggerLevel(loggerName));
  }

  @ParameterizedTest
  @MethodSource("loggerLevelArgs")
  void withLoggerLevel(Level before, Level after, String loggerName) throws Exception {
    try (LoggerLevelChangeContext ignored = LoggingConfigurator.withLoggerLevel(loggerName, after.name())) {
      assertEquals(after, LogManager.getLogger(loggerName).getLevel());
    }
    assertEquals(before, LogManager.getLogger(loggerName).getLevel());
  }

  static Stream<Arguments> loggerLevelArgs() {
    return Stream.of(
        arguments(Level.INFO, Level.ERROR, LogManager.ROOT_LOGGER_NAME),
        arguments(Level.WARN, Level.ERROR, LoggingConfigurator.class.getCanonicalName()),
        arguments(Level.INFO, Level.ERROR, Log4j2LoggingConfigurator.class.getCanonicalName())
    );
  }
}
