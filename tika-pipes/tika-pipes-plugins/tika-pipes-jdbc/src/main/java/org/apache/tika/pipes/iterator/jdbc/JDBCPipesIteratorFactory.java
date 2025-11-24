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
package org.apache.tika.pipes.iterator.jdbc;

import java.io.IOException;

import org.pf4j.Extension;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.pipesiterator.PipesIteratorFactory;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * Factory for creating JDBC pipes iterators.
 *
 * <p>Example JSON configuration:
 * <pre>
 * "pipes-iterator": {
 *   "jdbc-pipes-iterator": {
 *     "connection": "jdbc:postgresql://localhost/mydb",
 *     "select": "select id, path from documents",
 *     "fetchKeyColumn": "path",
 *     "idColumn": "id",
 *     "baseConfig": {
 *       "fetcherId": "my-fetcher",
 *       "emitterId": "my-emitter"
 *     }
 *   }
 * }
 * </pre>
 */
@Extension
public class JDBCPipesIteratorFactory implements PipesIteratorFactory {

    public static final String NAME = "jdbc-pipes-iterator";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public JDBCPipesIterator buildExtension(ExtensionConfig extensionConfig) throws IOException, TikaConfigException {
        return JDBCPipesIterator.build(extensionConfig);
    }
}
