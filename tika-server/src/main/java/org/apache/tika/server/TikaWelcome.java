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

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;

/**
 * <p>Provides a basic welcome to the Apache Tika Server.</p>
 * <p>TODO Should ideally also list the endpoints we have defined,
 *  see TIKA-1269 for details of all that.</p>
 */
@Path("/")
public class TikaWelcome {
    private static final String DOCS_URL = "https://wiki.apache.org/tika/TikaJAXRS";
    
    private Tika tika;
    private HTMLHelper html;
    private List<Class<?>> endpoints;
    
    public TikaWelcome(TikaConfig tika, JAXRSServerFactoryBean sf) {
        this.tika = new Tika(tika);
        this.html = new HTMLHelper();
        this.endpoints = sf.getResourceClasses(); 
    }
    
    @GET
    @Produces("text/html")
    public String getWelcomeHTML() {
        StringBuffer h = new StringBuffer();
        html.generateHeader(h, "Welcome to the " + tika.toString() + " Server");
        
        h.append("<p>For endpoints, please see <a href=\"");
        h.append(DOCS_URL);
        h.append("\">");
        h.append(DOCS_URL);
        h.append("</a></p>");
        h.append("<p>Please see TIKA-1269 for details of what should be here...</p>");

        html.generateFooter(h);
        return h.toString();
    }

    @GET
    @Produces("text/plain")
    public String getWelcomePlain() {
        StringBuffer text = new StringBuffer();
        
        text.append(tika.toString());
        text.append("\n");
        text.append("For endpoints, please see ");
        text.append(DOCS_URL);
        text.append("\n");

        return text.toString();
    }
}
