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
package org.apache.tika.parser;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;

public class CompositeParserTest extends TestCase {

    public void testFindDuplicateParsers() {
        Parser a = new EmptyParser() {
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return Collections.singleton(MediaType.TEXT_PLAIN);
            }
        };
        Parser b = new EmptyParser() {
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return Collections.singleton(MediaType.TEXT_PLAIN);
            }
        };
        Parser c = new EmptyParser() {
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return Collections.singleton(MediaType.OCTET_STREAM);
            }
        };

        CompositeParser composite = new CompositeParser(
                MediaTypeRegistry.getDefaultRegistry(), a, b, c);
        Map<MediaType, List<Parser>> duplicates =
            composite.findDuplicateParsers(new ParseContext());
        assertEquals(1, duplicates.size());
        List<Parser> parsers = duplicates.get(MediaType.TEXT_PLAIN);
        assertNotNull(parsers);
        assertEquals(2, parsers.size());
        assertEquals(a, parsers.get(0));
        assertEquals(b, parsers.get(1));
    }

}
