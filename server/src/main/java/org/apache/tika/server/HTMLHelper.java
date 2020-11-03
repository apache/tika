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

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Helps produce user facing HTML output.
 * <p/>
 * TODO Decide if this would be better done as a MessageBodyWriter
 */
public class HTMLHelper {
    private static final String PATH = "/tikaserver-template.html";
    private static final String TITLE_VAR = "[[TITLE]]";
    private static final String BODY_VAR = "[[BODY]]";
    private String PRE_BODY;
    private String POST_BODY;

    public HTMLHelper() {
        InputStream htmlStr = getClass().getResourceAsStream(PATH);
        if (htmlStr == null) {
            throw new IllegalArgumentException("Template Not Found - " + PATH);
        }
        try {
            String html = IOUtils.toString(htmlStr, UTF_8);
            int bodyAt = html.indexOf(BODY_VAR);
            PRE_BODY = html.substring(0, bodyAt);
            POST_BODY = html.substring(bodyAt + BODY_VAR.length());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read template");
        }
    }

    /**
     * Generates the HTML Header for the user facing page, adding
     * in the given title as required
     */
    public void generateHeader(StringBuffer html, String title) {
        html.append(PRE_BODY.replace(TITLE_VAR, title));
    }

    public void generateFooter(StringBuffer html) {
        html.append(POST_BODY);
    }}