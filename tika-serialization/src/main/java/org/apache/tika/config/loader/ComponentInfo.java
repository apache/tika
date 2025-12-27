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
package org.apache.tika.config.loader;

/**
 * Information about a registered Tika component.
 *
 * @param componentClass the component's class
 * @param selfConfiguring whether the component implements SelfConfiguring
 *                        (reads its own config from ParseContext's jsonConfigs)
 * @param contextKey the class to use as the key when adding to ParseContext,
 *                   or null to auto-detect based on known interfaces
 */
public record ComponentInfo(
        Class<?> componentClass,
        boolean selfConfiguring,
        Class<?> contextKey
) {
    /**
     * Creates a ComponentInfo with no explicit context key (auto-detect).
     */
    public ComponentInfo(Class<?> componentClass, boolean selfConfiguring) {
        this(componentClass, selfConfiguring, null);
    }
}
