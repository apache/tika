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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.server.HTMLHelper;
import org.eclipse.jetty.util.ajax.JSON;

/**
 * <p>Provides details of all the {@link Parser}s registered with
 * Apache Tika, similar to <em>--list-parsers</em> and
 * <em>--list-parser-details</em> within the Tika CLI.
 */
@Path("/parsers")
public class TikaParsers {
    private static final ParseContext EMPTY_PC = new ParseContext();
    private TikaConfig tika;
    private HTMLHelper html;

    public TikaParsers(TikaConfig tika) {
        this.tika = tika;
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
        ParserDetails p = new ParserDetails(tika.getParser());

        StringBuffer h = new StringBuffer();
        html.generateHeader(h, "Parsers available to Apache Tika");
        parserAsHTML(p, withMimeTypes, h, 2);
        html.generateFooter(h);
        return h.toString();
    }

    private void parserAsHTML(ParserDetails p, boolean withMimeTypes, StringBuffer html, int level) {
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
            html.append("<p>Decorated Parser</p>");
        }
        if (p.isComposite) {
            html.append("<p>Composite Parser</p>");
            for (Parser cp : p.childParsers) {
                parserAsHTML(new ParserDetails(cp), withMimeTypes, html, level + 1);
            }
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
    }

    @GET
    @Path("/details")
    @Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    public String getParserDetailsJSON() {
        return getParsersJSON(true);
    }

    @GET
    @Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    public String getParsersJSON() {
        return getParsersJSON(false);
    }

    protected String getParsersJSON(boolean withMimeTypes) {
        Map<String, Object> details = new HashMap<String, Object>();
        parserAsMap(new ParserDetails(tika.getParser()), withMimeTypes, details);
        return JSON.toString(details);
    }

    private void parserAsMap(ParserDetails p, boolean withMimeTypes, Map<String, Object> details) {
        details.put("name", p.className);
        details.put("composite", p.isComposite);
        details.put("decorated", p.isDecorated);

        if (p.isComposite) {
            List<Map<String, Object>> c = new ArrayList<Map<String, Object>>();
            for (Parser cp : p.childParsers) {
                Map<String, Object> cdet = new HashMap<String, Object>();
                parserAsMap(new ParserDetails(cp), withMimeTypes, cdet);
                c.add(cdet);
            }
            details.put("children", c);
        } else if (withMimeTypes) {
            List<String> mts = new ArrayList<String>(p.supportedTypes.size());
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
        renderParser(new ParserDetails(tika.getParser()), withMimeTypes, text, "");
        return text.toString();
    }

    private void renderParser(ParserDetails p, boolean withMimeTypes, StringBuffer text, String indent) {
        String nextIndent = indent + "  ";

        text.append(indent);
        text.append(p.className);
        if (p.isDecorated) {
            text.append(" (Decorated Parser)");
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
        private Set<MediaType> supportedTypes;
        private List<Parser> childParsers;

        private ParserDetails(Parser p) {
            if (p instanceof ParserDecorator) {
                isDecorated = true;
                p = ((ParserDecorator) p).getWrappedParser();
            }

            className = p.getClass().getName();
            shortName = className.substring(className.lastIndexOf('.') + 1);

            if (p instanceof CompositeParser) {
                isComposite = true;
                supportedTypes = Collections.emptySet();

                // Get the unique set of child parsers
                Set<Parser> children = new HashSet<Parser>(
                        ((CompositeParser) p).getParsers(EMPTY_PC).values());
                // Sort it by class name
                childParsers = new ArrayList<Parser>(children);
                Collections.sort(childParsers, new Comparator<Parser>() {                    @Override
                    public int compare(Parser p1, Parser p2) {
                        return p1.getClass().getName().compareTo(p2.getClass().getName());
                    }
                });
            } else {
                supportedTypes = p.getSupportedTypes(EMPTY_PC);
            }
        }
    }
}