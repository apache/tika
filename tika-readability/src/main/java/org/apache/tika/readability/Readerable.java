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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Port of Readability-readerable.js: decides, without running {@link Readability}, whether a
 * document probably has an article in it. Read-only on the document. Attribution: see {@link Readability}.
 */
public final class Readerable {

    public static final int DEFAULT_MIN_CONTENT_LENGTH = 140;
    public static final double DEFAULT_MIN_SCORE = 20;

    private Readerable() {
    }

    static boolean isNodeVisible(Element node) {
        return !"none".equals(Dom.style(node, "display"))
                && !node.hasAttr("hidden")
                // check for "fallback-image" so that wikimedia math images are displayed
                && (!node.hasAttr("aria-hidden") || !"true".equals(node.attr("aria-hidden"))
                || node.attr("class").contains("fallback-image"));
    }

    public static boolean isProbablyReaderable(Document doc) {
        return isProbablyReaderable(doc, DEFAULT_MIN_CONTENT_LENGTH, DEFAULT_MIN_SCORE, Readerable::isNodeVisible);
    }

    /**
     * @return whether we suspect {@link Readability#parse()} will succeed at returning an article
     */
    public static boolean isProbablyReaderable(Document doc, int minContentLength, double minScore,
                                               Predicate<Element> visibilityChecker) {
        List<Element> nodes = new ArrayList<>(Dom.byTag(doc, Set.of("p", "pre", "article")));

        // Get <div> nodes which have <br> node(s) and append them into the `nodes` variable. Some articles' DOM
        // structures might look like <div>Sentences<br><br>Sentences<br></div>
        Set<Element> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        seen.addAll(nodes);
        for (Element br : Dom.byTag(doc, "br")) {
            Element parent = br.parent();
            if (Dom.is(parent, "div") && seen.add(parent)) {
                nodes.add(parent);
            }
        }

        double score = 0;
        for (Element node : nodes) {
            if (!visibilityChecker.test(node)) {
                continue;
            }

            String matchString = node.attr("class") + " " + node.attr("id");
            if (Readability.UNLIKELY_CANDIDATES.matcher(matchString).find()
                    && !Readability.OK_MAYBE_ITS_A_CANDIDATE.matcher(matchString).find()) {
                continue;
            }

            if (Dom.is(node, "p") && hasLiAncestor(node)) {
                continue;
            }

            int textContentLength = JsCompat.trim(Dom.textContent(node)).length();
            if (textContentLength < minContentLength) {
                continue;
            }

            score += Math.sqrt(textContentLength - minContentLength);

            if (score > minScore) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLiAncestor(Element node) {
        for (Element p = node.parent(); p != null; p = p.parent()) {
            if (Dom.is(p, "li")) {
                return true;
            }
        }
        return false;
    }
}
