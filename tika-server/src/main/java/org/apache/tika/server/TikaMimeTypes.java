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
package org.apache.tika.server;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.Parser;
import org.eclipse.jetty.util.ajax.JSON;

/*
 * TODO Reduce duplication between the two methods, by
 * returning structured info that gets encoded two ways
 */
@Path("/mime-types")
public class TikaMimeTypes {
    private TikaConfig tika;
    public TikaMimeTypes(TikaConfig tika) {
        this.tika = tika;
    }
    
    @GET
    @Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    public String getMimeTypesJSON() {
        Map<String,Object> details = new HashMap<String, Object>();
        
        MediaTypeRegistry registry = tika.getMediaTypeRegistry();
        Map<MediaType, Parser> parsers = ((CompositeParser)tika.getParser()).getParsers();

        for (MediaType type : registry.getTypes()) {
            Map<String,Object> typeDets = new HashMap<String, Object>();

            typeDets.put("alias", registry.getAliases(type));
            MediaType supertype = registry.getSupertype(type);
            if (supertype != null && !MediaType.OCTET_STREAM.equals(supertype)) {
                typeDets.put("supertype", supertype);
            }
            Parser p = parsers.get(type);
            if (p != null) {
                if (p instanceof CompositeParser) {
                    p = ((CompositeParser)p).getParsers().get(type);
                }
                typeDets.put("parser", p.getClass().getName());
            }

            details.put(type.toString(), typeDets);
        }
        
        return JSON.toString(details);
    }
    
    @GET
    @Produces("text/plain")
    public String getMimeTypesPlain() {
        StringBuffer text = new StringBuffer();
        
        MediaTypeRegistry registry = tika.getMediaTypeRegistry();
        Map<MediaType, Parser> parsers = ((CompositeParser)tika.getParser()).getParsers();

        for (MediaType type : registry.getTypes()) {
            text.append(type);
            text.append("\n");
            
            for (MediaType alias : registry.getAliases(type)) {
                text.append("  alias:     " + alias);
                text.append("\n");
            }
            MediaType supertype = registry.getSupertype(type);
            if (supertype != null && !MediaType.OCTET_STREAM.equals(supertype)) {
                text.append("  supertype: " + supertype);
                text.append("\n");
            }
            
            Parser p = parsers.get(type);
            if (p != null) {
                if (p instanceof CompositeParser) {
                    p = ((CompositeParser)p).getParsers().get(type);
                }
                text.append("  parser:    " + p.getClass().getName());
                text.append("\n");
            }
        }

        return text.toString();
    }
}
