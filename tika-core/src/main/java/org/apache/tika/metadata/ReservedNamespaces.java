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
package org.apache.tika.metadata;

/**
 * Classifies metadata keys by who is allowed to assert them. The Tika-native namespace
 * ({@code tk:}) is a trust boundary: only Tika, via a registered {@link Property}, may write it.
 * A key name derived from file content (a blind scrape) that lands in this namespace is a forgery
 * and must be refused rather than honored.
 *
 * @since Apache Tika 4.0.0
 */
public final class ReservedNamespaces {

    private ReservedNamespaces() {
    }

    /**
     * Is {@code name} in the Tika-native namespace? True for the current {@code tk:} prefix and
     * the pre-4.0.0 {@code X-TIKA:} prefix — the legacy prefix stays reserved so a crafted file
     * cannot forge it during the 4.x window even though Tika no longer writes it.
     *
     * @param name a metadata key name (may be {@code null})
     * @return {@code true} if the key belongs to the reserved Tika-native namespace
     */
    public static boolean isTikaNative(String name) {
        return name != null
                && (name.startsWith(TikaCoreProperties.TIKA_META_PREFIX)
                        || name.startsWith(TikaCoreProperties.LEGACY_TIKA_META_PREFIX));
    }
}
