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
package org.apache.tika.parser.html;

/**
 * The default HTML mapping rules in Tika.
 *
 * @since Apache Tika 0.6
 */
public class DefaultHtmlMapper implements HtmlMapper {

    public String mapSafeElement(String name) {
        // Based on http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd

        if ("H1".equals(name)) return "h1";
        if ("H2".equals(name)) return "h2";
        if ("H3".equals(name)) return "h3";
        if ("H4".equals(name)) return "h4";
        if ("H5".equals(name)) return "h5";
        if ("H6".equals(name)) return "h6";

        if ("P".equals(name)) return "p";
        if ("PRE".equals(name)) return "pre";
        if ("BLOCKQUOTE".equals(name)) return "blockquote";

        if ("UL".equals(name)) return "ul";
        if ("OL".equals(name)) return "ol";
        if ("MENU".equals(name)) return "ul";
        if ("LI".equals(name)) return "li";
        if ("DL".equals(name)) return "dl";
        if ("DT".equals(name)) return "dt";
        if ("DD".equals(name)) return "dd";

        if ("TABLE".equals(name)) return "table";
        if ("THEAD".equals(name)) return "thead";
        if ("TBODY".equals(name)) return "tbody";
        if ("TR".equals(name)) return "tr";
        if ("TH".equals(name)) return "th";
        if ("TD".equals(name)) return "td";

        if ("ADDRESS".equals(name)) return "address";

        return null;
    }

    public boolean isDiscardElement(String name) {
        return "STYLE".equals(name) || "SCRIPT".equals(name);
    }

}
