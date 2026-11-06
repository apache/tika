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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs Mozilla's own conformance suite (test/test-pages at the pinned upstream commit, fetched at
 * build time) against the port. Comparison mirrors upstream's test-readability.js: in-order DOM
 * walk ignoring whitespace-only text, whitespace-collapsed text, and metadata. Attribute values are
 * compared too, which upstream's harness does not do.
 * Pages in known-failures.txt must fail; one that passes must be removed from the list.
 */
public class UpstreamTestPagesTest {

    private static final String URI = "http://fakehost/test/page.html";
    private static final Pattern WS = Pattern.compile(JsCompat.S + "+");
    private static final Pattern XML_NAME = Pattern.compile("^[A-Za-z_:][A-Za-z0-9_:.\\-·À-�]*$");

    private static final Map<String, String> RESULTS = new TreeMap<>();

    @TestFactory
    Stream<DynamicTest> testPages() throws IOException {
        Path root = pagesDir();
        Set<String> knownFailures = knownFailures();
        List<Path> dirs = new ArrayList<>();
        try (Stream<Path> s = Files.list(root)) {
            s.filter(Files::isDirectory).sorted().forEach(dirs::add);
        }
        assertTrue(dirs.size() > 100, "expected the upstream suite under " + root);
        return dirs.stream().map(dir -> DynamicTest.dynamicTest(dir.getFileName().toString(), () -> {
            String name = dir.getFileName().toString();
            List<String> problems = check(dir);
            RESULTS.put(name, problems.isEmpty() ? "PASS" : "FAIL " + problems.get(0));
            boolean known = knownFailures.contains(name);
            if (problems.isEmpty() && known) {
                fail(name + " now passes: remove it from known-failures.txt");
            }
            if (!problems.isEmpty() && !known) {
                fail(String.join("\n", problems));
            }
        }));
    }

    @AfterAll
    static void writeReport() throws IOException {
        String report = System.getProperty("readability.report");
        if (report == null || RESULTS.isEmpty()) {
            return;
        }
        long pass = RESULTS.values().stream().filter(v -> v.equals("PASS")).count();
        StringBuilder sb = new StringBuilder();
        sb.append("pass ").append(pass).append(" / ").append(RESULTS.size()).append('\n');
        RESULTS.forEach((k, v) -> sb.append(k).append('\t').append(v.replace('\n', ' ')).append('\n'));
        Files.writeString(Paths.get(report), sb.toString(), StandardCharsets.UTF_8);
    }

    static Path pagesDir() {
        Assumptions.assumeFalse(Boolean.getBoolean("readability.skipUpstreamTests"),
                "upstream conformance suite skipped (-Dreadability.skipUpstreamTests)");
        String dir = System.getProperty("readability.testPages");
        if (dir == null || !Files.isDirectory(Paths.get(dir))) {
            fail("upstream test pages not found at " + dir
                    + "; they are downloaded during generate-test-resources (skip with -Dreadability.skipUpstreamTests)");
        }
        return Paths.get(dir);
    }

    private static Set<String> knownFailures() throws IOException {
        Set<String> out = new TreeSet<>();
        try (InputStream is = UpstreamTestPagesTest.class.getResourceAsStream("known-failures.txt")) {
            if (is == null) {
                return out;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    out.add(line.split("\\s+")[0]);
                }
            }
        }
        return out;
    }

    static String read(Path p) throws IOException {
        return JsCompat.trim(Files.readString(p, StandardCharsets.UTF_8));
    }

    static Document parseSource(String html) {
        Document doc = Jsoup.parse(html, URI);
        removeComments(doc);
        return doc;
    }

    private static void removeComments(Node node) {
        for (Node child : new ArrayList<>(node.childNodes())) {
            if (child instanceof Comment) {
                child.remove();
            } else {
                removeComments(child);
            }
        }
    }

    static List<String> check(Path dir) throws IOException {
        List<String> problems = new ArrayList<>();
        String source = read(dir.resolve("source.html"));
        String expectedHtml = read(dir.resolve("expected.html"));
        @SuppressWarnings("unchecked")
        Map<String, Object> expectedMeta = (Map<String, Object>) MiniJson.parse(read(dir.resolve("expected-metadata.json")));

        Article article;
        try {
            article = new Readability(parseSource(source),
                    new ReadabilityOptions().classesToPreserve(List.of("caption"))).parse();
        } catch (RuntimeException e) {
            problems.add("threw " + e);
            return problems;
        }
        if (article == null) {
            problems.add("no article");
            return problems;
        }

        String domProblem = compareDom(Jsoup.parse(article.contentHtml()), Jsoup.parse(expectedHtml));
        if (domProblem != null) {
            problems.add("content: " + domProblem);
        }
        compareMeta(problems, "title", expectedMeta.get("title"), article.title(), true);
        compareMeta(problems, "byline", expectedMeta.get("byline"), article.byline(), true);
        compareMeta(problems, "excerpt", expectedMeta.get("excerpt"), article.excerpt(), true);
        compareMeta(problems, "siteName", expectedMeta.get("siteName"), article.siteName(), true);
        compareMeta(problems, "dir", expectedMeta.get("dir"), article.dir(), false);
        compareMeta(problems, "lang", expectedMeta.get("lang"), article.lang(), false);
        compareMeta(problems, "publishedTime", expectedMeta.get("publishedTime"), article.publishedTime(), false);

        Object readerable = expectedMeta.get("readerable");
        if (readerable instanceof Boolean
                && Readerable.isProbablyReaderable(parseSource(source)) != (Boolean) readerable) {
            problems.add("readerable: expected " + readerable);
        }
        return problems;
    }

    /** Upstream checks dir, lang and publishedTime only when the expectation is truthy. */
    private static void compareMeta(List<String> problems, String key, Object expected, String actual, boolean always) {
        String exp = expected instanceof String ? (String) expected : null;
        if (!always && (exp == null || exp.isEmpty())) {
            return;
        }
        if (!Objects.equals(exp, actual)) {
            problems.add(key + ": expected <" + exp + "> got <" + actual + ">");
        }
    }

    private static Node next(Node n) {
        if (n.firstChild() != null) {
            return n.firstChild();
        }
        while (n != null && n.nextSibling() == null) {
            n = n.parentNode();
        }
        return n == null ? null : n.nextSibling();
    }

    private static Node nextIgnoringEmptyText(Node n) {
        do {
            n = next(n);
        } while (n != null && Dom.isText(n) && JsCompat.trim(Dom.textContent(n)).isEmpty());
        return n;
    }

    private static String nodeStr(Node n) {
        if (n == null) {
            return "(no node)";
        }
        if (Dom.isText(n)) {
            return "#text(" + collapse(Dom.textContent(n)) + ")";
        }
        if (!(n instanceof Element)) {
            return "some other node type: " + n.nodeName();
        }
        Element e = (Element) n;
        String rv = e.normalName();
        if (!e.attr("id").isEmpty()) {
            rv += "#" + e.attr("id");
        }
        if (!e.attr("class").isEmpty()) {
            rv += ".(" + e.attr("class") + ")";
        }
        return rv;
    }

    /** We don't pretty-print both sides as upstream does, so edges of text runs are trimmed too. */
    private static String collapse(String s) {
        return JsCompat.trim(WS.matcher(s).replaceAll(" "));
    }

    private static int attributeCount(Element e) {
        int n = 0;
        for (org.jsoup.nodes.Attribute a : e.attributes()) {
            if (XML_NAME.matcher(a.getKey()).matches()) {
                n++;
            }
        }
        return n;
    }

    private static String compareDom(Document actual, Document expected) {
        Node a = actual.firstElementChild();
        Node e = expected.firstElementChild();
        while (a != null || e != null) {
            String as = nodeStr(a);
            String es = nodeStr(e);
            if (!as.equals(es)) {
                return "expected " + truncate(es) + " got " + truncate(as) + context(a);
            }
            // stricter than upstream, whose value check reads an undefined name and so compares counts only
            if (a instanceof Element && !attributeValues((Element) a).equals(attributeValues((Element) e))) {
                return "attributes of " + as + ": expected " + attributeValues((Element) e) + " got " + attributeValues((Element) a);
            }
            a = nextIgnoringEmptyText(a);
            e = nextIgnoringEmptyText(e);
        }
        return null;
    }

    private static Map<String, String> attributeValues(Element e) {
        Map<String, String> m = new TreeMap<>();
        for (org.jsoup.nodes.Attribute attr : e.attributes()) {
            if (XML_NAME.matcher(attr.getKey()).matches()) {
                // expected files spell boolean attributes both itemscope="" and itemscope="itemscope"
                m.put(attr.getKey(), attr.getValue().equals(attr.getKey()) ? "" : attr.getValue());
            }
        }
        return m;
    }

    private static String context(Node n) {
        if (n == null || n.parentNode() == null) {
            return "";
        }
        return " (in " + truncate(n.parentNode().outerHtml()) + ")";
    }

    private static String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
