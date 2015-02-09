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

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.apache.tika.mime.MediaType;
import org.junit.Test;

public class ParserDecoratorTest {
    @Test
    public void withAndWithoutTypes() {
        Set<MediaType> onlyTxt = Collections.singleton(MediaType.TEXT_PLAIN);
        Set<MediaType> onlyOct = Collections.singleton(MediaType.OCTET_STREAM);
        Set<MediaType> both = new HashSet<MediaType>();
        both.addAll(onlyOct);
        both.addAll(onlyTxt);
        
        Parser p;
        Set<MediaType> types;
        ParseContext context = new ParseContext();

        
        // With a parser of no types, get the decorated type
        p = ParserDecorator.withTypes(EmptyParser.INSTANCE, onlyTxt);
        types = p.getSupportedTypes(context);
        assertEquals(1, types.size());
        assertEquals(types.toString(), true, types.contains(MediaType.TEXT_PLAIN));
        
        // With a parser with other types, still just the decorated type
        p = ParserDecorator.withTypes(
                new DummyParser(onlyOct, new HashMap<String,String>(), ""), onlyTxt);
        types = p.getSupportedTypes(context);
        assertEquals(1, types.size());
        assertEquals(types.toString(), true, types.contains(MediaType.TEXT_PLAIN));
        
        
        // Exclude will remove if there
        p = ParserDecorator.withoutTypes(EmptyParser.INSTANCE, onlyTxt);
        types = p.getSupportedTypes(context);
        assertEquals(0, types.size());
        
        p = ParserDecorator.withoutTypes(
                new DummyParser(onlyOct, new HashMap<String,String>(), ""), onlyTxt);
        types = p.getSupportedTypes(context);
        assertEquals(1, types.size());
        assertEquals(types.toString(), true, types.contains(MediaType.OCTET_STREAM));
        
        p = ParserDecorator.withoutTypes(
                new DummyParser(both, new HashMap<String,String>(), ""), onlyTxt);
        types = p.getSupportedTypes(context);
        assertEquals(1, types.size());
        assertEquals(types.toString(), true, types.contains(MediaType.OCTET_STREAM));
    }
}
