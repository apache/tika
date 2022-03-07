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
package org.apache.tika.server.core.resource;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.server.core.HTMLHelper;

/**
 * <p>Provides details of all the mimetypes known to Apache Tika,
 * similar to <em>--list-supported-types</em> with the Tika CLI.
 * <p>Can also provide full details on a single known type.
 */
@Path("/mime-types")
public class TikaMimeTypes {

    private HTMLHelper html;

    public TikaMimeTypes() {
        this.html = new HTMLHelper();
    }

    private static String[] copyToStringArray(MediaType[] aliases) {
        String[] strings = new String[aliases.length];
        for (int i = 0; i < aliases.length; i++) {
            strings[i] = aliases[i].toString();
        }
        return strings;
    }

    @GET
    @Produces("text/html")
    public String getMimeTypesHTML() {
        StringBuffer h = new StringBuffer();
        html.generateHeader(h, "Apache Tika Supported Mime Types");

        // Get our types
        List<MediaTypeDetails> types = getMediaTypes();

        // Get the first type in each section
        SortedMap<String, String> firstType = new TreeMap<>();
        for (MediaTypeDetails type : types) {
            if (!firstType.containsKey(type.type.getType())) {
                firstType.put(type.type.getType(), type.type.toString());
            }
        }
        h.append("<ul>");
        for (String section : firstType.keySet()) {
            h.append("<li><a href=\"#").append(firstType.get(section)).append("\">").append(section)
             .append("</a></li>\n");
        }
        h.append("</ul>");

        // Output all of them
        for (MediaTypeDetails type : types) {
            h.append("<a name=\"").append(type.type).append("\"></a>\n");
            h.append("<h2><a href=\"mime-types/").append(type.type)
             .append("\">").append(type.type).append("</a></h2>\n");

            for (MediaType alias : type.aliases) {
                h.append("<div>Alias: ").append(alias).append("</div>\n");
            }
            if (type.supertype != null) {
                h.append("<div>Super Type: <a href=\"#").append(type.supertype).append("\">")
                 .append(type.supertype).append("</a></div>\n");
            }
            if (type.mime != null) {
               if (!type.mime.getDescription().isEmpty()) {
                  h.append("<div>Description: ").append(type.mime.getDescription()).append("</div>\n");
               }
               if (!type.mime.getAcronym().isEmpty()) {
                  h.append("<div>Acronym: ").append(type.mime.getAcronym()).append("</div>\n");
               }
               if (!type.mime.getExtension().isEmpty()) {
                  h.append("<div>Default Extension: ").append(type.mime.getExtension()).append("</div>\n");
               }
            }

            if (type.parser != null) {
                h.append("<div>Parser: ").append(type.parser).append("</div>\n");
            }
        }

        html.generateFooter(h);
        return h.toString();
    }

    @GET
    @Path("/{type}/{subtype}")
    @Produces("text/html")
    public String getMimeTypeDetailsHTML(@PathParam("type") String typePart,
                                         @PathParam("subtype") String subtype) {
        MediaTypeDetails type = getMediaType(typePart, subtype);

        StringBuffer h = new StringBuffer();
        html.generateHeader(h, "Apache Tika Details on Mime Type " + type.type);
        h.append("<h2>").append(type.type).append("</h2>\n");

        for (MediaType alias : type.aliases) {
           h.append("<div>Alias: ").append(alias).append("</div>\n");
        }
        if (type.supertype != null) {
           h.append("<div>Super Type: <a href=\"#").append(type.supertype).append("\">")
            .append(type.supertype).append("</a></div>\n");
        }
        if (type.mime != null) {
           if (!type.mime.getDescription().isEmpty()) {
              h.append("<div>Description: ").append(type.mime.getDescription()).append("</div>\n");
           }
           if (!type.mime.getAcronym().isEmpty()) {
              h.append("<div>Acronym: ").append(type.mime.getAcronym()).append("</div>\n");
           }
           if (!type.mime.getUniformTypeIdentifier().isEmpty()) {
              h.append("<div>Uniform Type Identifier: ").append(type.mime.getUniformTypeIdentifier()).append("</div>\n");
           }
           for (URI uri : type.mime.getLinks()) {
              h.append("<div>Link: ").append(uri).append("</div>\n");
           }
           if (!type.mime.getExtension().isEmpty()) {
              h.append("<div>Default Extension: ").append(type.mime.getExtension()).append("</div>\n");
           }
           for (String ext : type.mime.getExtensions()) {
              h.append("<div>Extension: ").append(ext).append("</div>\n");
           }
        }

        if (type.parser != null) {
           h.append("<div>Parser: ").append(type.parser).append("</div>\n");
        }

        html.generateFooter(h);
        return h.toString();
    }

    @GET
    @Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    public String getMimeTypesJSON() throws IOException {
        Map<String, Object> details = new HashMap<>();

        for (MediaTypeDetails type : getMediaTypes()) {
            Map<String, Object> typeDets = new HashMap<>();

            typeDets.put("alias", copyToStringArray(type.aliases));
            if (type.supertype != null) {
                typeDets.put("supertype", type.supertype.toString());
            }
            if (type.parser != null) {
                typeDets.put("parser", type.parser);
            }

            details.put(type.type.toString(), typeDets);
        }

        return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(details);
    }

    @GET
    @Path("/{type}/{subtype}")
    @Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    public String getMimeTypeDetailsJSON(@PathParam("type") String typePart,
                                         @PathParam("subtype") String subtype) throws IOException {
        MediaTypeDetails type = getMediaType(typePart, subtype);
        Map<String, Object> details = new HashMap<>();

        details.put("type", type.type.toString());
        details.put("alias", copyToStringArray(type.aliases));
        if (type.supertype != null) {
           details.put("supertype", type.supertype.toString());
        }
        if (type.parser != null) {
           details.put("parser", type.parser);
        }
        if (type.mime != null) {
           if (! type.mime.getDescription().isEmpty()) {
              details.put("description", type.mime.getDescription());
           }
           if (! type.mime.getAcronym().isEmpty()) {
              details.put("acronym", type.mime.getAcronym());
           }
           if (! type.mime.getUniformTypeIdentifier().isEmpty()) {
              details.put("uniformTypeIdentifier", type.mime.getUniformTypeIdentifier());
           }
           if (! type.mime.getLinks().isEmpty()) {
              details.put("links", type.mime.getLinks());
           }
           if (! type.mime.getExtension().isEmpty()) {
              details.put("defaultExtension", type.mime.getExtension());
           }
           if (! type.mime.getExtensions().isEmpty()) {
              details.put("extensions", type.mime.getExtensions());
           }
        }

        return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(details);
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

    protected MediaTypeDetails getMediaType(String type, String subtype) throws NotFoundException {
       MediaType mt = MediaType.parse(type+"/"+subtype);
       for (MediaTypeDetails mtd : getMediaTypes()) {
          if (mtd.type.equals(mt)) return mtd;
       }
       throw new NotFoundException("No Media Type registered in Tika for " + mt);
    }
    protected List<MediaTypeDetails> getMediaTypes() {
        TikaConfig config = TikaResource.getConfig();
        MimeTypes mimeTypes = config.getMimeRepository();
        MediaTypeRegistry registry = config.getMediaTypeRegistry();
        Map<MediaType, Parser> parsers = ((CompositeParser)config.getParser()).getParsers();

        List<MediaTypeDetails> types =
                new ArrayList<>(registry.getTypes().size());

        for (MediaType type : registry.getTypes()) {
            MediaTypeDetails details = new MediaTypeDetails();
            details.type = type;
            details.aliases = registry.getAliases(type).toArray(new MediaType[0]);

            try {
               details.mime = mimeTypes.getRegisteredMimeType(type.toString());
            } catch (MimeTypeException e) {}

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
        private MimeType mime;
        private String parser;
    }
}
