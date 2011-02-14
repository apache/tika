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
package org.apache.tika.sax.xpath;

import java.util.HashMap;
import java.util.Map;

/**
 * Parser for a very simple XPath subset. Only the following XPath constructs
 * (with namespaces) are supported:
 * <ul>
 *   <li><code>.../text()</code></li>
 *   <li><code>.../@*</code></li>
 *   <li><code>.../@name</code></li>
 *   <li><code>.../*...</code></li>
 *   <li><code>.../name...</code></li>
 *   <li><code>...//*...</code></li>
 *   <li><code>...//name...</code></li>
 * </ul>
 */
public class XPathParser {

    private final Map<String, String> prefixes = new HashMap<String, String>();

    public XPathParser() {
    }

    public XPathParser(String prefix, String namespace) {
        addPrefix(prefix, namespace);
    }

    public void addPrefix(String prefix, String namespace) {
        prefixes.put(prefix, namespace);
    }

    /**
     * Parses the given simple XPath expression to an evaluation state
     * initialized at the document node. Invalid expressions are not flagged
     * as errors, they just result in a failing evaluation state.
     *
     * @param xpath simple XPath expression
     * @return XPath evaluation state
     */
    public Matcher parse(String xpath) {
        if (xpath.equals("/text()")) {
            return TextMatcher.INSTANCE;
        } else if (xpath.equals("/node()")) {
            return NodeMatcher.INSTANCE;
        } else if (xpath.equals("/descendant:node()")) {
            return new CompositeMatcher(
                    TextMatcher.INSTANCE,
                    new ChildMatcher(new SubtreeMatcher(NodeMatcher.INSTANCE)));
        } else if (xpath.equals("/@*")) {
            return AttributeMatcher.INSTANCE;
        } else if (xpath.length() == 0) {
            return ElementMatcher.INSTANCE;
        } else if (xpath.startsWith("/@")) {
            String name = xpath.substring(2);
            String prefix = null;
            int colon = name.indexOf(':');
            if (colon != -1) {
                prefix = name.substring(0, colon);
                name = name.substring(colon + 1);
            }
            if (prefixes.containsKey(prefix)) {
                return new NamedAttributeMatcher(prefixes.get(prefix), name);
            } else {
                return Matcher.FAIL;
            }
        } else if (xpath.startsWith("/*")) {
            return new ChildMatcher(parse(xpath.substring(2)));
        } else if (xpath.startsWith("///")) {
            return Matcher.FAIL;
        } else if (xpath.startsWith("//")) {
            return new SubtreeMatcher(parse(xpath.substring(1)));
        } else if (xpath.startsWith("/")) {
            int slash = xpath.indexOf('/', 1);
            if (slash == -1) {
                slash = xpath.length();
            }
            String name = xpath.substring(1, slash);
            String prefix = null;
            int colon = name.indexOf(':');
            if (colon != -1) {
                prefix = name.substring(0, colon);
                name = name.substring(colon + 1);
            }
            if (prefixes.containsKey(prefix)) {
                return new NamedElementMatcher(
                        prefixes.get(prefix), name,
                        parse(xpath.substring(slash)));
            } else {
                return Matcher.FAIL;
            }
        } else {
            return Matcher.FAIL;
        }
    }

}
