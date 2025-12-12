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
package org.apache.tika.config;

/**
 * Marker interface indicating that a component reads its own configuration
 * from {@link ConfigContainer} inside the {@link org.apache.tika.parser.ParseContext} at runtime.
 * <p>
 * Components implementing this interface will NOT be automatically resolved
 * by ParseContextUtils. Instead, the JSON configuration will remain in
 * ConfigContainer, and the component is responsible for reading and applying
 * its own configuration during execution.
 * <p>
 * This is typically used by parsers and other components that need fine-grained
 * control over how their configuration is loaded and merged with defaults.
 * <p>
 * Example:
 * <pre>
 * {@literal @}TikaComponent
 * public class PDFParser implements Parser, SelfConfiguring {
 *
 *     private final PDFParserConfig defaultConfig;
 *
 *     public void parse(..., ParseContext context) {
 *         // Component reads its own config from ConfigContainer
 *         PDFParserConfig config = ParseContextConfig.getConfig(
 *             context, "pdf-parser", PDFParserConfig.class, defaultConfig);
 *         // use config...
 *     }
 * }
 * </pre>
 * <p>
 * Components that do NOT implement this interface will have their configuration
 * automatically deserialized and added to ParseContext by ParseContextUtils.
 *
 * @since Apache Tika 4.0
 * @see ConfigContainer
 * @see ParseContextConfig
 */
public interface SelfConfiguring {
    // Marker interface - no methods
}
