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
package org.apache.tika.parser.pdf;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDAttributeObject;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.Revisions;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.StandardStructureTypes;

/**
 * A PDF's structure tree, read once per document: every element in preorder, and every
 * marked-content leaf keyed by the content stream it lives in (a page, or a form XObject with
 * its own /StructParents) and its MCID. Bounded and cycle-safe; a tree over the caps is
 * unusable and says why.
 */
final class StructureIndex {

    static final int MAX_NODES = 500_000;
    static final int MAX_LEAVES = 1_000_000;
    static final int MAX_DEPTH = 512;
    private static final int MAX_ROLE_HOPS = 16;
    private static final COSName STM = COSName.getPDFName("Stm");
    private static final Set<String> STANDARD_TYPES = standardTypes();

    /** A structure element. */
    static final class Node {
        final int id;
        final Node parent;
        final int depth;
        /** Standard structure type after role mapping, or the custom name when unmapped. */
        final String type;
        final COSDictionary dict;
        /** The first /OBJR kid's object, e.g. a Link annotation. */
        COSDictionary objr;
        /** The writer's cached element mapping. */
        PDFMarkedContent2XHTML.ElementSpec spec;

        private Node(int id, Node parent, int depth, String type, COSDictionary dict) {
            this.id = id;
            this.parent = parent;
            this.depth = depth;
            this.type = type;
            this.dict = dict;
        }

        int countAncestors(String type) {
            int n = 0;
            for (Node p = parent; p != null; p = p.parent) {
                if (type.equals(p.type)) {
                    n++;
                }
            }
            return n;
        }
    }

    /** One MCID reference in the tree; {@code order} is its position in document order. */
    static final class Leaf {
        final Node node;
        final int order;

        private Leaf(Node node, int order) {
            this.node = node;
            this.order = order;
        }
    }

    private static final class Frame {
        final COSBase kid;
        final Node parent;
        final int depth;
        final COSDictionary page;

        Frame(COSBase kid, Node parent, int depth, COSDictionary page) {
            this.kid = kid;
            this.parent = parent;
            this.depth = depth;
            this.page = page;
        }
    }

    private final Map<COSBase, Map<Integer, Leaf>> leavesByScope = new IdentityHashMap<>();
    private final Map<String, Object> roleMap;
    private final Map<String, String> resolvedTypes = new HashMap<>();
    private int nodeCount;
    private int leafCount;
    private String reason;

    private StructureIndex(Map<String, Object> roleMap) {
        this.roleMap = roleMap;
    }

    /**
     * Never throws: a document without a tree, or with one over the caps or unreadable, yields
     * an index whose {@link #reason()} says so.
     */
    static StructureIndex load(PDDocument doc) {
        StructureIndex index = new StructureIndex(Collections.emptyMap());
        try {
            PDStructureTreeRoot root = doc.getDocumentCatalog().getStructureTreeRoot();
            if (root == null) {
                index.reason = "no-structure-tree";
                return index;
            }
            COSBase k = root.getCOSObject().getItem(COSName.K);
            if (k == null) {
                index.reason = "empty-structure-tree";
                return index;
            }
            index = new StructureIndex(root.getRoleMap());
            index.walk(k);
        } catch (RuntimeException e) {
            index.reason = "structure-tree-unreadable";
            index.leavesByScope.clear();
        }
        return index;
    }

    /** Null when the tree is usable. */
    String reason() {
        return reason;
    }

    int nodeCount() {
        return nodeCount;
    }

    int leafCount() {
        return leafCount;
    }

    /** The tree's leaves for a page or form content stream, by MCID; empty if none. */
    Map<Integer, Leaf> leaves(COSBase scope) {
        Map<Integer, Leaf> m = leavesByScope.get(scope);
        return m == null ? Collections.emptyMap() : m;
    }

    Leaf leaf(COSBase scope, int mcid) {
        Map<Integer, Leaf> m = leavesByScope.get(scope);
        return m == null ? null : m.get(mcid);
    }

    private void walk(COSBase rootKids) {
        Set<COSDictionary> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Frame> stack = new ArrayDeque<>();
        pushKids(rootKids, null, 0, null, stack);
        while (!stack.isEmpty()) {
            Frame f = stack.pop();
            COSBase kid = f.kid;
            if (kid instanceof COSObject) {
                kid = ((COSObject) kid).getObject();
            }
            if (kid instanceof COSInteger) {
                if (f.parent != null && f.page != null) {
                    addLeaf(f.parent, f.page, ((COSInteger) kid).intValue());
                }
            } else if (kid instanceof COSArray) {
                pushKids(kid, f.parent, f.depth, f.page, stack);
            } else if (kid instanceof COSDictionary) {
                COSDictionary dict = (COSDictionary) kid;
                String type = dict.getNameAsString(COSName.TYPE);
                if (type == null || PDStructureElement.TYPE.equals(type)) {
                    if (!visited.add(dict)) {
                        continue;
                    }
                    if (f.depth > MAX_DEPTH) {
                        fail("structure-tree-depth");
                        return;
                    }
                    if (++nodeCount > MAX_NODES) {
                        fail("structure-tree-nodes");
                        return;
                    }
                    COSDictionary ownPage = dict.getCOSDictionary(COSName.PG);
                    COSDictionary page = ownPage != null ? ownPage : f.page;
                    Node node = new Node(nodeCount, f.parent, f.depth,
                            resolveType(dict.getNameAsString(COSName.S)), dict);
                    COSBase k = dict.getItem(COSName.K);
                    if (k != null) {
                        pushKids(k, node, f.depth + 1, page, stack);
                    }
                } else if (COSName.MCR.getName().equals(type)) {
                    if (f.parent == null) {
                        continue;
                    }
                    COSBase stm = dict.getDictionaryObject(STM);
                    COSDictionary pg = dict.getCOSDictionary(COSName.PG);
                    COSBase scope = stm != null ? stm : (pg != null ? pg : f.page);
                    int mcid = dict.getInt(COSName.MCID, -1);
                    if (scope != null && mcid >= 0) {
                        addLeaf(f.parent, scope, mcid);
                    }
                } else if (COSName.OBJR.getName().equals(type)) {
                    if (f.parent != null && f.parent.objr == null) {
                        f.parent.objr = dict.getCOSDictionary(COSName.OBJ);
                    }
                }
            }
            if (reason != null) {
                return;
            }
        }
    }

    private void pushKids(COSBase kids, Node parent, int depth, COSDictionary page,
                          Deque<Frame> stack) {
        if (kids instanceof COSArray) {
            COSArray array = (COSArray) kids;
            for (int i = array.size() - 1; i >= 0; i--) {
                stack.push(new Frame(array.get(i), parent, depth, page));
            }
        } else {
            stack.push(new Frame(kids, parent, depth, page));
        }
    }

    private void addLeaf(Node node, COSBase scope, int mcid) {
        if (leafCount >= MAX_LEAVES) {
            fail("structure-tree-leaves");
            return;
        }
        Map<Integer, Leaf> m = leavesByScope.computeIfAbsent(scope, s -> new HashMap<>());
        if (!m.containsKey(mcid)) {
            m.put(mcid, new Leaf(node, ++leafCount));
        }
    }

    private void fail(String why) {
        reason = why;
        leavesByScope.clear();
    }

    /** Follows the role map to a standard type; a custom type with no mapping keeps its name. */
    private String resolveType(String raw) {
        if (raw == null) {
            return "";
        }
        String cached = resolvedTypes.get(raw);
        if (cached != null) {
            return cached;
        }
        String t = raw;
        for (int hops = 0; hops < MAX_ROLE_HOPS && !STANDARD_TYPES.contains(t); hops++) {
            Object mapped = roleMap.get(t);
            if (!(mapped instanceof String) || mapped.equals(t)) {
                break;
            }
            t = (String) mapped;
        }
        resolvedTypes.put(raw, t);
        return t;
    }

    static boolean isStandardType(String type) {
        return STANDARD_TYPES.contains(type);
    }

    /** The /A /URI of a Link annotation reached through /OBJR, or null. */
    static String linkUri(COSDictionary objr) {
        if (objr == null) {
            return null;
        }
        COSDictionary action = objr.getCOSDictionary(COSName.A);
        if (action == null || !COSName.URI.equals(action.getCOSName(COSName.S))) {
            return null;
        }
        String uri = action.getString(COSName.URI);
        return uri == null || uri.isBlank() ? null : uri;
    }

    /** The element's attribute objects across revisions; empty on hostile input. */
    static List<PDAttributeObject> attributes(COSDictionary element) {
        List<PDAttributeObject> out = new ArrayList<>();
        try {
            Revisions<PDAttributeObject> revisions =
                    new PDStructureElement(element).getAttributes();
            for (int i = 0; i < revisions.size(); i++) {
                PDAttributeObject attribute = revisions.getObject(i);
                if (attribute != null) {
                    out.add(attribute);
                }
            }
        } catch (RuntimeException e) {
            out.clear();
        }
        return out;
    }

    private static Set<String> standardTypes() {
        Set<String> types = new HashSet<>(StandardStructureTypes.types);
        // PDF 2.0 additions PDFBox does not list
        Collections.addAll(types, "Title", "FENote", "Sub", "Em", "Strong", "Aside",
                "DocumentFragment", "Artifact");
        return types;
    }
}
