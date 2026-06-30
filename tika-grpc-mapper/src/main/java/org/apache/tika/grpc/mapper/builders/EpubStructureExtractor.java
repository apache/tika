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
package org.apache.tika.grpc.mapper.builders;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.tika.grpc.v1.EpubContentItem;
import org.apache.tika.grpc.v1.EpubEmbeddedResource;
import org.apache.tika.grpc.v1.EpubManifestItem;
import org.apache.tika.grpc.v1.EpubMetadata;
import org.apache.tika.grpc.v1.EpubTocItem;

/**
 * Extracts EPUB structure (OPF manifest, spine, TOC, encryption) directly from the .epub container bytes
 * and enriches an EpubMetadata.Builder accordingly.
 */
public final class EpubStructureExtractor {

    private EpubStructureExtractor() { }

    /**
     * Enriches the given {@link EpubMetadata.Builder} with structure read directly
     * from the raw {@code .epub} (ZIP) container bytes.
     * <p>
     * Unzips the container, locates the OPF package document via
     * {@code META-INF/container.xml} (falling back to the first {@code .opf} entry),
     * and populates the root OPF path, reading direction, navigation document and
     * cover image paths, the manifest and spine (reading order), embedded resource
     * classification (images, fonts, stylesheets, other), DRM/encryption details,
     * and the table of contents from an EPUB 2 NCX or EPUB 3 NAV document. All
     * parsing failures are swallowed so that malformed input leaves the builder
     * unchanged.
     *
     * @param builder the EPUB metadata builder to enrich in place
     * @param epubBytes the raw bytes of the {@code .epub} container; ignored if {@code null} or empty
     */
    public static void enrich(EpubMetadata.Builder builder, byte[] epubBytes) {
        if (epubBytes == null || epubBytes.length == 0) return;

        Map<String, byte[]> pathToBytes = new HashMap<>();
        Map<String, Long> pathToSize = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(epubBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(0, (int) entry.getSize()));
                byte[] buf = new byte[8192];
                int r;
                while ((r = zis.read(buf)) != -1) {
                    bos.write(buf, 0, r);
                }
                String name = normalizePath(entry.getName());
                pathToBytes.put(name, bos.toByteArray());
                pathToSize.put(name, entry.getSize());
            }
        } catch (Exception ignored) {
            return;
        }

        String containerPath = "META-INF/container.xml";
        String opfPath = null;
        if (pathToBytes.containsKey(containerPath)) {
            try {
                Document container = parseXml(pathToBytes.get(containerPath));
                NodeList roots = container.getElementsByTagName("rootfile");
                for (int i = 0; i < roots.getLength(); i++) {
                    Element el = (Element) roots.item(i);
                    String fullPath = el.getAttribute("full-path");
                    String mediaType = el.getAttribute("media-type");
                    if (fullPath != null && !fullPath.isEmpty()) {
                        opfPath = normalizePath(fullPath);
                        if (mediaType != null && mediaType.contains("package+xml")) break;
                    }
                }
            } catch (Exception ignored) { }
        }
        if (opfPath == null) {
            // Fallback: find first .opf
            for (String p : pathToBytes.keySet()) {
                if (p.toLowerCase(Locale.ROOT).endsWith(".opf")) {
                    opfPath = p;
                    break;
                }
            }
        }
        if (opfPath == null || !pathToBytes.containsKey(opfPath)) return;
        builder.setRootOpfPath(opfPath);

        String opfDir = parentDir(opfPath);

        try {
            Document opf = parseXml(pathToBytes.get(opfPath));

            // page-progression-direction → reading_direction
            NodeList pkgList = opf.getElementsByTagName("package");
            if (pkgList.getLength() > 0) {
                Element pkg = (Element) pkgList.item(0);
                String ppd = pkg.getAttribute("page-progression-direction");
                if (ppd != null && !ppd.isEmpty()) builder.setReadingDirection(ppd);
            }

            // metadata: cover via <meta name="cover" content="id"/>
            String coverIdFromMeta = null;
            NodeList metaList = opf.getElementsByTagName("meta");
            for (int i = 0; i < metaList.getLength(); i++) {
                Element m = (Element) metaList.item(i);
                String name = m.getAttribute("name");
                if ("cover".equalsIgnoreCase(name)) {
                    coverIdFromMeta = m.getAttribute("content");
                    break;
                }
            }

            // manifest
            Map<String, ManifestEntry> manifest = new LinkedHashMap<>();
            NodeList manifestNodes = opf.getElementsByTagName("manifest");
            if (manifestNodes.getLength() > 0) {
                Element manifestEl = (Element) manifestNodes.item(0);
                NodeList items = manifestEl.getElementsByTagName("item");
                for (int i = 0; i < items.getLength(); i++) {
                    Element item = (Element) items.item(i);
                    String id = item.getAttribute("id");
                    String href = resolvePath(opfDir, item.getAttribute("href"));
                    String mediaType = item.getAttribute("media-type");
                    String properties = item.getAttribute("properties");
                    ManifestEntry me = new ManifestEntry(id, href, mediaType, properties);
                    Long size = pathToSize.getOrDefault(href, -1L);
                    me.size = size;
                    manifest.put(id, me);
                }
            }

            // navigation_document_path & cover_image
            for (ManifestEntry me : manifest.values()) {
                if (me.properties.contains("nav")) {
                    builder.setNavigationDocumentPath(me.href);
                }
                if (me.properties.contains("cover-image") || (coverIdFromMeta != null && coverIdFromMeta.equals(me.id))) {
                    builder.setCoverImagePath(me.href);
                }
            }

            // spine → reading order
            NodeList spineNodes = opf.getElementsByTagName("spine");
            int spineIndex = 0;
            if (spineNodes.getLength() > 0) {
                Element spine = (Element) spineNodes.item(0);
                NodeList itemrefs = spine.getElementsByTagName("itemref");
                for (int i = 0; i < itemrefs.getLength(); i++) {
                    Element ir = (Element) itemrefs.item(i);
                    String idref = ir.getAttribute("idref");
                    ManifestEntry me = manifest.get(idref);
                    EpubContentItem.Builder ci = EpubContentItem.newBuilder()
                            .setId(idref)
                            .setSpineIndex(spineIndex++);
                    if (me != null) {
                        ci.setHref(me.href);
                        if (me.mediaType != null && !me.mediaType.isEmpty()) ci.setMediaType(me.mediaType);
                        if (!me.properties.isEmpty()) ci.addAllProperties(splitProperties(me.properties));
                    }
                    String linear = ir.getAttribute("linear");
                    if (linear != null && !linear.isEmpty()) ci.setIsLinear(!"no".equalsIgnoreCase(linear));
                    builder.addSpineItems(ci.build());
                }
                builder.setContentFileCount(spineIndex);
            }

            // manifest_items in proto
            for (ManifestEntry me : manifest.values()) {
                EpubManifestItem.Builder mi = EpubManifestItem.newBuilder()
                        .setId(me.id)
                        .setHref(me.href)
                        .setMediaType(me.mediaType);
                if (!me.properties.isEmpty()) mi.addAllProperties(splitProperties(me.properties));
                if (me.size != null && me.size >= 0) mi.setFileSize(me.size);
                builder.addManifestItems(mi.build());
            }
            builder.setEmbeddedResourceCount(Math.max(0, manifest.size() - builder.getContentFileCount()));

            // encryption -> DRM
            String encryptionPath = "META-INF/encryption.xml";
            if (pathToBytes.containsKey(encryptionPath)) {
                builder.setHasDrm(true);
                try {
                    Document enc = parseXml(pathToBytes.get(encryptionPath));
                    NodeList cipherRefs = enc.getElementsByTagName("CipherReference");
                    for (int i = 0; i < cipherRefs.getLength(); i++) {
                        Element cr = (Element) cipherRefs.item(i);
                        String uri = cr.getAttribute("URI");
                        if (uri != null && !uri.isEmpty()) builder.addEncryptedResources(uri);
                    }
                } catch (Exception ignored) { }
            }

            // Classify embedded resources
            for (ManifestEntry me : manifest.values()) {
                String mt = me.mediaType == null ? "" : me.mediaType;
                EpubEmbeddedResource.Builder rb = EpubEmbeddedResource.newBuilder()
                        .setId(me.id)
                        .setHref(me.href)
                        .setMediaType(mt);
                if (me.size != null && me.size >= 0) rb.setFileSize(me.size);

                if (mt.startsWith("image/")) {
                    builder.addImages(rb.build());
                } else if (isFontType(mt)) {
                    builder.addFonts(rb.build());
                } else if ("text/css".equalsIgnoreCase(mt)) {
                    builder.addStylesheets(rb.build());
                } else if (!isSpineItem(me.id, builder.getSpineItemsList())) {
                    builder.addOtherResources(rb.build());
                }
            }

            // TOC (EPUB 2 NCX)
            ManifestEntry ncxEntry = findByMediaType(manifest, "application/x-dtbncx+xml");
            if (ncxEntry != null && pathToBytes.containsKey(ncxEntry.href)) {
                try {
                    Document ncx = parseXml(pathToBytes.get(ncxEntry.href));
                    Element navMap = firstElement(ncx.getElementsByTagName("navMap"));
                    if (navMap != null) {
                        List<EpubTocItem> toc = new ArrayList<>();
                        buildNcxToc(navMap, 0, toc);
                        for (EpubTocItem item : toc) builder.addTableOfContents(item);
                    }
                } catch (Exception ignored) { }
            }

            // TOC (EPUB 3 NAV XHTML)
            if (builder.hasNavigationDocumentPath()) {
                String navPath = builder.getNavigationDocumentPath();
                if (navPath != null && pathToBytes.containsKey(navPath)) {
                    try {
                        Document navDoc = parseXml(pathToBytes.get(navPath));
                        Element tocNav = findTocNav(navDoc);
                        if (tocNav != null) {
                            String navDir = parentDir(navPath);
                            List<EpubTocItem> toc = new ArrayList<>();
                            buildHtmlNavToc(tocNav, navDir, 0, toc);
                            if (!toc.isEmpty()) {
                                builder.clearTableOfContents();
                                for (EpubTocItem item : toc) builder.addTableOfContents(item);
                            }
                        }
                    } catch (Exception ignored) { }
                }
            }

        } catch (Exception ignored) {
            // ignore
        }
    }

    private static void buildNcxToc(Element parent, int level, List<EpubTocItem> out) {
        NodeList navPoints = parent.getElementsByTagName("navPoint");
        for (int i = 0; i < navPoints.getLength(); i++) {
            Element np = (Element) navPoints.item(i);
            String label = textOf(firstElement(np.getElementsByTagName("text")));
            Element content = firstElement(np.getElementsByTagName("content"));
            String src = content != null ? content.getAttribute("src") : null;
            EpubTocItem.Builder ti = EpubTocItem.newBuilder()
                    .setLabel(label == null ? "" : label)
                    .setLevel(level)
                    .setPlayOrder(i);
            if (src != null && !src.isEmpty()) ti.setHref(src);

            // Recurse for children
            List<EpubTocItem> children = new ArrayList<>();
            NodeList childNPs = np.getElementsByTagName("navPoint");
            for (int j = 0; j < childNPs.getLength(); j++) {
                buildNcxToc((Element) childNPs.item(j), level + 1, children);
            }
            ti.addAllChildren(children);
            out.add(ti.build());
        }
    }

    private static void buildHtmlNavToc(Element navRoot, String navDocDir, int level, List<EpubTocItem> out) {
        Element ol = firstElementByLocalName(navRoot, "ol");
        if (ol == null) return;
        NodeList liNodes = ol.getChildNodes();
        int play = 0;
        for (int i = 0; i < liNodes.getLength(); i++) {
            Node n = liNodes.item(i);
            if (!(n instanceof Element)) continue;
            Element li = (Element) n;
            if (!"li".equalsIgnoreCase(li.getLocalName() != null ? li.getLocalName() : li.getTagName())) continue;
            EpubTocItem item = buildTocItemFromLi(li, navDocDir, level, play++);
            if (item != null) out.add(item);
        }
    }

    private static EpubTocItem buildTocItemFromLi(Element li, String navDocDir, int level, int playOrder) {
        Element a = firstElementByLocalName(li, "a");
        String label = a != null ? textOf(a) : textOf(firstElementByLocalName(li, "span"));
        String href = a != null ? a.getAttribute("href") : null;
        if (href != null && !href.isEmpty()) {
            href = resolvePath(navDocDir, href);
        }
        EpubTocItem.Builder ti = EpubTocItem.newBuilder()
                .setLabel(label == null ? "" : label)
                .setLevel(level)
                .setPlayOrder(playOrder);
        if (href != null && !href.isEmpty()) ti.setHref(href);

        Element childOl = firstElementByLocalName(li, "ol");
        if (childOl != null) {
            NodeList childNodes = childOl.getChildNodes();
            int childPlay = 0;
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node n = childNodes.item(i);
                if (!(n instanceof Element)) continue;
                Element childLi = (Element) n;
                if (!"li".equalsIgnoreCase(childLi.getLocalName() != null ? childLi.getLocalName() : childLi.getTagName())) continue;
                EpubTocItem child = buildTocItemFromLi(childLi, navDocDir, level + 1, childPlay++);
                if (child != null) ti.addChildren(child);
            }
        }
        return ti.build();
    }

    private static Element findTocNav(Document navDoc) {
        NodeList navs = navDoc.getElementsByTagNameNS("*", "nav");
        for (int i = 0; i < navs.getLength(); i++) {
            Node n = navs.item(i);
            if (!(n instanceof Element)) continue;
            Element nav = (Element) n;
            String epubType = nav.getAttribute("epub:type");
            if (epubType == null || epubType.isEmpty()) {
                epubType = nav.getAttributeNS("http://www.idpf.org/2007/ops", "type");
            }
            if (epubType != null && epubType.toLowerCase(Locale.ROOT).contains("toc")) return nav;
        }
        // Fallback: any <nav> named 'toc'
        NodeList htmlNavs = navDoc.getElementsByTagName("nav");
        for (int i = 0; i < htmlNavs.getLength(); i++) {
            Node n = htmlNavs.item(i);
            if (n instanceof Element) {
                Element nav = (Element) n;
                String role = nav.getAttribute("role");
                if ("doc-toc".equalsIgnoreCase(role)) return nav;
            }
        }
        return null;
    }

    private static Element firstElementByLocalName(Element parent, String localName) {
        if (parent == null) return null;
        NodeList nl = parent.getElementsByTagNameNS("*", localName);
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n instanceof Element && n.getParentNode() == parent) return (Element) n;
        }
        NodeList any = parent.getElementsByTagName(localName);
        for (int i = 0; i < any.getLength(); i++) {
            Node n = any.item(i);
            if (n instanceof Element) return (Element) n;
        }
        return null;
    }
    private static boolean isFontType(String mt) {
        String v = mt.toLowerCase(Locale.ROOT);
        return v.startsWith("font/") || v.contains("woff") || v.contains("opentype");
    }

    private static boolean isSpineItem(String id, List<EpubContentItem> spine) {
        for (EpubContentItem ci : spine) if (ci.getId().equals(id)) return true;
        return false;
    }

    private static ManifestEntry findByMediaType(Map<String, ManifestEntry> manifest, String mediaType) {
        for (ManifestEntry me : manifest.values()) {
            if (mediaType.equalsIgnoreCase(me.mediaType)) return me;
        }
        return null;
    }

    private static List<String> splitProperties(String props) {
        if (props == null || props.isEmpty()) return Collections.emptyList();
        String[] parts = props.trim().split("\\s+");
        List<String> list = new ArrayList<>();
        for (String p : parts) if (!p.isEmpty()) list.add(p);
        return list;
    }

    private static Document parseXml(byte[] data) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setExpandEntityReferences(false);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder b = f.newDocumentBuilder();
        return b.parse(new ByteArrayInputStream(data));
    }

    private static String textOf(Element el) {
        if (el == null) return null;
        String t = el.getTextContent();
        return t == null ? null : t.trim();
    }

    private static Element firstElement(NodeList list) {
        if (list == null) return null;
        for (int i = 0; i < list.getLength(); i++) {
            Node n = list.item(i);
            if (n instanceof Element) return (Element) n;
        }
        return null;
    }

    private static String parentDir(String path) {
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(0, idx) : "";
    }

    private static String resolvePath(String baseDir, String href) {
        if (href == null || href.isEmpty()) return href;
        String combined = baseDir == null || baseDir.isEmpty() ? href : baseDir + "/" + href;
        return normalizePath(combined);
    }

    private static String normalizePath(String p) {
        if (p == null) return null;
        String[] parts = p.replace('\\', '/').split("/");
        Deque<String> stack = new ArrayDeque<>();
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) { if (!stack.isEmpty()) stack.removeLast(); }
            else stack.addLast(part);
        }
        StringBuilder sb = new StringBuilder();
        for (String s : stack) {
            if (sb.length() > 0) sb.append('/');
            sb.append(s);
        }
        return sb.toString();
    }

    private static final class ManifestEntry {
        final String id;
        final String href;
        final String mediaType;
        final String properties;
        Long size;
        ManifestEntry(String id, String href, String mediaType, String properties) {
            this.id = id == null ? "" : id;
            this.href = href == null ? "" : href;
            this.mediaType = mediaType == null ? "" : mediaType;
            this.properties = properties == null ? "" : properties;
        }
    }
}
