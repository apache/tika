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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.server.core.HTMLHelper;

/**
 * <p>Provides details of all the {@link Parser}s registered with
 * Apache Tika, similar to <em>--list-parsers</em> and
 * <em>--list-parser-details</em> within the Tika CLI.
 */
@Path("/parsers")
public class TikaParsers {
    private static final ParseContext EMPTY_PC = new ParseContext();
    private HTMLHelper html;

    public TikaParsers() {
        this.html = new HTMLHelper();
    }

    @GET
    @Path("/details")
    @Produces("text/html")
    public String getParserDetailsHTML() {
        return getParsersHTML(true);
    }

    @GET
    @Produces("text/html")
    public String getParsersHTML() {
        return getParsersHTML(false);
    }

    protected String getParsersHTML(boolean withMimeTypes) {
        ParserDetails p = new ParserDetails(TikaResource.getConfig().getParser());

        StringBuffer h = new StringBuffer();
        html.generateHeader(h, "Parsers available to Apache Tika");
        parserAsHTML(p, withMimeTypes, h, 2);
        html.generateFooter(h);
        return h.toString();
    }

    private void parserAsHTML(ParserDetails p, boolean withMimeTypes, StringBuffer html,
                              int level) {
        html.append("<h");
        html.append(level);
        html.append(">");
        html.append(p.shortName);
        html.append("</h");
        html.append(level);
        html.append(">");
        html.append("<p>Class: ");
        html.append(p.className);
        html.append("</p>");
        if (p.isDecorated) {
            html.append("<p>Decorated Parser");
            if (p.decoratedBy != null) {
                html.append(" - ").append(p.decoratedBy);
            }
            html.append("</p>");
        }
        if (p.isComposite) {
            html.append("<p>Composite Parser</p>");
            html.append("<div style=\"margin-left: 1em\">\n");
            for (Parser cp : p.childParsers) {
                parserAsHTML(new ParserDetails(cp), withMimeTypes, html, level + 1);
            }
            html.append("</div>\n");
        } else if (withMimeTypes) {
            html.append("<p>Mime Types:");
            html.append("<ul>");
            for (MediaType mt : p.supportedTypes) {
                html.append("<li>");
                html.append(mt.toString());
                html.append("</li>");
            }
            html.append("</ul>");
            html.append("</p>");
        }
        html.append("\n");
    }

    @GET
    @Path("/details")
    @Produces(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
    public String getParserDetailsJSON() throws IOException {
        return getParsersJSON(true);
    }

    @GET
    @Produces(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
    public String getParsersJSON() throws IOException {
        return getParsersJSON(false);
    }

    protected String getParsersJSON(boolean withMimeTypes) throws IOException {
        Map<String, Object> details = new HashMap<>();
        parserAsMap(new ParserDetails(TikaResource.getConfig().getParser()), withMimeTypes,
                details);
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(details);
    }

    private void parserAsMap(ParserDetails p, boolean withMimeTypes, Map<String, Object> details) {
        details.put("name", p.className);
        details.put("composite", p.isComposite);
        details.put("decorated", p.isDecorated);

        if (p.isComposite) {
            List<Map<String, Object>> c = new ArrayList<>();
            for (Parser cp : p.childParsers) {
                Map<String, Object> cdet = new HashMap<>();
                parserAsMap(new ParserDetails(cp), withMimeTypes, cdet);
                c.add(cdet);
            }
            details.put("children", c);
        } else if (withMimeTypes) {
            List<String> mts = new ArrayList<>(p.supportedTypes.size());
            for (MediaType mt : p.supportedTypes) {
                mts.add(mt.toString());
            }
            details.put("supportedTypes", mts);
        }
    }

    @GET
    @Path("/details")
    @Produces("text/plain")
    public String getParserDetailssPlain() {
        return getParsersPlain(true);
    }

    @GET
    @Produces("text/plain")
    public String getParsersPlain() {
        return getParsersPlain(false);
    }

    protected String getParsersPlain(boolean withMimeTypes) {
        StringBuffer text = new StringBuffer();
        renderParser(new ParserDetails(TikaResource.getConfig().getParser()), withMimeTypes, text,
                "");
        return text.toString();
    }

    private void renderParser(ParserDetails p, boolean withMimeTypes, StringBuffer text,
                              String indent) {
        String nextIndent = indent + "  ";

        text.append(indent);
        text.append(p.className);
        if (p.isDecorated) {
            text.append(" (Decorated Parser");
            if (p.decoratedBy != null) {
                text.append(" ").append(p.decoratedBy);
            }
            text.append(")");
        }
        if (p.isComposite) {
            text.append(" (Composite Parser):\n");

            for (Parser cp : p.childParsers) {
                renderParser(new ParserDetails(cp), withMimeTypes, text, nextIndent);
            }
        } else {
            text.append("\n");
            if (withMimeTypes) {
                for (MediaType mt : p.supportedTypes) {
                    text.append(nextIndent);
                    text.append("Supports: ");
                    text.append(mt.toString());
                    text.append("\n");
                }
            }
        }
    }

    private static class ParserDetails {
        private String className;
        private String shortName;
        private boolean isComposite;
        private boolean isDecorated;
        private String decoratedBy;
        private Set<MediaType> supportedTypes;
        private List<Parser> childParsers;

        private ParserDetails(Parser p) {
            if (p instanceof ParserDecorator) {
                isDecorated = true;
                decoratedBy = ((ParserDecorator) p).getDecorationName();
                p = ((ParserDecorator) p).getWrappedParser();
            }

            className = p.getClass().getName();
            shortName = className.substring(className.lastIndexOf('.') + 1);

            if (p instanceof CompositeParser) {
                isComposite = true;
                supportedTypes = Collections.emptySet();

                // Get the unique set of child parsers
                Set<Parser> children =
                        new HashSet<>(((CompositeParser) p).getParsers(EMPTY_PC).values());
                // Sort it by class name
                childParsers = new ArrayList<>(children);
                childParsers.sort(Comparator.comparing(parser -> parser.getClass().getName()));
            } else {
                supportedTypes = p.getSupportedTypes(EMPTY_PC);
            }
        }
    }
}
