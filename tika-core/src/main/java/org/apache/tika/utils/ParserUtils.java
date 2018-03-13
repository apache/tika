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
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;

/**
 * Helper util methods for Parsers themselves.
 */
public class ParserUtils {
    public final static Property EMBEDDED_PARSER =
            Property.internalText(TikaCoreProperties.TIKA_META_EXCEPTION_PREFIX + "embedded_parser");
    public final static Property EMBEDDED_EXCEPTION =
            Property.internalText(TikaCoreProperties.TIKA_META_EXCEPTION_PREFIX + "embedded_exception");
    
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

    /**
     * Records details of the {@link Parser} used to the {@link Metadata},
     *  typically wanted where multiple parsers could be picked between
     *  or used.
     */
    public static void recordParserDetails(Parser parser, Metadata metadata) {
        metadata.add("X-Parsed-By", getParserClassname(parser));
    }

    /**
     * Records details of a {@link Parser}'s failure to the
     *  {@link Metadata}, so you can check what went wrong even if the
     *  {@link Exception} wasn't immediately thrown (eg when several different
     *  Parsers are used)
     */
    public static void recordParserFailure(Parser parser, Exception failure, 
                                           Metadata metadata) {
        String trace = ExceptionUtils.getStackTrace(failure);
        metadata.add(EMBEDDED_EXCEPTION, trace);
        metadata.add(EMBEDDED_PARSER, getParserClassname(parser));
    }
}
