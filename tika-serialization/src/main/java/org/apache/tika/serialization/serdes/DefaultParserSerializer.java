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
package org.apache.tika.serialization.serdes;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.Parser;

/**
 * Serializer for DefaultParser that outputs exclusions.
 * <p>
 * Outputs:
 * <pre>
 * "default-parser"  // if no exclusions
 * { "default-parser": { "exclude": ["html-parser"] } }  // with exclusions
 * </pre>
 */
public class DefaultParserSerializer extends SpiCompositeSerializer<DefaultParser> {

    public DefaultParserSerializer() {
        super("default-parser");
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Set<Class<?>> getExcludedClasses(DefaultParser value) {
        Collection<Class<? extends Parser>> excluded = value.getExcludedClasses();
        if (excluded == null || excluded.isEmpty()) {
            return null;
        }
        return new HashSet<>((Collection<Class<?>>) (Collection<?>) excluded);
    }
}
