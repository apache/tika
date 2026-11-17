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

import static org.apache.tika.readability.Dom.attr;
import static org.apache.tika.readability.Dom.byTag;
import static org.apache.tika.readability.Dom.is;
import static org.apache.tika.readability.Dom.isText;
import static org.apache.tika.readability.JsCompat.NS;
import static org.apache.tika.readability.JsCompat.S;
import static org.apache.tika.readability.JsCompat.lower;
import static org.apache.tika.readability.JsCompat.trim;
import static org.apache.tika.readability.JsCompat.truthy;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.NodeFilter;
import org.jsoup.select.NodeTraversor;

/**
 * Java port of Mozilla's Readability.js (https://github.com/mozilla/readability), which is
 * Copyright (c) 2010 Arc90 Inc and Copyright (c) 2010-2026 Mozilla and Contributors, under the
 * Apache License 2.0. Tracks the upstream commit named by {@code readability.upstream.sha} in
 * this module's pom.
 * <p>
 * Methods keep upstream's names and order so an upstream diff maps onto this file. Like
 * upstream, {@link #parse()} mutates the document it is given: pass a clone to keep the original.
 * Not thread-safe; one instance per document.
 */
public class Readability {

    private static final int FLAG_STRIP_UNLIKELYS = 0x1;
    private static final int FLAG_WEIGHT_CLASSES = 0x2;
    private static final int FLAG_CLEAN_CONDITIONALLY = 0x4;

    private static final Set<String> DEFAULT_TAGS_TO_SCORE =
            Set.of("section", "h2", "h3", "h4", "h5", "h6", "p", "td", "pre");

    // NOTE: unlikelyCandidates and okMaybeItsACandidate are duplicated in Readerable.
    static final Pattern UNLIKELY_CANDIDATES = Pattern.compile(
            "-ad-|ai2html|banner|breadcrumbs|combx|comment|community|cover-wrap|disqus|extra|footer|gdpr|header|legends|menu|related|remark|replies|rss|shoutbox|sidebar|"
                    + "skyscraper|social|sponsor|supplemental|ad-break|agegate|pagination|pager|popup|yom-remote",
            Pattern.CASE_INSENSITIVE);
    static final Pattern OK_MAYBE_ITS_A_CANDIDATE =
            Pattern.compile("and|article|body|column|content|main|mathjax|shadow", Pattern.CASE_INSENSITIVE);
    private static final Pattern POSITIVE = Pattern.compile(
            "article|body|content|entry|hentry|h-entry|main|page|pagination|post|text|blog|story", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEGATIVE = Pattern.compile(
            "-ad-|hidden|^hid$| hid$| hid |^hid |banner|combx|comment|com-|contact|footer|gdpr|masthead|media|meta|outbrain|promo|related|scroll|share|shoutbox|"
                    + "sidebar|skyscraper|sponsor|shopping|tags|widget",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BYLINE = Pattern.compile("byline|author|dateline|writtenby|p-author", Pattern.CASE_INSENSITIVE);
    private static final Pattern NORMALIZE = Pattern.compile(S + "{2,}");
    static final Pattern VIDEOS = Pattern.compile(
            "//(www\\.)?((dailymotion|youtube|youtube-nocookie|player\\.vimeo|v\\.qq|bilibili|live.bilibili)\\.com|(archive|upload\\.wikimedia)\\.org|player\\.twitch\\.tv)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SHARE_ELEMENTS = Pattern.compile("(\\b|_)(share|sharedaddy)(\\b|_)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKENIZE = Pattern.compile("\\W+");
    private static final Pattern WHITESPACE = Pattern.compile("^" + S + "*\\z");
    private static final Pattern HAS_CONTENT = Pattern.compile(NS + "\\z");
    private static final Pattern HASH_URL = Pattern.compile("^#.+");
    private static final Pattern SRCSET_URL = Pattern.compile("(" + NS + "+)(" + S + "+[\\d.]+[xw])?(" + S + "*(?:,|\\z))");
    private static final Pattern B64_DATA_URL = Pattern.compile("^data:" + S + "*([^" + JsCompat.WS_CHARS + ";,]+)" + S + "*;" + S + "*base64" + S + "*,",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMAS = Pattern.compile("[\\u002C\\u060C\\uFE50\\uFE10\\uFE11\\u2E41\\u2E34\\u2E32\\uFF0C]");
    // upstream's alternation binds ^ and $ to the first and last alternatives only
    private static final Pattern JSON_LD_ARTICLE_TYPES = Pattern.compile(
            "^Article|AdvertiserContentArticle|NewsArticle|AnalysisNewsArticle|AskPublicNewsArticle|BackgroundNewsArticle|OpinionNewsArticle|"
                    + "ReportageNewsArticle|ReviewNewsArticle|Report|SatiricalArticle|ScholarlyArticle|MedicalScholarlyArticle|SocialMediaPosting|"
                    + "BlogPosting|LiveBlogPosting|DiscussionForumPosting|TechArticle|APIReference\\z");
    private static final Pattern AD_WORDS = Pattern.compile(
            "^(ad(vertising|vertisement)?|pub(licité)?|werb(ung)?|广告|Реклама|Anuncio)\\z",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern LOADING_WORDS = Pattern.compile(
            "^((loading|正在加载|Загрузка|chargement|cargando)(…|\\.\\.\\.)?)\\z",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern IMAGE_EXT = Pattern.compile("\\.(jpg|jpeg|png|webp)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SRCSET_LIKE = Pattern.compile("\\.(jpg|jpeg|png|webp)" + S + "+\\d");
    private static final Pattern SRC_LIKE = Pattern.compile("^" + S + "*" + NS + "+\\.(jpg|jpeg|png|webp)" + NS + "*" + S + "*\\z");
    private static final Pattern SENTENCE_END = Pattern.compile("\\.( |\\z)");
    private static final Pattern CDATA = Pattern.compile("^" + S + "*<!\\[CDATA\\[|\\]\\]>" + S + "*\\z");
    private static final Pattern SCHEMA_DOT_ORG = Pattern.compile("^https?://schema\\.org/?\\z");
    private static final Pattern ESCAPED_ENTITY = Pattern.compile("&(quot|amp|apos|lt|gt);");
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(?:x([0-9a-f]+)|([0-9]+));", Pattern.CASE_INSENSITIVE);
    private static final Pattern META_PROPERTY = Pattern.compile(
            S + "*(article|dc|dcterm|og|twitter)" + S + "*:" + S + "*(author|creator|description|published_time|title|site_name)" + S + "*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern META_NAME = Pattern.compile(
            "^" + S + "*(?:(dc|dcterm|og|twitter|parsely|weibo:(article|webpage))" + S + "*[-.:]" + S + "*)?(author|creator|pub-date|description|title|site_name)" + S + "*\\z",
            Pattern.CASE_INSENSITIVE);

    private static final String TITLE_SEPARATORS = "\\|\\-–—\\\\/>»";
    private static final Pattern TITLE_SEPARATOR = Pattern.compile(S + "[" + TITLE_SEPARATORS + "]" + S);
    private static final Pattern TITLE_HIERARCHICAL_SEPARATOR = Pattern.compile(S + "[\\\\/>»]" + S);
    private static final Pattern TITLE_FIRST_PART = Pattern.compile("^[^" + TITLE_SEPARATORS + "]*[" + TITLE_SEPARATORS + "]");

    private static final List<String> UNLIKELY_ROLES =
            List.of("menu", "menubar", "complementary", "navigation", "alert", "alertdialog", "dialog");
    private static final Set<String> DIV_TO_P_ELEMS =
            Set.of("blockquote", "dl", "div", "img", "ol", "p", "pre", "table", "ul");
    private static final Set<String> ALTER_TO_DIV_EXCEPTIONS = Set.of("div", "article", "section", "p", "ol", "ul");
    private static final List<String> PRESENTATIONAL_ATTRIBUTES =
            List.of("align", "background", "bgcolor", "border", "cellpadding", "cellspacing", "frame", "hspace", "rules", "style", "valign", "vspace");
    private static final Set<String> DEPRECATED_SIZE_ATTRIBUTE_ELEMS = Set.of("table", "th", "td", "hr", "pre");
    // canvas, iframe, svg and video are phrasing content but tend to be removed when put into paragraphs
    private static final Set<String> PHRASING_ELEMS = Set.of(
            "abbr", "audio", "b", "bdo", "br", "button", "cite", "code", "data", "datalist", "dfn", "em", "embed", "i", "img", "input", "kbd", "label",
            "mark", "math", "meter", "noscript", "object", "output", "progress", "q", "ruby", "samp", "script", "select", "small", "span", "strong",
            "sub", "sup", "textarea", "time", "var", "wbr");
    private static final List<String> CLASSES_TO_PRESERVE = List.of("page");
    private static final Map<String, String> HTML_ESCAPE_MAP =
            Map.of("lt", "<", "gt", ">", "amp", "&", "quot", "\"", "apos", "'");
    private static final Set<String> TEXTISH_TAGS;

    static {
        java.util.HashSet<String> t = new java.util.HashSet<>(DIV_TO_P_ELEMS);
        t.add("span");
        t.add("li");
        t.add("td");
        TEXTISH_TAGS = Set.copyOf(t);
    }

    private final Document doc;
    private final String documentUri;
    private final String baseUri;
    private String articleTitle;
    private String articleByline;
    private String articleDir;
    private String articleLang;
    private final List<Attempt> attempts = new ArrayList<>();
    private Map<String, String> metadata = new LinkedHashMap<>();

    private final int maxElemsToParse;
    private final long maxTextScanned;
    private long textScanned;
    private final int nbTopCandidates;
    private final int charThreshold;
    private final List<String> classesToPreserve;
    private final boolean keepClasses;
    private final boolean disableJsonLd;
    private final Pattern allowedVideoRegex;
    private final double linkDensityModifier;

    private int flags = FLAG_STRIP_UNLIKELYS | FLAG_WEIGHT_CLASSES | FLAG_CLEAN_CONDITIONALLY;

    // upstream's node.readability and table._readabilityDataTable expando properties
    private final Map<Element, double[]> scores = new IdentityHashMap<>();
    private final Map<Element, Boolean> dataTables = new IdentityHashMap<>();

    private record Attempt(Element articleContent, int textLength) {
    }

    public Readability(Document doc) {
        this(doc, new ReadabilityOptions());
    }

    public Readability(Document doc, ReadabilityOptions options) {
        this.doc = doc;
        this.maxElemsToParse = options.getMaxElemsToParse();
        this.maxTextScanned = options.getMaxTextScanned();
        this.nbTopCandidates = options.getNbTopCandidates();
        this.charThreshold = options.getCharThreshold();
        List<String> preserve = new ArrayList<>(CLASSES_TO_PRESERVE);
        preserve.addAll(options.getClassesToPreserve());
        this.classesToPreserve = preserve;
        this.keepClasses = options.isKeepClasses();
        this.disableJsonLd = options.isDisableJsonLd();
        this.allowedVideoRegex = options.getAllowedVideoRegex() != null ? options.getAllowedVideoRegex() : VIDEOS;
        this.linkDensityModifier = options.getLinkDensityModifier();
        this.documentUri = options.getDocumentUri() != null ? options.getDocumentUri() : doc.location();
        this.baseUri = computeBaseUri(doc, documentUri);
        doc.outputSettings().prettyPrint(false);
    }

    /** DOM textContent, charged against maxTextScanned. */
    private String textContent(Node n) {
        String t = Dom.textContent(n);
        textScanned += t.length();
        if (maxTextScanned > 0 && textScanned > maxTextScanned) {
            throw new ReadabilityLimitException("Aborting parsing document; more than " + maxTextScanned
                    + " characters of text scanned");
        }
        return t;
    }

    long textScanned() {
        return textScanned;
    }

    /** DOM {@code document.baseURI}: the first {@code <base href>}, resolved against the document URI. */
    private static String computeBaseUri(Document doc, String documentUri) {
        for (Element base : byTag(doc, "base")) {
            String href = attr(base, "href");
            if (href != null) {
                String resolved = JsCompat.resolveUrl(documentUri, href);
                return resolved != null ? resolved : documentUri;
            }
        }
        return documentUri;
    }

    /**
     * Run any post-process modifications to article content as necessary.
     */
    private void postProcessContent(Element articleContent) {
        // Readability cannot open relative uris so we convert them to absolute uris.
        fixRelativeUris(articleContent);

        simplifyNestedElements(articleContent);

        if (!keepClasses) {
            cleanClasses(articleContent);
        }
    }

    private interface NodePredicate {
        boolean test(Element node);
    }

    /**
     * Iterates backwards over a static node list, removing each node {@code filterFn} accepts
     * (or every node when it is null).
     */
    private void removeNodes(List<Element> nodeList, NodePredicate filterFn) {
        for (int i = nodeList.size() - 1; i >= 0; i--) {
            Element node = nodeList.get(i);
            if (node.parentNode() != null) {
                if (filterFn == null || filterFn.test(node)) {
                    node.remove();
                }
            }
        }
    }

    private void replaceNodeTags(List<Element> nodeList, String newTagName) {
        for (Element node : nodeList) {
            setNodeTag(node, newTagName);
        }
    }

    /**
     * Removes the class="" attribute from every element in the given subtree, except those that
     * match CLASSES_TO_PRESERVE and the classesToPreserve option.
     */
    private void cleanClasses(Element root) {
        for (Element node : root.getAllElements()) {
            StringBuilder className = new StringBuilder();
            String cls = attr(node, "class");
            for (String c : JsCompat.WS_RUN.split(cls == null ? "" : cls, -1)) {
                if (classesToPreserve.contains(c)) {
                    if (className.length() > 0) {
                        className.append(' ');
                    }
                    className.append(c);
                }
            }
            if (className.length() > 0) {
                node.attr("class", className.toString());
            } else {
                node.removeAttr("class");
            }
        }
    }

    /**
     * Converts each &lt;a&gt; and &lt;img&gt; uri in the given element to an absolute URI,
     * ignoring #ref URIs.
     */
    private void fixRelativeUris(Element articleContent) {
        for (Element link : byTag(articleContent, "a")) {
            String href = attr(link, "href");
            if (truthy(href)) {
                // Remove links with javascript: URIs, since they won't work after scripts have been removed from the page.
                if (href.startsWith("javascript:")) {
                    if (link.childNodeSize() == 1 && isText(link.childNode(0))) {
                        link.replaceWith(new org.jsoup.nodes.TextNode(textContent(link)));
                    } else {
                        Element container = doc.createElement("span");
                        while (link.firstChild() != null) {
                            container.appendChild(link.firstChild());
                        }
                        link.replaceWith(container);
                    }
                } else {
                    link.attr("href", toAbsoluteUri(href));
                }
            }
        }

        for (Element media : byTag(articleContent, Set.of("img", "picture", "figure", "video", "audio", "source"))) {
            String src = attr(media, "src");
            String poster = attr(media, "poster");
            String srcset = attr(media, "srcset");

            if (truthy(src)) {
                media.attr("src", toAbsoluteUri(src));
            }
            if (truthy(poster)) {
                media.attr("poster", toAbsoluteUri(poster));
            }
            if (truthy(srcset)) {
                Matcher m = SRCSET_URL.matcher(srcset);
                StringBuilder sb = new StringBuilder();
                while (m.find()) {
                    String repl = toAbsoluteUri(m.group(1)) + (m.group(2) == null ? "" : m.group(2)) + m.group(3);
                    m.appendReplacement(sb, Matcher.quoteReplacement(repl));
                }
                m.appendTail(sb);
                media.attr("srcset", sb.toString());
            }
        }
    }

    private String toAbsoluteUri(String uri) {
        // Leave hash links alone if the base URI matches the document URI:
        if (baseUri != null && baseUri.equals(documentUri) && uri.startsWith("#")) {
            return uri;
        }
        String resolved = JsCompat.resolveUrl(baseUri, uri);
        return resolved != null ? resolved : uri;
    }

    private void simplifyNestedElements(Element articleContent) {
        Element node = articleContent;

        while (node != null) {
            if (node.parentNode() != null && (is(node, "div") || is(node, "section"))
                    && !(truthy(attr(node, "id")) && node.attr("id").startsWith("readability"))) {
                if (isElementWithoutContent(node)) {
                    node = removeAndGetNext(node);
                    continue;
                } else if (hasSingleTagInsideElement(node, "div") || hasSingleTagInsideElement(node, "section")) {
                    Element child = node.child(0);
                    for (Attribute a : node.attributes().asList()) {
                        child.attr(a.getKey(), a.getValue());
                    }
                    node.replaceWith(child);
                    node = child;
                    continue;
                }
            }
            node = getNextNode(node, false);
        }
    }

    /**
     * Get the article title as an H1.
     */
    private String getArticleTitle() {
        String curTitle = documentTitle();
        String origTitle = curTitle;

        boolean titleHadHierarchicalSeparators = false;

        // If there's a separator in the title, first remove the final part
        if (TITLE_SEPARATOR.matcher(curTitle).find()) {
            titleHadHierarchicalSeparators = TITLE_HIERARCHICAL_SEPARATOR.matcher(curTitle).find();
            Matcher m = TITLE_SEPARATOR.matcher(origTitle);
            int last = -1;
            while (m.find()) {
                last = m.start();
            }
            curTitle = origTitle.substring(0, last);

            // If the resulting title is too short, remove the first part instead:
            if (wordCount(curTitle) < 3) {
                curTitle = TITLE_FIRST_PART.matcher(origTitle).replaceFirst("");
            }
        } else if (curTitle.contains(": ")) {
            // Check if we have an heading containing this exact string, so we could assume it's the full title.
            String trimmedTitle = trim(curTitle);
            boolean match = false;
            for (Element heading : byTag(doc, Set.of("h1", "h2"))) {
                if (trim(textContent(heading)).equals(trimmedTitle)) {
                    match = true;
                    break;
                }
            }

            // If we don't, let's extract the title out of the original title string.
            if (!match) {
                curTitle = origTitle.substring(origTitle.lastIndexOf(':') + 1);

                // If the title is now too short, try the first colon instead:
                if (wordCount(curTitle) < 3) {
                    curTitle = origTitle.substring(origTitle.indexOf(':') + 1);
                    // But if we have too many words before the colon there's something weird
                    // with the titles and the H tags so let's just use the original title instead
                } else if (wordCount(origTitle.substring(0, origTitle.indexOf(':'))) > 5) {
                    curTitle = origTitle;
                }
            }
        } else if (curTitle.length() > 150 || curTitle.length() < 15) {
            List<Element> hOnes = byTag(doc, "h1");
            if (hOnes.size() == 1) {
                curTitle = getInnerText(hOnes.get(0), true);
            }
        }

        curTitle = NORMALIZE.matcher(trim(curTitle)).replaceAll(" ");
        // If we now have 4 words or fewer as our title, and either no 'hierarchical' separators (\, /, > or ») were found in
        // the original title or we decreased the number of words by more than 1 word, use the original title.
        int curTitleWordCount = wordCount(curTitle);
        if (curTitleWordCount <= 4
                && (!titleHadHierarchicalSeparators
                || curTitleWordCount != wordCount(TITLE_SEPARATOR.matcher(origTitle).replaceAll("")) - 1)) {
            curTitle = origTitle;
        }

        return curTitle;
    }

    private static int wordCount(String str) {
        return JsCompat.splitLength(str, JsCompat.WS_RUN);
    }

    /** DOM {@code document.title}, trimmed. */
    private String documentTitle() {
        Element title = Dom.firstByTag(doc, "title");
        if (title == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Node child : title.childNodes()) {
            if (isText(child)) {
                sb.append(textContent(child));
            }
        }
        // strip and collapse ASCII whitespace
        return trim(sb.toString().replaceAll("[\\t\\n\\f\\r ]+", " ").replaceAll("^ | $", ""));
    }

    /**
     * Prepare the HTML document for readability to scrape it. This includes things like stripping
     * javascript, CSS, and handling terrible markup.
     */
    private void prepDocument() {
        // Remove all style tags in head
        removeNodes(byTag(doc, "style"), null);

        if (doc.body() != null) {
            replaceBrs(doc.body());
        }

        replaceNodeTags(byTag(doc, "font"), "span");
    }

    /**
     * Finds the next node, starting from the given node, and ignoring whitespace in between. If
     * the given node is an element, the same node is returned.
     */
    private Node nextNode(Node node) {
        Node next = node;
        while (next != null && !(next instanceof Element) && WHITESPACE.matcher(textContent(next)).find()) {
            next = next.nextSibling();
        }
        return next;
    }

    /**
     * Replaces 2 or more successive &lt;br&gt; elements with a single &lt;p&gt;. Whitespace
     * between &lt;br&gt; elements are ignored.
     */
    private void replaceBrs(Element elem) {
        for (Element br : byTag(elem, "br")) {
            Node next = br.nextSibling();

            // Whether 2 or more <br> elements have been found and replaced with a <p> block.
            boolean replaced = false;

            // If we find a <br> chain, remove the <br>s until we hit another node or non-whitespace. This leaves behind
            // the first <br> in the chain (which will be replaced with a <p> later).
            while ((next = nextNode(next)) != null && is(next, "br")) {
                replaced = true;
                Node brSibling = next.nextSibling();
                next.remove();
                next = brSibling;
            }

            // If we removed a <br> chain, replace the remaining <br> with a <p>. Add all sibling nodes as children of the
            // <p> until we hit another <br> chain.
            if (replaced) {
                Element p = doc.createElement("p");
                br.replaceWith(p);

                next = p.nextSibling();
                while (next != null) {
                    // If we've hit another <br><br>, we're done adding children to this <p>.
                    if (is(next, "br")) {
                        Node nextElem = nextNode(next.nextSibling());
                        if (nextElem != null && is(nextElem, "br")) {
                            break;
                        }
                    }

                    if (!isPhrasingContent(next)) {
                        break;
                    }

                    // Otherwise, make this node a child of the new <p>.
                    Node sibling = next.nextSibling();
                    p.appendChild(next);
                    next = sibling;
                }

                while (p.lastChild() != null && isWhitespace(p.lastChild())) {
                    p.lastChild().remove();
                }

                if (is(p.parentNode(), "p")) {
                    setNodeTag(p.parent(), "div");
                }
            }
        }
    }

    /** Renames in place, as upstream does under JSDOMParser, so scores stay attached. */
    private Element setNodeTag(Element node, String tag) {
        node.tagName(lower(tag));
        return node;
    }

    /**
     * Prepare the article node for display. Clean out any inline styles, iframes, forms, strip
     * extraneous &lt;p&gt; tags, etc.
     */
    private void prepArticle(Element articleContent) {
        cleanStyles(articleContent);

        // Check for data tables before we continue, to avoid removing items in those tables, which will often be isolated
        // even though they're visually linked to other content-ful elements (text, images, etc.).
        markDataTables(articleContent);

        fixLazyImages(articleContent);

        // Clean out junk from the article content
        cleanConditionally(articleContent, "form");
        cleanConditionally(articleContent, "fieldset");
        clean(articleContent, "object");
        clean(articleContent, "embed");
        clean(articleContent, "footer");
        clean(articleContent, "link");
        clean(articleContent, "aside");

        // Clean out elements with little content that have "share" in their id/class combinations from final top
        // candidates, which means we don't remove the top candidates even they have "share".
        int shareElementThreshold = ReadabilityOptions.DEFAULT_CHAR_THRESHOLD;

        for (Element topCandidate : articleContent.children()) {
            cleanMatchedNodes(topCandidate, (node, matchString) ->
                    SHARE_ELEMENTS.matcher(matchString).find() && textContent(node).length() < shareElementThreshold);
        }

        clean(articleContent, "iframe");
        clean(articleContent, "input");
        clean(articleContent, "textarea");
        clean(articleContent, "select");
        clean(articleContent, "button");
        cleanHeaders(articleContent);

        // Do these last as the previous stuff may have removed junk that will affect these
        cleanConditionally(articleContent, "table");
        cleanConditionally(articleContent, "ul");
        cleanConditionally(articleContent, "div");

        // replace H1 with H2 as H1 should be only title that is displayed separately
        replaceNodeTags(byTag(articleContent, "h1"), "h2");

        // Remove extra paragraphs
        removeNodes(byTag(articleContent, "p"), paragraph -> {
            // At this point, nasty iframes have been removed; only embedded video ones remain.
            int contentElementCount = byTag(paragraph, Set.of("img", "embed", "object", "iframe")).size();
            return contentElementCount == 0 && getInnerText(paragraph, false).isEmpty();
        });

        for (Element br : byTag(articleContent, "br")) {
            Node next = nextNode(br.nextSibling());
            if (next != null && is(next, "p")) {
                br.remove();
            }
        }

        // Remove single-cell tables
        for (Element table : byTag(articleContent, "table")) {
            Element tbody = hasSingleTagInsideElement(table, "tbody") ? table.firstElementChild() : table;
            if (hasSingleTagInsideElement(tbody, "tr")) {
                Element row = tbody.firstElementChild();
                if (hasSingleTagInsideElement(row, "td")) {
                    Element cell = row.firstElementChild();
                    boolean allPhrasing = true;
                    for (Node c : cell.childNodes()) {
                        if (!isPhrasingContent(c)) {
                            allPhrasing = false;
                            break;
                        }
                    }
                    cell = setNodeTag(cell, allPhrasing ? "p" : "div");
                    if (table.parentNode() != null) {
                        table.replaceWith(cell);
                    }
                }
            }
        }
    }

    /**
     * Initialize a node with the readability object. Also checks the className/id for special
     * names to add to its score.
     */
    private void initializeNode(Element node) {
        double score = 0;
        switch (node.normalName()) {
            case "div":
                score += 5;
                break;
            case "pre":
            case "td":
            case "blockquote":
                score += 3;
                break;
            case "address":
            case "ol":
            case "ul":
            case "dl":
            case "dd":
            case "dt":
            case "li":
            case "form":
                score -= 3;
                break;
            case "h1":
            case "h2":
            case "h3":
            case "h4":
            case "h5":
            case "h6":
            case "th":
                score -= 5;
                break;
            default:
                break;
        }
        score += getClassWeight(node);
        scores.put(node, new double[]{score});
    }

    private double score(Element e) {
        return scores.get(e)[0];
    }

    private Element removeAndGetNext(Element node) {
        Element nextNode = getNextNode(node, true);
        node.remove();
        return nextNode;
    }

    /**
     * Traverse the DOM from node to node, starting at the node passed in. Pass true for the second
     * parameter to indicate this node itself (and its kids) are going away, and we want the next
     * node over. Calling this in a loop will traverse the DOM depth-first.
     */
    private static Element getNextNode(Element node, boolean ignoreSelfAndKids) {
        // First check for kids if those aren't being ignored
        if (!ignoreSelfAndKids && node.firstElementChild() != null) {
            return node.firstElementChild();
        }
        // Then for siblings...
        if (node.nextElementSibling() != null) {
            return node.nextElementSibling();
        }
        // And finally, move up the parent chain *and* find a sibling (because this is depth-first traversal, we will have
        // already seen the parent nodes themselves).
        do {
            node = node.parent();
        } while (node != null && node.nextElementSibling() == null);
        return node == null ? null : node.nextElementSibling();
    }

    /**
     * Compares second text to first one: 1 = same text, 0 = completely different text. Splits
     * both texts into words and finds words that are unique in the second text.
     */
    private static double textSimilarity(String textA, String textB) {
        List<String> tokensA = tokens(textA);
        List<String> tokensB = tokens(textB);
        if (tokensA.isEmpty() || tokensB.isEmpty()) {
            return 0;
        }
        List<String> uniqTokensB = new ArrayList<>();
        for (String t : tokensB) {
            if (!tokensA.contains(t)) {
                uniqTokensB.add(t);
            }
        }
        double distanceB = (double) String.join(" ", uniqTokensB).length() / String.join(" ", tokensB).length();
        return 1 - distanceB;
    }

    private static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        for (String t : TOKENIZE.split(lower(text), -1)) {
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /**
     * Checks whether an element node contains a valid byline.
     */
    private boolean isValidByline(Element node, String matchString) {
        String rel = attr(node, "rel");
        String itemprop = attr(node, "itemprop");
        int bylineLength = trim(textContent(node)).length();

        return ("author".equals(rel) || (truthy(itemprop) && itemprop.contains("author")) || BYLINE.matcher(matchString).find())
                && bylineLength > 0 && bylineLength < 100;
    }

    /** Ancestors nearest first; like the DOM, the document node is the last one. */
    private static List<Element> getNodeAncestors(Element node, int maxDepth) {
        int i = 0;
        List<Element> ancestors = new ArrayList<>();
        while (node.parent() != null) {
            ancestors.add(node.parent());
            if (maxDepth > 0 && ++i == maxDepth) {
                break;
            }
            node = node.parent();
        }
        return ancestors;
    }

    private static String matchString(Element node) {
        return node.attr("class") + " " + node.attr("id");
    }

    private static boolean isBody(Element e) {
        return e == null || is(e, "body");
    }

    /**
     * Using a variety of metrics (content score, classname, element types), find the content that
     * is most likely to be the stuff a user wants to read. Then return it wrapped up in a div.
     */
    @SuppressWarnings("checkstyle:MethodLength")
    private Element grabArticle() {
        // upstream's `page !== null` is true for its no-argument call
        boolean isPaging = true;
        Element page = doc.body();

        // We can't grab an article if we don't have a page!
        if (page == null) {
            return null;
        }

        // upstream round-trips page.innerHTML; a clone keeps the exact tree
        Element pageCache = page.clone();

        while (true) {
            boolean stripUnlikelyCandidates = flagIsActive(FLAG_STRIP_UNLIKELYS);

            // First, node prepping. Trash nodes that look cruddy (like ones with the class name "comment", etc), and turn
            // divs into P tags where they have been used inappropriately (as in, where they contain no other block level
            // elements.)
            List<Element> elementsToScore = new ArrayList<>();
            Element node = doc.firstElementChild();

            boolean shouldRemoveTitleHeader = true;

            while (node != null) {
                if (is(node, "html")) {
                    articleLang = attr(node, "lang");
                }

                String matchString = matchString(node);

                if (!isProbablyVisible(node)) {
                    node = removeAndGetNext(node);
                    continue;
                }

                // User is not able to see elements applied with both "aria-modal = true" and "role = dialog"
                if ("true".equals(attr(node, "aria-modal")) && "dialog".equals(attr(node, "role"))) {
                    node = removeAndGetNext(node);
                    continue;
                }

                // If we don't have a byline yet check to see if this node is a byline; if it is store the byline and
                // remove the node.
                if (!truthy(articleByline) && !truthy(metadata.get("byline")) && isValidByline(node, matchString)) {
                    // Find child node matching [itemprop="name"] and use that if it exists for a more accurate author name byline
                    Element endOfSearchMarkerNode = getNextNode(node, true);
                    Element next = getNextNode(node, false);
                    Element itemPropNameNode = null;
                    while (next != null && next != endOfSearchMarkerNode) {
                        String itemprop = attr(next, "itemprop");
                        if (truthy(itemprop) && itemprop.contains("name")) {
                            itemPropNameNode = next;
                            break;
                        } else {
                            next = getNextNode(next, false);
                        }
                    }
                    articleByline = trim(textContent(itemPropNameNode != null ? itemPropNameNode : node));
                    node = removeAndGetNext(node);
                    continue;
                }

                if (shouldRemoveTitleHeader && headerDuplicatesTitle(node)) {
                    shouldRemoveTitleHeader = false;
                    node = removeAndGetNext(node);
                    continue;
                }

                // Remove unlikely candidates
                if (stripUnlikelyCandidates) {
                    if (UNLIKELY_CANDIDATES.matcher(matchString).find()
                            && !OK_MAYBE_ITS_A_CANDIDATE.matcher(matchString).find()
                            && !hasAncestorTag(node, "table", 0, null)
                            && !hasAncestorTag(node, "code", 0, null)
                            && !is(node, "body")
                            && !is(node, "a")) {
                        node = removeAndGetNext(node);
                        continue;
                    }

                    String role = attr(node, "role");
                    if (role != null && UNLIKELY_ROLES.contains(role)) {
                        node = removeAndGetNext(node);
                        continue;
                    }
                }

                // Remove DIV, SECTION, and HEADER nodes without any content(e.g. text, image, video, or iframe).
                switch (node.normalName()) {
                    case "div":
                    case "section":
                    case "header":
                    case "h1":
                    case "h2":
                    case "h3":
                    case "h4":
                    case "h5":
                    case "h6":
                        if (isElementWithoutContent(node)) {
                            node = removeAndGetNext(node);
                            continue;
                        }
                        break;
                    default:
                        break;
                }

                if (DEFAULT_TAGS_TO_SCORE.contains(node.normalName())) {
                    elementsToScore.add(node);
                }

                // Turn all divs that don't have children block level elements into p's
                if (is(node, "div")) {
                    // Put phrasing content into paragraphs.
                    wrapPhrasingRuns(node);

                    // Sites like http://mobile.slate.com encloses each paragraph with a DIV element. DIVs with only a P
                    // element inside and no text content can be safely converted into plain P elements to avoid confusing
                    // the scoring algorithm with DIVs with are, in practice, paragraphs.
                    if (hasSingleTagInsideElement(node, "p") && getLinkDensity(node) < 0.25) {
                        Element newNode = node.child(0);
                        node.replaceWith(newNode);
                        node = newNode;
                        elementsToScore.add(node);
                    } else if (!hasChildBlockElement(node)) {
                        node = setNodeTag(node, "p");
                        elementsToScore.add(node);
                    }
                }
                node = getNextNode(node, false);
            }

            // Loop through all paragraphs, and assign a score to them based on how content-y they look. Then add their
            // score to their parent node. A score is determined by things like number of commas, class names, etc.
            List<Element> candidates = new ArrayList<>();
            for (Element elementToScore : elementsToScore) {
                if (!Dom.hasElementParent(elementToScore)) {
                    continue;
                }

                // If this paragraph is less than 25 characters, don't even count it.
                String innerText = getInnerText(elementToScore, true);
                if (innerText.length() < 25) {
                    continue;
                }

                // Exclude nodes with no ancestor.
                List<Element> ancestors = getNodeAncestors(elementToScore, 5);
                if (ancestors.isEmpty()) {
                    continue;
                }

                double contentScore = 0;

                // Add a point for the paragraph itself as a base.
                contentScore += 1;

                // Add points for any commas within this paragraph.
                contentScore += JsCompat.splitLength(innerText, COMMAS);

                // For every 100 characters in this paragraph, add another point. Up to 3 points.
                contentScore += Math.min(Math.floor(innerText.length() / 100.0), 3);

                // Initialize and score ancestors.
                for (int level = 0; level < ancestors.size(); level++) {
                    Element ancestor = ancestors.get(level);
                    if (ancestor instanceof Document || !Dom.hasElementParent(ancestor)) {
                        continue;
                    }

                    if (!scores.containsKey(ancestor)) {
                        initializeNode(ancestor);
                        candidates.add(ancestor);
                    }

                    // Node score divider:
                    // - parent:             1 (no division)
                    // - grandparent:        2
                    // - great grandparent+: ancestor level * 3
                    double scoreDivider;
                    if (level == 0) {
                        scoreDivider = 1;
                    } else if (level == 1) {
                        scoreDivider = 2;
                    } else {
                        scoreDivider = level * 3;
                    }
                    scores.get(ancestor)[0] += contentScore / scoreDivider;
                }
            }

            // After we've calculated scores, loop through all of the possible candidate nodes we found and find the one
            // with the highest score.
            List<Element> topCandidates = new ArrayList<>();
            for (Element candidate : candidates) {
                // Scale the final candidates score based on link density. Good content should have a relatively small
                // link density (5% or less) and be mostly unaffected by this operation.
                double candidateScore = score(candidate) * (1 - getLinkDensity(candidate));
                scores.get(candidate)[0] = candidateScore;

                for (int t = 0; t < nbTopCandidates; t++) {
                    Element aTopCandidate = t < topCandidates.size() ? topCandidates.get(t) : null;

                    if (aTopCandidate == null || candidateScore > score(aTopCandidate)) {
                        topCandidates.add(t, candidate);
                        if (topCandidates.size() > nbTopCandidates) {
                            topCandidates.remove(topCandidates.size() - 1);
                        }
                        break;
                    }
                }
            }

            Element topCandidate = topCandidates.isEmpty() ? null : topCandidates.get(0);
            boolean neededToCreateTopCandidate = false;
            Element parentOfTopCandidate;

            // If we still have no top candidate, just use the body as a last resort. We also have to copy the body node
            // so it is something we can modify.
            if (topCandidate == null || is(topCandidate, "body")) {
                // Move all of the page's children into topCandidate
                topCandidate = doc.createElement("div");
                neededToCreateTopCandidate = true;
                // Move everything (not just elements, also text nodes etc.) into the container so we even include text
                // directly in the body:
                while (page.firstChild() != null) {
                    topCandidate.appendChild(page.firstChild());
                }

                page.appendChild(topCandidate);

                initializeNode(topCandidate);
            } else {
                // Find a better top candidate node if it contains (at least three) nodes which belong to `topCandidates`
                // array and whose scores are quite closed with current `topCandidate` node.
                List<List<Element>> alternativeCandidateAncestors = new ArrayList<>();
                for (int i = 1; i < topCandidates.size(); i++) {
                    if (score(topCandidates.get(i)) / score(topCandidate) >= 0.75) {
                        alternativeCandidateAncestors.add(getNodeAncestors(topCandidates.get(i), 0));
                    }
                }
                final int minimumTopCandidates = 3;
                if (alternativeCandidateAncestors.size() >= minimumTopCandidates) {
                    parentOfTopCandidate = topCandidate.parent();
                    while (!isBody(parentOfTopCandidate)) {
                        int listsContainingThisAncestor = 0;
                        for (int ancestorIndex = 0;
                                ancestorIndex < alternativeCandidateAncestors.size() && listsContainingThisAncestor < minimumTopCandidates;
                                ancestorIndex++) {
                            if (containsIdentity(alternativeCandidateAncestors.get(ancestorIndex), parentOfTopCandidate)) {
                                listsContainingThisAncestor++;
                            }
                        }
                        if (listsContainingThisAncestor >= minimumTopCandidates) {
                            topCandidate = parentOfTopCandidate;
                            break;
                        }
                        parentOfTopCandidate = parentOfTopCandidate.parent();
                    }
                }
                if (!scores.containsKey(topCandidate)) {
                    initializeNode(topCandidate);
                }

                // Because of our bonus system, parents of candidates might have scores themselves. They get half of the
                // node. There won't be nodes with higher scores than our topCandidate, but if we see the score going *up*
                // in the first few steps up the tree, that's a decent sign that there might be more content lurking in
                // other places that we want to unify in. The sibling stuff below does some of that - but only if we've
                // looked high enough up the DOM tree.
                parentOfTopCandidate = topCandidate.parent();
                double lastScore = score(topCandidate);
                // The scores shouldn't get too low.
                double scoreThreshold = lastScore / 3;
                while (!isBody(parentOfTopCandidate)) {
                    if (!scores.containsKey(parentOfTopCandidate)) {
                        parentOfTopCandidate = parentOfTopCandidate.parent();
                        continue;
                    }
                    double parentScore = score(parentOfTopCandidate);
                    if (parentScore < scoreThreshold) {
                        break;
                    }
                    if (parentScore > lastScore) {
                        // Alright! We found a better parent to use.
                        topCandidate = parentOfTopCandidate;
                        break;
                    }
                    lastScore = score(parentOfTopCandidate);
                    parentOfTopCandidate = parentOfTopCandidate.parent();
                }

                // If the top candidate is the only child, use parent instead. This will help sibling joining logic when
                // adjacent content is actually located in parent's sibling node.
                parentOfTopCandidate = topCandidate.parent();
                while (!isBody(parentOfTopCandidate) && parentOfTopCandidate.childrenSize() == 1) {
                    topCandidate = parentOfTopCandidate;
                    parentOfTopCandidate = topCandidate.parent();
                }
                if (!scores.containsKey(topCandidate)) {
                    initializeNode(topCandidate);
                }
            }

            // Now that we have the top candidate, look through its siblings for content that might also be related.
            // Things like preambles, content split by ads that we removed, etc.
            Element articleContent = doc.createElement("div");
            if (isPaging) {
                articleContent.attr("id", "readability-content");
            }

            double siblingScoreThreshold = Math.max(10, score(topCandidate) * 0.2);
            // Keep potential top candidate's parent node to try to get text direction of it later.
            parentOfTopCandidate = topCandidate.parent();
            List<Element> siblings = parentOfTopCandidate.children();

            for (int s = 0, sl = siblings.size(); s < sl; s++) {
                Element sibling = siblings.get(s);
                boolean append = false;

                if (sibling == topCandidate) {
                    append = true;
                } else {
                    double contentBonus = 0;

                    // Give a bonus if sibling nodes and top candidates have the example same classname
                    String topClass = topCandidate.attr("class");
                    if (sibling.attr("class").equals(topClass) && !topClass.isEmpty()) {
                        contentBonus += score(topCandidate) * 0.2;
                    }

                    if (scores.containsKey(sibling) && score(sibling) + contentBonus >= siblingScoreThreshold) {
                        append = true;
                    } else if (is(sibling, "p")) {
                        double linkDensity = getLinkDensity(sibling);
                        String nodeContent = getInnerText(sibling, true);
                        int nodeLength = nodeContent.length();

                        if (nodeLength > 80 && linkDensity < 0.25) {
                            append = true;
                        } else if (nodeLength < 80 && nodeLength > 0 && linkDensity == 0
                                && SENTENCE_END.matcher(nodeContent).find()) {
                            append = true;
                        }
                    }
                }

                if (append) {
                    if (!ALTER_TO_DIV_EXCEPTIONS.contains(sibling.normalName())) {
                        // We have a node that isn't a common block level element, like a form or td tag. Turn it into a
                        // div so it doesn't get filtered out later by accident.
                        sibling = setNodeTag(sibling, "div");
                    }

                    articleContent.appendChild(sibling);
                    // Fetch children again; the appended sibling has left the list.
                    siblings = parentOfTopCandidate.children();
                    s -= 1;
                    sl -= 1;
                }
            }

            // So we have all of the content that we need. Now we clean it up for presentation.
            prepArticle(articleContent);

            if (neededToCreateTopCandidate) {
                // We already created a fake div thing, and there wouldn't have been any siblings left for the previous
                // loop, so there's no point trying to create a new div, and then move all the children over. Just assign
                // IDs and class names here. No need to append because that already happened anyway.
                topCandidate.attr("id", "readability-page-1");
                topCandidate.attr("class", "page");
            } else {
                Element div = doc.createElement("div");
                div.attr("id", "readability-page-1");
                div.attr("class", "page");
                while (articleContent.firstChild() != null) {
                    div.appendChild(articleContent.firstChild());
                }
                articleContent.appendChild(div);
            }

            boolean parseSuccessful = true;

            // Now that we've gone through the full algorithm, check to see if we got any meaningful content. If we
            // didn't, we may need to re-run grabArticle with different flags set. This gives us a higher likelihood of
            // finding the content, and the sieve approach gives us a higher likelihood of finding the -right- content.
            int textLength = getInnerText(articleContent, true).length();
            if (textLength < charThreshold) {
                parseSuccessful = false;
                page.empty();
                for (Node child : new ArrayList<>(pageCache.clone().childNodes())) {
                    page.appendChild(child);
                }

                attempts.add(new Attempt(articleContent, textLength));

                if (flagIsActive(FLAG_STRIP_UNLIKELYS)) {
                    removeFlag(FLAG_STRIP_UNLIKELYS);
                } else if (flagIsActive(FLAG_WEIGHT_CLASSES)) {
                    removeFlag(FLAG_WEIGHT_CLASSES);
                } else if (flagIsActive(FLAG_CLEAN_CONDITIONALLY)) {
                    removeFlag(FLAG_CLEAN_CONDITIONALLY);
                } else {
                    // No luck after removing flags, just return the longest text we found during the different loops
                    attempts.sort((a, b) -> Integer.compare(b.textLength(), a.textLength()));

                    // But first check if we actually have something
                    if (attempts.get(0).textLength() == 0) {
                        return null;
                    }

                    articleContent = attempts.get(0).articleContent();
                    parseSuccessful = true;
                }
            }

            if (parseSuccessful) {
                // Find out text direction from ancestors of final top candidate.
                List<Element> ancestors = new ArrayList<>();
                ancestors.add(parentOfTopCandidate);
                ancestors.add(topCandidate);
                ancestors.addAll(getNodeAncestors(parentOfTopCandidate, 0));
                for (Element ancestor : ancestors) {
                    if (ancestor instanceof Document) {
                        continue;
                    }
                    String dir = attr(ancestor, "dir");
                    if (truthy(dir)) {
                        articleDir = dir;
                        break;
                    }
                }
                return articleContent;
            }
        }
    }

    private static boolean containsIdentity(List<Element> list, Element e) {
        for (Element x : list) {
            if (x == e) {
                return true;
            }
        }
        return false;
    }

    /**
     * Converts some of the common HTML entities in string to their corresponding characters.
     */
    static String unescapeHtmlEntities(String str) {
        if (!truthy(str)) {
            return str;
        }
        Matcher m = ESCAPED_ENTITY.matcher(str);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(HTML_ESCAPE_MAP.get(m.group(1))));
        }
        m.appendTail(sb);
        m = NUMERIC_ENTITY.matcher(sb.toString());
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String hex = m.group(1);
            long num;
            try {
                num = hex != null ? Long.parseLong(hex, 16) : Long.parseLong(m.group(2));
            } catch (NumberFormatException e) {
                num = Long.MAX_VALUE;
            }
            // these character references are replaced by a conforming HTML parser
            if (num == 0 || num > 0x10FFFF || (num >= 0xD800 && num <= 0xDFFF)) {
                num = 0xFFFD;
            }
            m.appendReplacement(out, Matcher.quoteReplacement(new String(Character.toChars((int) num))));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** A TypeError upstream: a JSON-LD value of the wrong shape abandons that script. */
    private static final class JsTypeError extends RuntimeException {
        JsTypeError() {
            super(null, null, false, false);
        }
    }

    /** JS {@code obj[key]}: null for undefined; throws where JS throws (null receiver). */
    private static Object prop(Object obj, String key) {
        if (obj == MiniJson.NULL || obj == null) {
            throw new JsTypeError();
        }
        if (obj instanceof Map) {
            return ((Map<?, ?>) obj).get(key);
        }
        return null;
    }

    private static boolean jsTruthy(Object v) {
        if (v == null || v == MiniJson.NULL) {
            return false;
        }
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        if (v instanceof Double) {
            double d = (Double) v;
            return d != 0 && !Double.isNaN(d);
        }
        if (v instanceof String) {
            return !((String) v).isEmpty();
        }
        return true;
    }

    /** JS {@code value.match(re)}; a non-string receiver has no match() and throws. */
    private static boolean jsMatch(Object value, Pattern re) {
        if (!(value instanceof String)) {
            throw new JsTypeError();
        }
        return re.matcher((String) value).find();
    }

    private static boolean isArticleType(Object it) {
        Object type = prop(it, "@type");
        return jsTruthy(type) && jsMatch(type, JSON_LD_ARTICLE_TYPES);
    }

    /**
     * Try to extract metadata from JSON-LD object. For now, only Schema.org objects of type
     * Article or its subtypes are supported.
     */
    private Map<String, String> getJsonLd() {
        Map<String, String> found = null;
        for (Element jsonLdElement : byTag(doc, "script")) {
            if (found != null) {
                break;
            }
            if (!"application/ld+json".equals(attr(jsonLdElement, "type"))) {
                continue;
            }
            try {
                // Strip CDATA markers if present
                String content = CDATA.matcher(textContent(jsonLdElement)).replaceAll("");
                Object parsed = MiniJson.parse(content);

                if (parsed instanceof List) {
                    Object match = null;
                    for (Object it : (List<?>) parsed) {
                        if (isArticleType(it)) {
                            match = it;
                            break;
                        }
                    }
                    if (match == null) {
                        continue;
                    }
                    parsed = match;
                }

                Object context = prop(parsed, "@context");
                boolean matches = (context instanceof String && SCHEMA_DOT_ORG.matcher((String) context).find())
                        || ((context instanceof Map || context instanceof List || context == MiniJson.NULL)
                        && prop(context, "@vocab") instanceof String
                        && SCHEMA_DOT_ORG.matcher((String) prop(context, "@vocab")).find());

                if (!matches) {
                    continue;
                }

                if (!jsTruthy(prop(parsed, "@type")) && prop(parsed, "@graph") instanceof List) {
                    Object match = null;
                    for (Object it : (List<?>) prop(parsed, "@graph")) {
                        Object type = prop(it, "@type");
                        if (jsMatch(jsTruthy(type) ? type : "", JSON_LD_ARTICLE_TYPES)) {
                            match = it;
                            break;
                        }
                    }
                    parsed = match;
                }

                if (parsed == null || !jsTruthy(prop(parsed, "@type")) || !jsMatch(prop(parsed, "@type"), JSON_LD_ARTICLE_TYPES)) {
                    continue;
                }

                Map<String, String> md = new LinkedHashMap<>();

                Object name = prop(parsed, "name");
                Object headline = prop(parsed, "headline");
                if (name instanceof String && headline instanceof String && !name.equals(headline)) {
                    // we have both name and headline element in the JSON-LD. They should both be the same but some websites
                    // like aktualne.cz put their own name into "name" and the article title to "headline" which confuses
                    // Readability. So we try to check if either "name" or "headline" closely matches the html title, and if
                    // so, use that one. If not, then we use "name" by default.
                    String title = getArticleTitle();
                    boolean nameMatches = textSimilarity((String) name, title) > 0.75;
                    boolean headlineMatches = textSimilarity((String) headline, title) > 0.75;

                    if (headlineMatches && !nameMatches) {
                        md.put("title", (String) headline);
                    } else {
                        md.put("title", (String) name);
                    }
                } else if (name instanceof String) {
                    md.put("title", trim((String) name));
                } else if (headline instanceof String) {
                    md.put("title", trim((String) headline));
                }
                Object author = prop(parsed, "author");
                if (jsTruthy(author)) {
                    if (prop(author, "name") instanceof String) {
                        md.put("byline", trim((String) prop(author, "name")));
                    } else if (author instanceof List && !((List<?>) author).isEmpty()
                            && jsTruthy(((List<?>) author).get(0)) && prop(((List<?>) author).get(0), "name") instanceof String) {
                        List<String> names = new ArrayList<>();
                        for (Object a : (List<?>) author) {
                            if (jsTruthy(a) && prop(a, "name") instanceof String) {
                                names.add(trim((String) prop(a, "name")));
                            }
                        }
                        md.put("byline", String.join(", ", names));
                    }
                }
                if (prop(parsed, "description") instanceof String) {
                    md.put("excerpt", trim((String) prop(parsed, "description")));
                }
                Object publisher = prop(parsed, "publisher");
                if (jsTruthy(publisher) && prop(publisher, "name") instanceof String) {
                    md.put("siteName", trim((String) prop(publisher, "name")));
                }
                if (prop(parsed, "datePublished") instanceof String) {
                    md.put("datePublished", trim((String) prop(parsed, "datePublished")));
                }
                found = md;
            } catch (RuntimeException e) {
                // malformed or unexpected JSON-LD: skip this script, as upstream does
            }
        }
        return found != null ? found : new LinkedHashMap<>();
    }

    @SafeVarargs
    private static <T> T or(T... values) {
        for (T v : values) {
            if (v instanceof String ? truthy((String) v) : v != null) {
                return v;
            }
        }
        return null;
    }

    /**
     * Attempts to get excerpt and byline metadata for the article.
     */
    private Map<String, String> getArticleMetadata(Map<String, String> jsonld) {
        Map<String, String> md = new LinkedHashMap<>();
        Map<String, String> values = new LinkedHashMap<>();

        // Find description tags.
        for (Element element : byTag(doc, "meta")) {
            String elementName = attr(element, "name");
            String elementProperty = attr(element, "property");
            String content = attr(element, "content");
            if (!truthy(content)) {
                continue;
            }
            boolean matched = false;

            // property is a space-separated list of values
            if (truthy(elementProperty)) {
                Matcher m = META_PROPERTY.matcher(elementProperty);
                if (m.find()) {
                    matched = true;
                    // Convert to lowercase, and remove any whitespace so we can match below.
                    String name = JsCompat.removeWhitespace(lower(m.group()));
                    values.put(name, trim(content));
                }
            }
            // name is a single value
            if (!matched && truthy(elementName) && META_NAME.matcher(elementName).find()) {
                // Convert to lowercase, remove any whitespace, and convert dots to colons so we can match below.
                String name = JsCompat.removeWhitespace(lower(elementName)).replace('.', ':');
                values.put(name, trim(content));
            }
        }

        // get title
        md.put("title", or(jsonld.get("title"), values.get("dc:title"), values.get("dcterm:title"), values.get("og:title"),
                values.get("weibo:article:title"), values.get("weibo:webpage:title"), values.get("title"),
                values.get("twitter:title"), values.get("parsely-title")));

        if (!truthy(md.get("title"))) {
            md.put("title", getArticleTitle());
        }

        String articleAuthor = values.get("article:author") != null && !JsCompat.isAbsoluteUrl(values.get("article:author"))
                ? values.get("article:author") : null;

        // get author
        md.put("byline", or(jsonld.get("byline"), values.get("dc:creator"), values.get("dcterm:creator"), values.get("author"),
                values.get("parsely-author"), articleAuthor));

        // get description
        md.put("excerpt", or(jsonld.get("excerpt"), values.get("dc:description"), values.get("dcterm:description"),
                values.get("og:description"), values.get("weibo:article:description"), values.get("weibo:webpage:description"),
                values.get("description"), values.get("twitter:description")));

        // get site name
        md.put("siteName", or(jsonld.get("siteName"), values.get("og:site_name")));

        // get article published time
        md.put("publishedTime", or(jsonld.get("datePublished"), values.get("article:published_time"),
                values.get("parsely-pub-date")));

        // in many sites the meta value is escaped with HTML entities, so here we need to unescape it
        md.replaceAll((k, v) -> unescapeHtmlEntities(v));

        return md;
    }

    /**
     * Check if node is image, or if node contains exactly only one image whether as a direct child
     * or as its descendants.
     */
    private boolean isSingleImage(Element node) {
        while (node != null) {
            if (is(node, "img")) {
                return true;
            }
            if (node.childrenSize() != 1 || !trim(textContent(node)).isEmpty()) {
                return false;
            }
            node = node.child(0);
        }
        return false;
    }

    /**
     * Find all &lt;noscript&gt; that are located after &lt;img&gt; nodes, and which contain only
     * one &lt;img&gt; element. Replace the first image with the image from inside the
     * &lt;noscript&gt; tag, and remove the &lt;noscript&gt; tag. This improves the quality of the
     * images we use on some sites (e.g. Medium).
     */
    private void unwrapNoscriptImages() {
        // Find img without source or attributes that might contains image, and remove it. This is done to prevent a
        // placeholder img is replaced by img from noscript in next step.
        for (Element img : byTag(doc, "img")) {
            boolean keep = false;
            for (Attribute a : img.attributes()) {
                switch (a.getKey()) {
                    case "src":
                    case "srcset":
                    case "data-src":
                    case "data-srcset":
                        keep = true;
                        break;
                    default:
                        break;
                }
                if (keep || IMAGE_EXT.matcher(a.getValue()).find()) {
                    keep = true;
                    break;
                }
            }
            if (!keep) {
                img.remove();
            }
        }

        // Next find noscript and try to extract its image
        for (Element noscript : byTag(doc, "noscript")) {
            // Parse content of noscript and make sure it only contains image
            if (!isSingleImage(noscript)) {
                continue;
            }
            Element tmp = doc.createElement("div");
            for (Node c : noscript.childNodes()) {
                tmp.appendChild(c.clone());
            }

            // If noscript has previous sibling and it only contains image, replace it with noscript content. However we
            // also keep old attributes that might contains image.
            Element prevElement = noscript.previousElementSibling();
            if (prevElement != null && isSingleImage(prevElement)) {
                Element prevImg = prevElement;
                if (!is(prevImg, "img")) {
                    prevImg = Dom.firstByTag(prevElement, "img");
                }

                Element newImg = Dom.firstByTag(tmp, "img");
                for (Attribute a : prevImg.attributes().asList()) {
                    if (a.getValue().isEmpty()) {
                        continue;
                    }

                    if (a.getKey().equals("src") || a.getKey().equals("srcset") || IMAGE_EXT.matcher(a.getValue()).find()) {
                        if (a.getValue().equals(attr(newImg, a.getKey()))) {
                            continue;
                        }

                        String attrName = a.getKey();
                        if (newImg.hasAttr(attrName)) {
                            attrName = "data-old-" + attrName;
                        }

                        newImg.attr(attrName, a.getValue());
                    }
                }

                prevElement.replaceWith(tmp.firstElementChild());
            }
        }
    }

    /**
     * Removes script tags from the document.
     */
    private void removeScripts() {
        removeNodes(byTag(doc, Set.of("script", "noscript")), null);
    }

    /**
     * Check if this node has only whitespace and a single element with given tag. Returns false if
     * the DIV node contains non-empty text nodes or if it contains no element with given tag or
     * more than 1 element.
     */
    private boolean hasSingleTagInsideElement(Element element, String tag) {
        // There should be exactly 1 element child with given tag
        if (element.childrenSize() != 1 || !is(element.child(0), tag)) {
            return false;
        }

        // And there should be no text nodes with real content
        for (Node node : element.childNodes()) {
            if (isText(node) && HAS_CONTENT.matcher(textContent(node)).find()) {
                return false;
            }
        }
        return true;
    }

    private boolean isElementWithoutContent(Element node) {
        return trim(textContent(node)).isEmpty()
                && (node.childrenSize() == 0
                || node.childrenSize() == byTag(node, "br").size() + byTag(node, "hr").size());
    }

    /**
     * Determine whether element has any children block level elements.
     */
    private static boolean hasChildBlockElement(Element element) {
        for (Element e : element.getAllElements()) {
            if (e != element && DIV_TO_P_ELEMS.contains(e.normalName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determine if a node qualifies as phrasing content.
     * https://developer.mozilla.org/en-US/docs/Web/Guide/HTML/Content_categories#Phrasing_content
     */
    /**
     * Wraps each run of phrasing children of {@code div} in a {@code <p>}, dropping whitespace at run
     * edges and runs that are only whitespace. Rebuilds the child list in one pass: moving children
     * one at a time, as upstream does, re-indexes every jsoup sibling per move.
     */
    private void wrapPhrasingRuns(Element div) {
        List<Node> old = new ArrayList<>(div.childNodes());
        boolean any = false;
        for (Node c : old) {
            if (isPhrasingContent(c)) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }
        div.empty();
        List<Node> out = new ArrayList<>(old.size());
        int i = 0;
        while (i < old.size()) {
            if (!isPhrasingContent(old.get(i))) {
                out.add(old.get(i++));
                continue;
            }
            int start = i;
            while (i < old.size() && isPhrasingContent(old.get(i))) {
                i++;
            }
            int end = i;
            while (start < end && isWhitespace(old.get(start))) {
                start++;
            }
            while (end > start && isWhitespace(old.get(end - 1))) {
                end--;
            }
            if (start < end) {
                Element p = doc.createElement("p");
                p.appendChildren(old.subList(start, end));
                out.add(p);
            }
        }
        div.appendChildren(out);
    }

    private static boolean isPhrasingContent(Node node) {
        if (isText(node)) {
            return true;
        }
        if (!(node instanceof Element)) {
            return false;
        }
        String tag = ((Element) node).normalName();
        if (PHRASING_ELEMS.contains(tag)) {
            return true;
        }
        if (tag.equals("a") || tag.equals("del") || tag.equals("ins")) {
            for (Node c : node.childNodes()) {
                if (!isPhrasingContent(c)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private boolean isWhitespace(Node node) {
        return (isText(node) && trim(textContent(node)).isEmpty()) || is(node, "br");
    }

    /**
     * Get the inner text of a node. This also strips out any excess whitespace to be found.
     */
    private String getInnerText(Element e, boolean normalizeSpaces) {
        String text = trim(textContent(e));
        if (normalizeSpaces) {
            return NORMALIZE.matcher(text).replaceAll(" ");
        }
        return text;
    }

    /**
     * Get the number of times a string s appears in the node e.
     */
    private int getCharCount(Element e, char s) {
        String text = getInnerText(e, true);
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == s) {
                n++;
            }
        }
        return n;
    }

    /**
     * Remove the style attribute on every e and under.
     */
    private void cleanStyles(Element root) {
        NodeTraversor.filter(new NodeFilter() {
            @Override
            public FilterResult head(Node node, int depth) {
                if (!(node instanceof Element)) {
                    return FilterResult.SKIP_ENTIRELY;
                }
                Element e = (Element) node;
                if (lower(e.tagName()).equals("svg")) {
                    return FilterResult.SKIP_ENTIRELY;
                }
                // Remove `style` and deprecated presentational attributes
                for (String a : PRESENTATIONAL_ATTRIBUTES) {
                    e.removeAttr(a);
                }
                if (DEPRECATED_SIZE_ATTRIBUTE_ELEMS.contains(e.normalName())) {
                    e.removeAttr("width");
                    e.removeAttr("height");
                }
                return FilterResult.CONTINUE;
            }

            @Override
            public FilterResult tail(Node node, int depth) {
                return FilterResult.CONTINUE;
            }
        }, root);
    }

    /**
     * Get the density of links as a percentage of the content. This is the amount of text that is
     * inside a link divided by the total text in the node.
     */
    private double getLinkDensity(Element element) {
        int textLength = getInnerText(element, true).length();
        if (textLength == 0) {
            return 0;
        }

        double linkLength = 0;
        for (Element linkNode : byTag(element, "a")) {
            String href = attr(linkNode, "href");
            double coefficient = truthy(href) && HASH_URL.matcher(href).find() ? 0.3 : 1;
            linkLength += getInnerText(linkNode, true).length() * coefficient;
        }

        return linkLength / textLength;
    }

    /**
     * Get an elements class/id weight. Uses regular expressions to tell if this element looks good
     * or bad.
     */
    private int getClassWeight(Element e) {
        if (!flagIsActive(FLAG_WEIGHT_CLASSES)) {
            return 0;
        }

        int weight = 0;

        // Look for a special classname
        String className = e.attr("class");
        if (!className.isEmpty()) {
            if (NEGATIVE.matcher(className).find()) {
                weight -= 25;
            }
            if (POSITIVE.matcher(className).find()) {
                weight += 25;
            }
        }

        // Look for a special ID
        String id = e.attr("id");
        if (!id.isEmpty()) {
            if (NEGATIVE.matcher(id).find()) {
                weight -= 25;
            }
            if (POSITIVE.matcher(id).find()) {
                weight += 25;
            }
        }

        return weight;
    }

    /**
     * Clean a node of all elements of type "tag". (Unless it's a youtube/vimeo video. People love
     * movies.)
     */
    private void clean(Element e, String tag) {
        boolean isEmbed = tag.equals("object") || tag.equals("embed") || tag.equals("iframe");

        removeNodes(byTag(e, tag), element -> {
            // Allow youtube and vimeo videos through as people usually want to see those.
            if (isEmbed) {
                // First, check the elements attributes to see if any of them contain youtube or vimeo
                if (anyAttributeMatches(element, allowedVideoRegex)) {
                    return false;
                }
                // upstream's object innerHTML check compares tagName to "object" and never fires
            }
            return true;
        });
    }

    private static boolean anyAttributeMatches(Element element, Pattern p) {
        for (Attribute a : element.attributes()) {
            if (p.matcher(a.getValue()).find()) {
                return true;
            }
        }
        return false;
    }

    private interface ElementTest {
        boolean test(Element e);
    }

    /**
     * Check if a given node has one of its ancestor tag name matching the provided one.
     *
     * @param maxDepth 0 for upstream's default of 3; negative for no limit
     */
    private static boolean hasAncestorTag(Element node, String tagName, int maxDepth, ElementTest filterFn) {
        maxDepth = maxDepth == 0 ? 3 : maxDepth;
        int depth = 0;
        while (node.parent() != null) {
            if (maxDepth > 0 && depth > maxDepth) {
                return false;
            }
            Element parent = node.parent();
            if (is(parent, tagName) && (filterFn == null || filterFn.test(parent))) {
                return true;
            }
            node = parent;
            depth++;
        }
        return false;
    }

    /**
     * Return an object indicating how many rows and columns this table has.
     */
    private static int[] getRowAndColumnCount(Element table) {
        double rows = 0;
        double columns = 0;
        for (Element tr : byTag(table, "tr")) {
            double rowspan = rowOrColSpan(attr(tr, "rowspan"));
            rows += truthy(rowspan) ? rowspan : 1;

            // Now look for column-related info
            double columnsInThisRow = 0;
            for (Element cell : byTag(tr, "td")) {
                double colspan = rowOrColSpan(attr(cell, "colspan"));
                columnsInThisRow += truthy(colspan) ? colspan : 1;
            }
            columns = Math.max(columns, columnsInThisRow);
        }
        return new int[]{(int) rows, (int) columns};
    }

    private static double rowOrColSpan(String value) {
        return truthy(value) ? JsCompat.parseInt(value) : 0;
    }

    /**
     * Look for 'data' (as opposed to 'layout') tables, for which we use similar checks as
     * https://searchfox.org/mozilla-central/rev/f82d5c549f046cb64ce5602bfd894b7ae807c8f8/accessible/generic/TableAccessible.cpp#19
     */
    private void markDataTables(Element root) {
        for (Element table : byTag(root, "table")) {
            String role = attr(table, "role");
            if ("presentation".equals(role)) {
                dataTables.put(table, false);
                continue;
            }
            String datatable = attr(table, "datatable");
            if ("0".equals(datatable)) {
                dataTables.put(table, false);
                continue;
            }
            String summary = attr(table, "summary");
            if (truthy(summary)) {
                dataTables.put(table, true);
                continue;
            }

            Element caption = Dom.firstByTag(table, "caption");
            if (caption != null && caption.childNodeSize() > 0) {
                dataTables.put(table, true);
                continue;
            }

            // If the table has a descendant with any of these tags, consider a data table:
            boolean descendantExists = false;
            for (String tag : List.of("col", "colgroup", "tfoot", "thead", "th")) {
                if (Dom.firstByTag(table, tag) != null) {
                    descendantExists = true;
                    break;
                }
            }
            if (descendantExists) {
                dataTables.put(table, true);
                continue;
            }

            // Nested tables indicate a layout table:
            if (Dom.firstByTag(table, "table") != null) {
                dataTables.put(table, false);
                continue;
            }

            int[] sizeInfo = getRowAndColumnCount(table);

            if (sizeInfo[1] == 1 || sizeInfo[0] == 1) {
                // single colum/row tables are commonly used for page layout purposes.
                dataTables.put(table, false);
                continue;
            }

            if (sizeInfo[0] >= 10 || sizeInfo[1] > 4) {
                dataTables.put(table, true);
                continue;
            }
            // Now just go by size entirely:
            dataTables.put(table, sizeInfo[0] * sizeInfo[1] > 10);
        }
    }

    private boolean isDataTable(Element t) {
        return Boolean.TRUE.equals(dataTables.get(t));
    }

    /** DOM {@code img.src}/{@code img.srcset}; other elements have no such property. */
    private String imgProperty(Element elem, String name) {
        return is(elem, "img") ? elem.attr(name) : "";
    }

    /** Convert images and figures that have properties like data-src into images that can be loaded without JS. */
    private void fixLazyImages(Element root) {
        for (Element elem : byTag(root, Set.of("img", "picture", "figure"))) {
            // In some sites (e.g. Kotaku), they put 1px square image as base64 data uri in the src attribute. So, here we
            // check if the data uri is too short, just might as well remove it.
            String src = imgProperty(elem, "src");
            Matcher parts = B64_DATA_URL.matcher(src);
            if (truthy(src) && parts.find()) {
                // Make sure it's not SVG, because SVG can have a meaningful image in under 133 bytes.
                if (parts.group(1).equals("image/svg+xml")) {
                    continue;
                }

                // Make sure this element has other attributes which contains image. If it doesn't, then this src is
                // important and shouldn't be removed.
                boolean srcCouldBeRemoved = false;
                for (Attribute a : elem.attributes()) {
                    if (a.getKey().equals("src")) {
                        continue;
                    }
                    if (IMAGE_EXT.matcher(a.getValue()).find()) {
                        srcCouldBeRemoved = true;
                        break;
                    }
                }

                // Here we assume if image is less than 100 bytes (or 133 after encoded to base64) it will be too small,
                // therefore it might be placeholder image.
                if (srcCouldBeRemoved) {
                    int b64starts = parts.group(0).length();
                    int b64length = src.length() - b64starts;
                    if (b64length < 133) {
                        elem.removeAttr("src");
                    }
                }
            }

            src = imgProperty(elem, "src");
            String srcset = imgProperty(elem, "srcset");
            if ((truthy(src) || (truthy(srcset) && !srcset.equals("null"))) && !lower(elem.attr("class")).contains("lazy")) {
                continue;
            }

            for (Attribute a : elem.attributes().asList()) {
                if (a.getKey().equals("src") || a.getKey().equals("srcset") || a.getKey().equals("alt")) {
                    continue;
                }
                String copyTo = null;
                if (SRCSET_LIKE.matcher(a.getValue()).find()) {
                    copyTo = "srcset";
                } else if (SRC_LIKE.matcher(a.getValue()).find()) {
                    copyTo = "src";
                }
                if (copyTo != null) {
                    // if this is an img or picture, set the attribute directly
                    if (is(elem, "img") || is(elem, "picture")) {
                        elem.attr(copyTo, a.getValue());
                    } else if (is(elem, "figure") && byTag(elem, Set.of("img", "picture")).isEmpty()) {
                        // if the item is a <figure> that does not contain an image or picture, create one and place it
                        // inside the figure; see the nytimes-3 testcase for an example
                        Element img = doc.createElement("img");
                        img.attr(copyTo, a.getValue());
                        elem.appendChild(img);
                    }
                }
            }
        }
    }

    private double getTextDensity(Element e, Set<String> tags) {
        int textLength = getInnerText(e, true).length();
        if (textLength == 0) {
            return 0;
        }
        double childrenLength = 0;
        for (Element child : byTag(e, tags)) {
            childrenLength += getInnerText(child, true).length();
        }
        return childrenLength / textLength;
    }

    /**
     * Clean an element of all tags of type "tag" if they look fishy. "Fishy" is an algorithm
     * based on content length, classnames, link density, number of images &amp; embeds, etc.
     */
    private void cleanConditionally(Element e, String tag) {
        if (!flagIsActive(FLAG_CLEAN_CONDITIONALLY)) {
            return;
        }

        // Gather counts for other typical elements embedded within. Traverse backwards so we can remove nodes at the
        // same time without effecting the traversal.
        removeNodes(byTag(e, tag), node -> {
            boolean isList = tag.equals("ul") || tag.equals("ol");
            if (!isList) {
                double listLength = 0;
                for (Element list : byTag(node, Set.of("ul", "ol"))) {
                    listLength += getInnerText(list, true).length();
                }
                isList = listLength / getInnerText(node, true).length() > 0.9;
            }

            // First check if this node IS data table, in which case don't remove it.
            if (tag.equals("table") && isDataTable(node)) {
                return false;
            }

            // Next check if we're inside a data table, in which case don't remove it as well.
            if (hasAncestorTag(node, "table", -1, this::isDataTable)) {
                return false;
            }

            if (hasAncestorTag(node, "code", 0, null)) {
                return false;
            }

            // keep element if it has a data tables
            for (Element tbl : byTag(node, "table")) {
                if (isDataTable(tbl)) {
                    return false;
                }
            }

            int weight = getClassWeight(node);

            double contentScore = 0;

            if (weight + contentScore < 0) {
                return true;
            }

            if (getCharCount(node, ',') < 10) {
                // If there are not very many commas, and the number of non-paragraph elements is more than paragraphs or
                // other ominous signs, remove the element.
                int p = byTag(node, "p").size();
                int img = byTag(node, "img").size();
                int li = byTag(node, "li").size() - 100;
                int input = byTag(node, "input").size();
                double headingDensity = getTextDensity(node, Set.of("h1", "h2", "h3", "h4", "h5", "h6"));

                int embedCount = 0;
                for (Element embed : byTag(node, Set.of("object", "embed", "iframe"))) {
                    // If this embed has attribute that matches video regex, don't delete it.
                    if (anyAttributeMatches(embed, allowedVideoRegex)) {
                        return false;
                    }
                    // upstream's object innerHTML check compares tagName to "object" and never fires
                    embedCount++;
                }

                String innerText = getInnerText(node, true);

                // toss any node whose inner text contains nothing but suspicious words
                if (AD_WORDS.matcher(innerText).find() || LOADING_WORDS.matcher(innerText).find()) {
                    return true;
                }

                int contentLength = innerText.length();
                double linkDensity = getLinkDensity(node);
                double textDensity = getTextDensity(node, TEXTISH_TAGS);
                boolean isFigureChild = hasAncestorTag(node, "figure", 0, null);

                // apply shadiness checks, then check for exceptions
                boolean haveToRemove = false;
                if (!isFigureChild && img > 1 && (double) p / img < 0.5) {
                    haveToRemove = true; // Bad p to img ratio
                }
                if (!isList && li > p) {
                    haveToRemove = true; // Too many li's outside of a list
                }
                if (input > Math.floor(p / 3.0)) {
                    haveToRemove = true; // Too many inputs per p
                }
                if (!isList && !isFigureChild && headingDensity < 0.9 && contentLength < 25 && (img == 0 || img > 2) && linkDensity > 0) {
                    haveToRemove = true; // Suspiciously short
                }
                if (!isList && weight < 25 && linkDensity > 0.2 + linkDensityModifier) {
                    haveToRemove = true; // Low weight and a little linky
                }
                if (weight >= 25 && linkDensity > 0.5 + linkDensityModifier) {
                    haveToRemove = true; // High weight and mostly links
                }
                if ((embedCount == 1 && contentLength < 75) || embedCount > 1) {
                    haveToRemove = true; // Suspicious embed
                }
                if (img == 0 && textDensity == 0) {
                    haveToRemove = true; // No useful content
                }

                // Allow simple lists of images to remain in pages
                if (isList && haveToRemove) {
                    for (Element child : node.children()) {
                        // Don't filter in lists with li's that contain more than one child
                        if (child.childrenSize() > 1) {
                            return true;
                        }
                    }
                    int liCount = byTag(node, "li").size();
                    // Only allow the list to remain if every li contains an image
                    if (img == liCount) {
                        return false;
                    }
                }
                return haveToRemove;
            }
            return false;
        });
    }

    private interface MatchFilter {
        boolean test(Element node, String matchString);
    }

    /**
     * Clean out elements that match the specified conditions.
     */
    private void cleanMatchedNodes(Element e, MatchFilter filter) {
        Element endOfSearchMarkerNode = getNextNode(e, true);
        Element next = getNextNode(e, false);
        while (next != null && next != endOfSearchMarkerNode) {
            if (filter.test(next, matchString(next))) {
                next = removeAndGetNext(next);
            } else {
                next = getNextNode(next, false);
            }
        }
    }

    /**
     * Clean out spurious headers from an Element.
     */
    private void cleanHeaders(Element e) {
        removeNodes(byTag(e, Set.of("h1", "h2")), node -> getClassWeight(node) < 0);
    }

    /**
     * Check if this node is an H1 or H2 element whose content is mostly the same as the article
     * title.
     */
    private boolean headerDuplicatesTitle(Element node) {
        if (!is(node, "h1") && !is(node, "h2")) {
            return false;
        }
        String heading = getInnerText(node, false);
        return textSimilarity(articleTitle, heading) > 0.75;
    }

    private boolean flagIsActive(int flag) {
        return (flags & flag) > 0;
    }

    private void removeFlag(int flag) {
        flags = flags & ~flag;
    }

    private static boolean isProbablyVisible(Element node) {
        return !"none".equals(Dom.style(node, "display"))
                && !"hidden".equals(Dom.style(node, "visibility"))
                && !node.hasAttr("hidden")
                // check for "fallback-image" so that wikimedia math images are displayed
                && (!node.hasAttr("aria-hidden") || !"true".equals(node.attr("aria-hidden"))
                || node.attr("class").contains("fallback-image"));
    }

    /**
     * Runs readability.
     * <p>
     * Workflow: 1. Prep the document by removing script tags, css, etc. 2. Build readability's DOM
     * tree. 3. Grab the article content from the current dom tree. 4. Replace the current DOM tree
     * with the new one. 5. Read peacefully.
     *
     * @return the article, or null when nothing readable was found
     * @throws ReadabilityLimitException when maxElemsToParse or maxTextScanned is exceeded
     */
    public Article parse() {
        // Avoid parsing too large documents, as per configuration option
        if (maxElemsToParse > 0) {
            int numTags = doc.getAllElements().size() - 1;
            if (numTags > maxElemsToParse) {
                throw new ReadabilityLimitException("Aborting parsing document; " + numTags + " elements found");
            }
        }

        // Unwrap image from noscript
        unwrapNoscriptImages();

        // Extract JSON-LD metadata before removing scripts
        Map<String, String> jsonLd = disableJsonLd ? new LinkedHashMap<>() : getJsonLd();

        // Remove script tags from the document.
        removeScripts();

        prepDocument();

        Map<String, String> md = getArticleMetadata(jsonLd);
        this.metadata = md;
        this.articleTitle = md.get("title");

        Element articleContent = grabArticle();
        if (articleContent == null) {
            return null;
        }

        postProcessContent(articleContent);

        // If we haven't found an excerpt in the article's metadata, use the article's first paragraph as the excerpt. This
        // is used for displaying a preview of the article's content.
        if (!truthy(md.get("excerpt"))) {
            Element first = Dom.firstByTag(articleContent, "p");
            if (first != null) {
                md.put("excerpt", trim(textContent(first)));
            }
        }

        // detached content serializes with jsoup's default (pretty) settings; give it an owner that does not
        Document holder = new Document(baseUri == null ? "" : baseUri);
        holder.outputSettings().prettyPrint(false);
        holder.appendChild(articleContent);

        return new Article(articleTitle, or(md.get("byline"), articleByline), articleDir, articleLang, articleContent,
                textContent(articleContent), md.get("excerpt"), md.get("siteName"), md.get("publishedTime"));
    }
}
