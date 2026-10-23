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
package org.apache.tika.inference.locator;

/**
 * The embedded document a chunk was derived from, for a chunk that was lifted into its
 * parent's {@code tk:chunks}: a page render or an inline image whose vector belongs to the
 * document it is part of.
 */
public class EmbeddedLocator {

    private final String idPath;
    private final String name;

    /**
     * @param idPath the child's {@code tk:embedded-id-path}
     * @param name   the child's resource name, or null if it had none
     */
    public EmbeddedLocator(String idPath, String name) {
        this.idPath = idPath;
        this.name = name;
    }

    public String getIdPath() {
        return idPath;
    }

    public String getName() {
        return name;
    }
}
