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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.Parser;
import org.eclipse.jetty.util.ajax.JSON;

/**
 * <p>Provides details of all the mimetypes known to Apache Tika,
 *  similar to <em>--list-supported-types</em> with the Tika CLI.
 *  
 * <p>TODO Provide better support for the HTML based outputs
 */
@Path("/mime-types")
public class TikaMimeTypes {
    private TikaConfig tika;
    public TikaMimeTypes(TikaConfig tika) {
        this.tika = tika;
    }
    
    @GET
    @Produces("text/html")
    public String getMimeTypesHTML() {
        StringBuffer html = new StringBuffer();
        html.append("<html><head><title>Tika Supported Mime Types</title></head>\n");
        html.append("<body><h1>Tika Supported Mime Types</h1>\n");
        
        // Get our types
        List<MediaTypeDetails> types = getMediaTypes();
        
        // Get the first type in each section
        SortedMap<String,String> firstType = new TreeMap<String, String>();
        for (MediaTypeDetails type : types) {
            if (! firstType.containsKey(type.type.getType())) {
                firstType.put(type.type.getType(), type.type.toString());
            }
        }
        html.append("<ul>");
        for (String section : firstType.keySet()) {
            html.append("<li><a href=\"#" + firstType.get(section) + "\">" + 
                        section + "</a></li>\n");
        }
        html.append("</ul>");
        
        // Output all of them
        for (MediaTypeDetails type : types) {
            html.append("<a name=\"" + type.type + "\"></a>\n");
            html.append("<h2>" + type.type + "</h2>\n");
            
            for (MediaType alias : type.aliases) {
                html.append("<div>Alias: " + alias + "</div>\n");
            }
            if (type.supertype != null) {
                html.append("<div>Super Type: <a href=\"#" + type.supertype + 
                            "\">" + type.supertype + "</a></div>\n");
            }
            
            if (type.parser != null) {
                html.append("<div>Parser: " + type.parser + "</div>\n");
            }
        }

        html.append("</body></html>\n");
        return html.toString();
    }
    
    @GET
    @Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    public String getMimeTypesJSON() {
        Map<String,Object> details = new HashMap<String, Object>();
        
        for (MediaTypeDetails type : getMediaTypes()) {
            Map<String,Object> typeDets = new HashMap<String, Object>();

            typeDets.put("alias", type.aliases);
            if (type.supertype != null) {
                typeDets.put("supertype", type.supertype);
            }
            if (type.parser != null) {
                typeDets.put("parser", type.parser);
            }

            details.put(type.type.toString(), typeDets);
        }
        
        return JSON.toString(details);
    }
    
    @GET
    @Produces("text/plain")
    public String getMimeTypesPlain() {
        StringBuffer text = new StringBuffer();
        
        for (MediaTypeDetails type : getMediaTypes()) {
            text.append(type.type.toString());
            text.append("\n");
            
            for (MediaType alias : type.aliases) {
                text.append("  alias:     " + alias + "\n");
            }
            if (type.supertype != null) {
                text.append("  supertype: " + type.supertype.toString() + "\n");
            }
            
            if (type.parser != null) {
                text.append("  parser:    " + type.parser + "\n");
            }
        }

        return text.toString();
    }
    
    protected List<MediaTypeDetails> getMediaTypes() {
        MediaTypeRegistry registry = tika.getMediaTypeRegistry();
        Map<MediaType, Parser> parsers = ((CompositeParser)tika.getParser()).getParsers();
        List<MediaTypeDetails> types = 
                new ArrayList<TikaMimeTypes.MediaTypeDetails>(registry.getTypes().size());

        for (MediaType type : registry.getTypes()) {
            MediaTypeDetails details = new MediaTypeDetails();
            details.type = type;
            details.aliases = registry.getAliases(type).toArray(new MediaType[0]);
            
            MediaType supertype = registry.getSupertype(type);
            if (supertype != null && !MediaType.OCTET_STREAM.equals(supertype)) {
                details.supertype = supertype;
            }
            
            Parser p = parsers.get(type);
            if (p != null) {
                if (p instanceof CompositeParser) {
                    p = ((CompositeParser)p).getParsers().get(type);
                }
                details.parser = p.getClass().getName();
            }
            
            types.add(details);
        }
        
        return types;
    }
    
    private static class MediaTypeDetails {
        private MediaType type;
        private MediaType[] aliases;
        private MediaType supertype;
        private String parser;
    }
}
