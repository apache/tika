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
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link SelfConfiguring} component (inherited by subclasses) that reads a
 * {@code parse-context} block under its own name through {@link ParseContextConfig#getConfig}.
 * <p>
 * Every parser, detector, renderer and translator is self-configuring, but most never read
 * such a block. A {@code parse-context} entry naming a self-configuring component without
 * this annotation (in a tika-server request, a gRPC {@code parse_context_json}, a pipes
 * {@code FetchEmitTuple}, a preset or the config file's {@code parse-context}) has no effect
 * and is logged as a warning; Tika 5.0 will refuse it.
 * <p>
 * A component that reads its block must carry this annotation. Reading a block for a
 * component without it fails an assertion, so a missing annotation surfaces in tests run
 * with {@code -ea}.
 *
 * @since Apache Tika 4.3
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RequestConfigurable {
}
