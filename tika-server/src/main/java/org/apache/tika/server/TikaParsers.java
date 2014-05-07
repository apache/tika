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

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.eclipse.jetty.util.ajax.JSON;

/**
 * <p>Provides details of all the {@link Parser}s registered with
 *  Apache Tika, similar to <em>--list-parsers</em> and
 *  <em>--list-parser-details</em> within the Tika CLI.
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
        StringBuffer h = new StringBuffer();
        html.generateHeader(h, "Parsers available to Apache Tika");
        parserAsHTML(tika.getParser(), withMimeTypes, h, 2);
        html.generateFooter(h);
        return h.toString();
    }
    private void parserAsHTML(Parser p, boolean withMimeTypes, StringBuffer html, int level) {
        html.append("<h");
        html.append(level);
        html.append(">");
        // TODO Parser Decorators
        String name = p.getClass().getName();
        html.append(name.substring(name.lastIndexOf('.')+1));
        html.append("</h");
        html.append(level);
        html.append(">");
        html.append("<p>Class: ");
        html.append(name);
        html.append("</p>");
        if (p instanceof CompositeParser) {
            html.append("<p>Composite Parser</p>");
            // TODO Sort nicely
            for (Parser cp : ((CompositeParser)p).getParsers(EMPTY_PC).values()) {
                parserAsHTML(cp, withMimeTypes, html, level+1);
            }            
        } else if (withMimeTypes) {
            html.append("<p>Mime Types:");
            html.append("<ul>");
            for (MediaType mt : p.getSupportedTypes(EMPTY_PC)) {
                html.append("<li>");
                html.append(mt.toString());
                html.append("</li>");
            }
            html.append("</ul>");
            html.append("</p>");
        }
    }
    
    /*
    @GET
    @Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    public String getDetectorsJSON() {
        Map<String,Object> details = new HashMap<String, Object>();
        detectorAsMap(tika.getDetector(), details);
        return JSON.toString(details);
    }
    private void detectorAsMap(Detector d, Map<String, Object> details) {
        details.put("name", d.getClass().getName());
        
        boolean isComposite = (d instanceof CompositeDetector);
        details.put("composite", isComposite);
        if (isComposite) {
            List<Map<String, Object>> c = new ArrayList<Map<String,Object>>();
            for (Detector cd : ((CompositeDetector)d).getDetectors()) {
                Map<String,Object> cdet = new HashMap<String, Object>();
                detectorAsMap(cd, cdet);
                c.add(cdet);
            }
            details.put("children", c);
        }
    }
    
    @GET
    @Produces("text/plain")
    public String getDetectorsPlain() {
        StringBuffer text = new StringBuffer();
        renderDetector(tika.getDetector(), text, 0);
        return text.toString();
    }
    private void renderDetector(Detector d, StringBuffer text, int indent) {
        boolean isComposite = (d instanceof CompositeDetector);
        String name = d.getClass().getName();
        
        for (int i=0; i<indent; i++) {
            text.append("  ");
        }
        text.append(name);
        if (isComposite) {
            text.append(" (Composite Detector):\n");

            List<Detector> subDetectors = ((CompositeDetector)d).getDetectors();
            for(Detector sd : subDetectors) {
                renderDetector(sd, text, indent+1);
            }
        } else {
            text.append("\n");
        }
    }
    */
    
    private static class ParserDetails {
        private String classname;
        private boolean isComposite;
        private boolean isDecorated;
        private List<MediaType> supportedTypes;
        private List<Parser> childParsers;
        private ParserDetails(Parser p) {
            // TODO Implement
        }
    }
}