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
package org.apache.tika.utils;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;

/**
 * Helper util methods for Parsers themselves.
 */
public class ParserUtils {
    /**
     * Does a deep clone of a Metadata object.
     */
    public static Metadata cloneMetadata(Metadata m) {
        Metadata clone = new Metadata();
        
        for (String n : m.names()){
            if (! m.isMultiValued(n)) {
                clone.set(n, m.get(n));
            } else {
                String[] vals = m.getValues(n);
                for (int i = 0; i < vals.length; i++) {
                    clone.add(n, vals[i]);
                }
            }
        }
        return clone;
    }

    /**
     * Identifies the real class name of the {@link Parser}, unwrapping
     *  any {@link ParserDecorator} decorations on top of it.
     */
    public static String getParserClassname(Parser parser) {
        if (parser instanceof ParserDecorator){
            return ((ParserDecorator) parser).getWrappedParser().getClass().getName();
        } else {
            return parser.getClass().getName();
        }
    }
}
