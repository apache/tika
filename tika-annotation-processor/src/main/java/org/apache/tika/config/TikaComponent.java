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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for Tika components (parsers, detectors, etc.) that enables:
 * <ul>
 *   <li>Automatic SPI file generation (META-INF/services/...)</li>
 *   <li>Name-based component registry for JSON configuration</li>
 * </ul>
 *
 * <p>The annotation processor generates:
 * <ul>
 *   <li>Standard Java SPI files for ServiceLoader</li>
 *   <li>Component index files (META-INF/tika/{type}.idx) for name-based lookup</li>
 * </ul>
 *
 * <p>This annotation is processed at compile time by the annotation processor.
 * The contextKey is recorded in the .idx file for runtime resolution.
 *
 * <p>Example usage:
 * <pre>
 * {@code @TikaComponent}
 * public class PDFParser extends AbstractParser {
 *     // auto-generates name "pdf-parser", included in SPI
 * }
 *
 * {@code @TikaComponent(name = "tesseract-ocr")}
 * public class TesseractOCRParser extends AbstractParser {
 *     // explicit name override, included in SPI
 * }
 *
 * {@code @TikaComponent(spi = false)}
 * public class DWGReadParser extends AbstractParser {
 *     // available by name, but NOT auto-loaded by default-parser
 * }
 *
 * {@code @TikaComponent(contextKey = MetadataFilter.class)}
 * public class MyFilter implements MetadataFilter, AnotherInterface {
 *     // explicit ParseContext key when class implements multiple known interfaces
 * }
 *
 * {@code @TikaComponent(defaultFor = ContentHandlerFactory.class)}
 * public class BasicContentHandlerFactory implements ContentHandlerFactory {
 *     // marks this as the default implementation for ContentHandlerFactory
 * }
 * </pre>
 *
 * @since 3.1.0
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface TikaComponent {

    /**
     * The component name used in JSON configuration. If empty, the name is
     * automatically generated from the class name using kebab-case conversion
     * (e.g., PDFParser becomes "pdf-parser").
     *
     * @return the component name, or empty string for auto-generation
     */
    String name() default "";

    /**
     * Whether this component should be included in SPI files for automatic
     * discovery via ServiceLoader. When false, the component is only available
     * via explicit configuration (not loaded by "default-parser").
     *
     * <p>Use {@code spi = false} for opt-in components that users must explicitly
     * enable in their configuration.
     *
     * @return true to include in SPI (default), false to require explicit config
     */
    boolean spi() default true;

    /**
     * The class to use as the key when adding this component to ParseContext.
     * <p>
     * By default ({@code void.class}), the key is auto-detected:
     * <ul>
     *   <li>If the component implements a known interface (e.g., MetadataFilter),
     *       that interface is used as the key</li>
     *   <li>Otherwise, the component's own class is used as the key</li>
     * </ul>
     * <p>
     * Use this attribute to explicitly specify the key when:
     * <ul>
     *   <li>The component implements multiple known interfaces (ambiguous)</li>
     *   <li>You need a specific interface/class that isn't auto-detected</li>
     * </ul>
     *
     * @return the class to use as ParseContext key, or void.class for auto-detection
     */
    Class<?> contextKey() default void.class;

    /**
     * Marks this component as the default implementation for the specified interface.
     * <p>
     * When set, this component will be used as the default when loading a ParseContext
     * with defaults (via {@code loadParseContextWithDefaults()}) and no explicit
     * configuration is provided for the interface.
     * <p>
     * The specified class should be an interface that this component implements.
     * For example:
     * <pre>
     * {@code @TikaComponent(defaultFor = ContentHandlerFactory.class)}
     * public class BasicContentHandlerFactory implements ContentHandlerFactory {
     *     // This will be instantiated by default when no ContentHandlerFactory is configured
     * }
     * </pre>
     *
     * @return the interface this component is the default for, or void.class if not a default
     */
    Class<?> defaultFor() default void.class;
}
