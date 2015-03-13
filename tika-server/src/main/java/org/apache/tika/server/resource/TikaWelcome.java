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

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.cxf.jaxrs.lifecycle.ResourceProvider;
import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.server.HTMLHelper;

/**
 * <p>Provides a basic welcome to the Apache Tika Server.</p>
 */
@Path("/")
public class TikaWelcome {
    private static final String DOCS_URL = "https://wiki.apache.org/tika/TikaJAXRS";

    private static final Map<Class<? extends Annotation>, String> HTTP_METHODS =
            new HashMap<Class<? extends Annotation>, String>();

    static {
        HTTP_METHODS.put(DELETE.class, "DELETE");
        HTTP_METHODS.put(GET.class, "GET");
        HTTP_METHODS.put(HEAD.class, "HEAD");
        HTTP_METHODS.put(OPTIONS.class, "OPTIONS");
        HTTP_METHODS.put(POST.class, "POST");
        HTTP_METHODS.put(PUT.class, "PUT");
    }

    private Tika tika;
    private HTMLHelper html;
    private List<Class<?>> endpoints = new LinkedList<Class<?>>();

    public TikaWelcome(TikaConfig tika, List<ResourceProvider> rCoreProviders) {
        this.tika = new Tika(tika);
        this.html = new HTMLHelper();
        for (ResourceProvider rp : rCoreProviders) {
            this.endpoints.add(rp.getResourceClass());
        }
    }

    protected List<Endpoint> identifyEndpoints() {
        List<Endpoint> found = new ArrayList<Endpoint>();
        for (Class<?> endpoint : endpoints) {
            Path p = endpoint.getAnnotation(Path.class);
            String basePath = null;
            if (p != null)
                basePath = p.value().endsWith("/") ? p.value().substring(0, p.value().length()-2):p.value();

            for (Method m : endpoint.getMethods()) {
                String httpMethod = null;
                String methodPath = null;
                String[] produces = null;

                for (Annotation a : m.getAnnotations()) {
                    for (Class<? extends Annotation> httpMethAnn : HTTP_METHODS.keySet()) {
                        if (httpMethAnn.isInstance(a)) {
                            httpMethod = HTTP_METHODS.get(httpMethAnn);
                        }
                    }
                    if (a instanceof Path) {
                        methodPath = ((Path) a).value();
                    }
                    if (a instanceof Produces) {
                        produces = ((Produces) a).value();
                    }
                }

                if (httpMethod != null) {
                    String mPath = basePath;
                    if (mPath == null) {
                        mPath = "";
                    }
                    if (methodPath != null) {
			if(methodPath.startsWith("/")){
			    mPath += methodPath;
			}
			else{
			    mPath += "/"+ methodPath;
			}
                    }
                    if (produces == null) {
                        produces = new String[0];
                    }
                    found.add(new Endpoint(endpoint, m, mPath, httpMethod, produces));
                }
            }
        }
        Collections.sort(found, new Comparator<Endpoint>() {
            @Override
            public int compare(Endpoint e1, Endpoint e2) {
                int res = e1.path.compareTo(e2.path);
                if (res == 0) {
                    res = e1.methodName.compareTo(e2.methodName);
                }
                return res;
            }
        });
        return found;
    }

    @GET
    @Produces("text/html")
    public String getWelcomeHTML() {
        StringBuffer h = new StringBuffer();
        String tikaVersion = tika.toString();

        html.generateHeader(h, "Welcome to the " + tikaVersion + " Server");

        h.append("<p>For endpoints, please see <a href=\"");
        h.append(DOCS_URL);
        h.append("\">");
        h.append(DOCS_URL);
        h.append("</a>");

        // TIKA-1269 -- Miredot documentation
        // As the SNAPSHOT endpoints are updated, please update the website by running
        // the server tests and doing step 12.6 of https://wiki.apache.org/tika/ReleaseProcess.
        Pattern p = Pattern.compile("\\d+\\.\\d+");
        Matcher m = p.matcher(tikaVersion);
        if (m.find()) {
            String versionNumber = m.group();
            String miredot = "http://tika.apache.org/" + versionNumber + "/miredot/index.html";
            h.append(" and <a href=\"")
                    .append(miredot)
                    .append("\">")
                    .append(miredot)
                    .append("</a>");
        }
        h.append("</p>\n");

        h.append("<ul>\n");
        for (Endpoint e : identifyEndpoints()) {
            h.append("<li><b>");
            h.append(e.httpMethod);
            h.append("</b> <i><a href=\"");
            h.append(e.path);
            h.append("\">");
            h.append(e.path);
            h.append("</a></i><br />");
            h.append("Class: ");
            h.append(e.className);
            h.append("<br />Method: ");
            h.append(e.methodName);
            for (String produces : e.produces) {
                h.append("<br />Produces: ");
                h.append(produces);
            }
            h.append("</li>\n");
        }
        h.append("</ul>\n");

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
        text.append("\n\n");

        for (Endpoint e : identifyEndpoints()) {
            text.append(e.httpMethod);
            text.append(" ");
            text.append(e.path);
            text.append("\n");
            for (String produces : e.produces) {
                text.append(" => ");
                text.append(produces);
                text.append("\n");
            }
        }

        return text.toString();
    }

    protected class Endpoint {
        public final String className;
        public final String methodName;
        public final String path;
        public final String httpMethod;
        public final List<String> produces;

        protected Endpoint(Class<?> endpoint, Method method, String path,
                           String httpMethod, String[] produces) {
            this.className = endpoint.getCanonicalName();
            this.methodName = method.getName();
            this.path = path;
            this.httpMethod = httpMethod;
            this.produces = Collections.unmodifiableList(Arrays.asList(produces));
        }
    }
}
