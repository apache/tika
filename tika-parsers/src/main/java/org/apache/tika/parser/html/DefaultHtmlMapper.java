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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The default HTML mapping rules in Tika.
 *
 * @since Apache Tika 0.6
 */
@SuppressWarnings("serial")
public class DefaultHtmlMapper implements HtmlMapper {

    // Based on http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd
    private static final Map<String, String> SAFE_ELEMENTS = new HashMap<String, String>() {{
        put("H1", "h1");
        put("H2", "h2");
        put("H3", "h3");
        put("H4", "h4");
        put("H5", "h5");
        put("H6", "h6");

        put("P", "p");
        put("PRE", "pre");
        put("BLOCKQUOTE", "blockquote");
        put("Q", "q");
        
        put("UL", "ul");
        put("OL", "ol");
        put("MENU", "ul");
        put("LI", "li");
        put("DL", "dl");
        put("DT", "dt");
        put("DD", "dd");

        put("TABLE", "table");
        put("THEAD", "thead");
        put("TBODY", "tbody");
        put("TR", "tr");
        put("TH", "th");
        put("TD", "td");

        put("ADDRESS", "address");
        
        // TIKA-460 - add anchors
        put("A", "a");
        
        // TIKA-463 - add additional elements that contain URLs (and their sub-elements)
        put("MAP", "map");
        put("AREA", "area");
        put("IMG", "img");
        put("FRAMESET", "frameset");
        put("FRAME", "frame");
        put("IFRAME", "iframe");
        put("OBJECT", "object");
        put("PARAM", "param");
        put("INS", "ins");
        put("DEL", "del");
    }};
    
    private static final Set<String> DISCARDABLE_ELEMENTS = new HashSet<String>() {{
        add("STYLE");
        add("SCRIPT");
    }};

    // For information on tags & attributes, see:
    // http://www.w3.org/TR/2002/REC-xhtml1-20020801/dtds.html#a_dtd_XHTML-1.0-Strict
    // http://www.w3schools.com/TAGS/
    private static final Map<String, Set<String>> SAFE_ATTRIBUTES = new HashMap<String, Set<String>>() {{
        put("a", attrSet("charset", "type", "name", "href", "hreflang", "rel", "rev", "shape", "coords"));
        put("img", attrSet("src", "alt", "longdesc", "height", "width", "usemap", "ismap"));
        put("frame", attrSet("longdesc", "name", "src", "frameborder", "marginwidth", "marginheight", "noresize", "scrolling"));
        put("iframe", attrSet("longdesc", "name", "src", "frameborder", "marginwidth", "marginheight", "scrolling", "align", "height", "width"));
        put("link", attrSet("charset", "href", "hreflang", "type", "rel", "rev", "media"));
        put("map", attrSet("id", "class", "style", "title", "name"));
        put("area", attrSet("shape", "coords", "href", "nohref", "alt"));
        put("object", attrSet("declare", "classid", "codebase", "data", "type", "codetype", "archive", "standby", "height", 
                "width", "usemap", "name", "tabindex", "align", "border", "hspace", "vspace"));
        put("param", attrSet("id", "name", "value", "valuetype", "type"));
        put("blockquote", attrSet("cite"));
        put("ins", attrSet("cite", "datetime"));
        put("del", attrSet("cite", "datetime"));
        put("q", attrSet("cite"));
        
        // TODO - fill out this set. Include core, i18n, etc sets where appropriate.
    }};
    
    private static Set<String> attrSet(String... attrs) {
        Set<String> result = new HashSet<String>();
        for (String attr : attrs) {
            result.add(attr);
        }
        return result;
    }
    
    /**
     * @since Apache Tika 0.8
     */
    public static final HtmlMapper INSTANCE = new DefaultHtmlMapper();

    public String mapSafeElement(String name) {
        return SAFE_ELEMENTS.get(name);
    }

    /** Normalizes an attribute name. Assumes that the element name 
     * is valid and normalized 
     */
    public String mapSafeAttribute(String elementName, String attributeName) {
        Set<String> safeAttrs = SAFE_ATTRIBUTES.get(elementName);
        if ((safeAttrs != null) && safeAttrs.contains(attributeName)) {
            return attributeName;
        } else {
            return null;
        }
    }
    
    public boolean isDiscardElement(String name) {
        return DISCARDABLE_ELEMENTS.contains(name);
    }

}
