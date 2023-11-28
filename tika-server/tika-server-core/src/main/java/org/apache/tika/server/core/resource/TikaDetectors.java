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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import org.apache.tika.detect.CompositeDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.server.core.HTMLHelper;

/**
 * <p>Provides details of all the {@link Detector}s registered with
 * Apache Tika, similar to <em>--list-detectors</em> with the Tika CLI.
 */
@Path("/detectors")
public class TikaDetectors {

    private HTMLHelper html;

    public TikaDetectors() {
        this.html = new HTMLHelper();
    }

    @GET
    @Produces("text/html")
    public String getDectorsHTML() {
        StringBuffer h = new StringBuffer();
        html.generateHeader(h, "Detectors available to Apache Tika");
        detectorAsHTML(TikaResource.getConfig().getDetector(), h, 2);
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
    @Produces(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
    public String getDetectorsJSON() throws IOException {
        Map<String, Object> details = new HashMap<>();
        detectorAsMap(TikaResource.getConfig().getDetector(), details);
        return new ObjectMapper().writeValueAsString(details);
    }

    private void detectorAsMap(Detector d, Map<String, Object> details) {
        details.put("name", d.getClass().getName());

        boolean isComposite = (d instanceof CompositeDetector);
        details.put("composite", isComposite);
        if (isComposite) {
            List<Map<String, Object>> c = new ArrayList<>();
            for (Detector cd : ((CompositeDetector) d).getDetectors()) {
                Map<String, Object> cdet = new HashMap<>();
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
        renderDetector(TikaResource.getConfig().getDetector(), text, 0);
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
            text.append("\n");
        }
    }
}
