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
package org.apache.tika.server.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.server.HTMLHelper;
import org.eclipse.jetty.util.ajax.JSON;

/**
 * <p>Provides details of all the mimetypes known to Apache Tika,
 * similar to <em>--list-supported-types</em> with the Tika CLI.
 */
@Path("/mime-types")
public class TikaMimeTypes {
    private TikaConfig tika;
    private HTMLHelper html;

    public TikaMimeTypes(TikaConfig tika) {
        this.tika = tika;
        this.html = new HTMLHelper();
    }

    @GET
    @Produces("text/html")
    public String getMimeTypesHTML() {
        StringBuffer h = new StringBuffer();
        html.generateHeader(h, "Apache Tika Supported Mime Types");

        // Get our types
        List<MediaTypeDetails> types = getMediaTypes();

        // Get the first type in each section
        SortedMap<String, String> firstType = new TreeMap<String, String>();
        for (MediaTypeDetails type : types) {
            if (!firstType.containsKey(type.type.getType())) {
                firstType.put(type.type.getType(), type.type.toString());
            }
        }
        h.append("<ul>");
        for (String section : firstType.keySet()) {
            h.append("<li><a href=\"#").append(firstType.get(section)).append("\">").append(section).append("</a></li>\n");
        }
        h.append("</ul>");

        // Output all of them
        for (MediaTypeDetails type : types) {
            h.append("<a name=\"").append(type.type).append("\"></a>\n");
            h.append("<h2>").append(type.type).append("</h2>\n");

            for (MediaType alias : type.aliases) {
                h.append("<div>Alias: ").append(alias).append("</div>\n");
            }
            if (type.supertype != null) {
                h.append("<div>Super Type: <a href=\"#").append(type.supertype).append("\">").append(type.supertype).append("</a></div>\n");
            }

            if (type.parser != null) {
                h.append("<div>Parser: ").append(type.parser).append("</div>\n");
            }
        }

        html.generateFooter(h);
        return h.toString();
    }

    @GET
    @Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    public String getMimeTypesJSON() {
        Map<String, Object> details = new HashMap<String, Object>();

        for (MediaTypeDetails type : getMediaTypes()) {
            Map<String, Object> typeDets = new HashMap<String, Object>();

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
                text.append("  alias:     ").append(alias).append("\n");
            }
            if (type.supertype != null) {
                text.append("  supertype: ").append(type.supertype.toString()).append("\n");
            }

            if (type.parser != null) {
                text.append("  parser:    ").append(type.parser).append("\n");
            }
        }

        return text.toString();
    }

    protected List<MediaTypeDetails> getMediaTypes() {
        MediaTypeRegistry registry = tika.getMediaTypeRegistry();
        Map<MediaType, Parser> parsers = ((CompositeParser) tika.getParser()).getParsers();
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
                    p = ((CompositeParser) p).getParsers().get(type);
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
