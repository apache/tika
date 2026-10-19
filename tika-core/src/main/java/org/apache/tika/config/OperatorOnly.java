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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a configuration property that only operator-authored configuration (the config
 * file, presets) may set. Per-request JSON ({@link JsonConfig#trusted()} false, which is
 * what a tika-server {@code /config} part or a pipes {@code FetchEmitTuple} carries) that
 * names the property is refused before any setter runs.
 * <p>
 * Put it on the setter (or field, or getter) of anything whose value reaches the host
 * rather than the document: a path or executable the parser runs, a class name it loads,
 * a URL or credential it sends data to, a pool size or memory ceiling an operator sized.
 * <pre>
 * {@literal @}OperatorOnly
 * public void setTesseractPath(String tesseractPath) { ... }
 * </pre>
 * Enforced by {@code ConfigDeserializer} (tika-serialization) for every component that
 * resolves its config through {@link ParseContextConfig#getConfig}. The check is by JSON
 * key, so a nested object's properties are checked against the nested type too.
 *
 * @since Apache Tika 4.2
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface OperatorOnly {
}
