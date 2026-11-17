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
package org.apache.tika.readability;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jsoup.nodes.Comment;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/** W3C DOM semantics over jsoup where the two differ. */
final class Dom {

    private Dom() {
    }

    /** DOM nodeType 3: jsoup keeps script/style bodies as {@link DataNode}. */
    static boolean isText(Node n) {
        return n instanceof TextNode || n instanceof DataNode;
    }

    static boolean is(Node n, String tag) {
        return n instanceof Element && !(n instanceof Document) && ((Element) n).normalName().equals(tag);
    }

    /** {@code element.parentNode} is an element, not the document and not absent. */
    static boolean hasElementParent(Node n) {
        Node p = n.parentNode();
        return p instanceof Element && !(p instanceof Document);
    }

    /** DOM {@code textContent}: text and script data of descendants, never comments. */
    static String textContent(Node n) {
        if (n instanceof TextNode) {
            return ((TextNode) n).getWholeText();
        }
        if (n instanceof DataNode) {
            return ((DataNode) n).getWholeData();
        }
        if (n instanceof Comment) {
            return ((Comment) n).getData();
        }
        if (!(n instanceof Element)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Node node = n.firstChild();
        while (node != null && node != n) {
            if (node instanceof TextNode) {
                sb.append(((TextNode) node).getWholeText());
            } else if (node instanceof DataNode) {
                sb.append(((DataNode) node).getWholeData());
            }
            Node first = node.firstChild();
            if (first != null) {
                node = first;
                continue;
            }
            while (node != n && node.nextSibling() == null) {
                node = node.parentNode();
            }
            node = node == n ? null : node.nextSibling();
        }
        return sb.toString();
    }

    /** {@code querySelectorAll(tags)}: descendants only, document order. */
    static List<Element> byTag(Element root, Set<String> tags) {
        List<Element> out = new ArrayList<>();
        for (Element e : root.getAllElements()) {
            if (e != root && tags.contains(e.normalName())) {
                out.add(e);
            }
        }
        return out;
    }

    static List<Element> byTag(Element root, String tag) {
        return byTag(root, Set.of(tag));
    }

    static Element firstByTag(Element root, String tag) {
        for (Element e : root.getAllElements()) {
            if (e != root && e.normalName().equals(tag)) {
                return e;
            }
        }
        return null;
    }

    /** {@code getAttribute}: null when absent. */
    static String attr(Element e, String name) {
        return e.hasAttr(name) ? e.attr(name) : null;
    }

    /** {@code element.style.<prop>} for an inline style; empty when not declared. */
    static String style(Element e, String prop) {
        String style = attr(e, "style");
        if (style == null) {
            return "";
        }
        String found = "";
        for (String decl : style.split(";")) {
            int colon = decl.indexOf(':');
            if (colon < 0) {
                continue;
            }
            if (JsCompat.lower(JsCompat.trim(decl.substring(0, colon))).equals(prop)) {
                String v = JsCompat.lower(JsCompat.trim(decl.substring(colon + 1)));
                if (v.endsWith("!important")) {
                    v = JsCompat.trim(v.substring(0, v.length() - "!important".length()));
                }
                found = v;
            }
        }
        return found;
    }
}
