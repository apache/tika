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

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.CompositeDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.server.HTMLHelper;
import org.eclipse.jetty.util.ajax.JSON;

/**
 * <p>Provides details of all the {@link Detector}s registered with
 * Apache Tika, similar to <em>--list-detectors</em> with the Tika CLI.
 */
@Path("/detectors")
public class TikaDetectors {
    private TikaConfig tika;
    private HTMLHelper html;

    public TikaDetectors(TikaConfig tika) {
        this.tika = tika;
        this.html = new HTMLHelper();
    }

    @GET
    @Produces("text/html")
    public String getDectorsHTML() {
        StringBuffer h = new StringBuffer();
        html.generateHeader(h, "Detectors available to Apache Tika");
        detectorAsHTML(tika.getDetector(), h, 2);
        html.generateFooter(h);
        return h.toString();
    }

    private void detectorAsHTML(Detector d, StringBuffer html, int level) {
        html.append("<h");
        html.append(level);
        html.append(">");
        String name = d.getClass().getName();
        html.append(name.substring(name.lastIndexOf('.') + 1));
        html.append("</h");
        html.append(level);
        html.append(">");
        html.append("<p>Class: ");
        html.append(name);
        html.append("</p>");
        if (d instanceof CompositeDetector) {
            html.append("<p>Composite Detector</p>");
            for (Detector cd : ((CompositeDetector) d).getDetectors()) {
                detectorAsHTML(cd, html, level + 1);
            }
        }
    }

    @GET
    @Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    public String getDetectorsJSON() {
        Map<String, Object> details = new HashMap<String, Object>();
        detectorAsMap(tika.getDetector(), details);
        return JSON.toString(details);
    }

    private void detectorAsMap(Detector d, Map<String, Object> details) {
        details.put("name", d.getClass().getName());

        boolean isComposite = (d instanceof CompositeDetector);
        details.put("composite", isComposite);
        if (isComposite) {
            List<Map<String, Object>> c = new ArrayList<Map<String, Object>>();
            for (Detector cd : ((CompositeDetector) d).getDetectors()) {
                Map<String, Object> cdet = new HashMap<String, Object>();
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

        for (int i = 0; i < indent; i++) {
            text.append("  ");
        }
        text.append(name);
        if (isComposite) {
            text.append(" (Composite Detector):\n");

            List<Detector> subDetectors = ((CompositeDetector) d).getDetectors();
            for (Detector sd : subDetectors) {
                renderDetector(sd, text, indent + 1);
            }
        } else {
            text.append("\n");        }
    }
}