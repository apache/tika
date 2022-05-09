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

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDMarkedContent;
import org.apache.pdfbox.text.PDFMarkedContentExtractor;
import org.apache.pdfbox.text.TextPosition;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * <p>This was added in Tika 1.24 as an alpha version of a text extractor
 * that builds the text from the marked text tree and includes/normalizes
 * some of the structural tags.
 * </p>
 *
 * @since 1.24
 */

public class PDFMarkedContent2XHTML extends PDF2XHTML {

    private static final int MAX_RECURSION_DEPTH = 1000;
    private static final String DIV = "div";
    private static final Map<String, HtmlTag> COMMON_TAG_MAP = new HashMap<>();

    static {
        //code requires these to be all lower case
        COMMON_TAG_MAP.put("document", new HtmlTag("body"));
        COMMON_TAG_MAP.put("div", new HtmlTag("div"));
        COMMON_TAG_MAP.put("p", new HtmlTag("p"));
        COMMON_TAG_MAP.put("span", new HtmlTag("span"));
        COMMON_TAG_MAP.put("table", new HtmlTag("table"));
        COMMON_TAG_MAP.put("thead", new HtmlTag("thead"));
        COMMON_TAG_MAP.put("tbody", new HtmlTag("tbody"));
        COMMON_TAG_MAP.put("tr", new HtmlTag("tr"));
        COMMON_TAG_MAP.put("th", new HtmlTag("th"));
        COMMON_TAG_MAP.put("td", new HtmlTag("td"));//TODO -- convert to th if in thead?
        COMMON_TAG_MAP.put("l", new HtmlTag("ul"));
        COMMON_TAG_MAP.put("li", new HtmlTag("li"));
        COMMON_TAG_MAP.put("h1", new HtmlTag("h1"));
        COMMON_TAG_MAP.put("h2", new HtmlTag("h2"));
        COMMON_TAG_MAP.put("h3", new HtmlTag("h3"));
        COMMON_TAG_MAP.put("h4", new HtmlTag("h4"));
        COMMON_TAG_MAP.put("h5", new HtmlTag("h5"));
        COMMON_TAG_MAP.put("h6", new HtmlTag("h6"));
    }

    //this stores state as we recurse through the structure tag tree
    private State state = new State();

    private PDFMarkedContent2XHTML(PDDocument document, XHTMLContentHandler xhtml,
                                   ParseContext context, Metadata metadata, PDFParserConfig config)
            throws IOException {
        super(document, xhtml, context, metadata, config);
    }

    /**
     * Converts the given PDF document (and related metadata) to a stream
     * of XHTML SAX events sent to the given content handler.
     *
     * @param pdDocument PDF document
     * @param xhtml    SAX content handler
     * @param metadata   PDF metadata
     * @throws SAXException  if the content handler fails to process SAX events
     * @throws TikaException if there was an exception outside of per page processing
     */
    public static void process(PDDocument pdDocument, XHTMLContentHandler xhtml,
                               ParseContext context,
                               Metadata metadata, PDFParserConfig config)
            throws SAXException, TikaException {

        PDFMarkedContent2XHTML pdfMarkedContent2XHTML = null;
        try {
            pdfMarkedContent2XHTML =
                    new PDFMarkedContent2XHTML(pdDocument, xhtml, context, metadata, config);
        } catch (IOException e) {
            throw new TikaException("couldn't initialize PDFMarkedContent2XHTML", e);
        }
        try {
            pdfMarkedContent2XHTML.writeText(pdDocument, new Writer() {
                @Override
                public void write(char[] cbuf, int off, int len) {
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            });
        } catch (IOException e) {
            if (e.getCause() instanceof SAXException) {
                throw (SAXException) e.getCause();
            } else {
                throw new TikaException("Unable to extract PDF content", e);
            }
        }
        if (pdfMarkedContent2XHTML.exceptions.size() > 0) {
            //throw the first
            throw new TikaException("Unable to extract PDF content",
                    pdfMarkedContent2XHTML.exceptions.get(0));
        }
    }

    private static Map<String, HtmlTag> loadRoleMap(Map<String, Object> roleMap) {
        if (roleMap == null) {
            return Collections.EMPTY_MAP;
        }
        Map<String, HtmlTag> tags = new HashMap<>();
        for (Map.Entry<String, Object> e : roleMap.entrySet()) {
            String k = e.getKey();
            Object obj = e.getValue();
            if (obj instanceof String) {
                String v = (String) obj;
                String lc = v.toLowerCase(Locale.US);
                if (COMMON_TAG_MAP.containsValue(new HtmlTag(lc))) {
                    tags.put(k, new HtmlTag(lc));
                } else {
                    tags.put(k, new HtmlTag(DIV, lc));
                }
            }
        }
        return tags;
    }

    private static void findPages(COSBase kidsObj, List<ObjectRef> pageRefs) {
        if (kidsObj == null) {
            return;
        }
        if (kidsObj instanceof COSArray) {
            for (COSBase kid : ((COSArray) kidsObj)) {
                if (kid instanceof COSObject) {
                    COSBase kidbase = ((COSObject) kid).getObject();
                    if (kidbase instanceof COSDictionary) {
                        COSDictionary dict = (COSDictionary) kidbase;
                        if (dict.containsKey(COSName.TYPE) &&
                                COSName.PAGE.equals(dict.getCOSName(COSName.TYPE))) {
                            pageRefs.add(new ObjectRef(((COSObject) kid).getObjectNumber(),
                                    ((COSObject) kid).getGenerationNumber()));
                            continue;
                        }
                        if (((COSDictionary) kidbase).containsKey(COSName.KIDS)) {
                            findPages(((COSDictionary) kidbase).getItem(COSName.KIDS), pageRefs);
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void processPages(PDPageTree pages) throws IOException {

        //this is a 0-indexed list of object refs for each page
        //we need this to map the mcids later...
        //TODO: is there a better way of getting these/doing the mapping?

        List<ObjectRef> pageRefs = new ArrayList<>();
        //STEP 1: get the page refs
        findPages(pdDocument.getPages().getCOSObject().getItem(COSName.KIDS), pageRefs);
        //confirm the right number of pages was found
        if (pageRefs.size() != pdDocument.getNumberOfPages()) {
            throw new IOException(new TikaException(
                    "Couldn't find the right number of page refs (" + pageRefs.size() +
                            ") for pages (" + pdDocument.getNumberOfPages() + ")"));
        }

        PDStructureTreeRoot structureTreeRoot =
                pdDocument.getDocumentCatalog().getStructureTreeRoot();

        //STEP 2: load the roleMap
        Map<String, HtmlTag> roleMap = loadRoleMap(structureTreeRoot.getRoleMap());

        //STEP 3: load all of the text, mapped to MCIDs
        Map<MCID, String> paragraphs = loadTextByMCID(pageRefs);

        //STEP 4: now recurse the the structure tree root and output the structure
        //and the text bits from paragraphs

        try {
            recurse(structureTreeRoot.getK(), null, 0, paragraphs, roleMap);
        } catch (SAXException e) {
            throw new IOException(e);
        }

        //STEP 5: handle all the potentially unprocessed bits
        try {
            if (state.hrefAnchorBuilder.length() > 0) {
                xhtml.startElement("p");
                writeString(state.hrefAnchorBuilder.toString());
                xhtml.endElement("p");
            }
            for (Map.Entry<MCID, String> entry: paragraphs.entrySet()) {
                if (!state.processedMCIDs.contains(entry.getKey())) {
                    if (entry.getKey().mcid > -1) {
                        //TODO: LOG! piece of text that wasn't referenced  in the marked content
                        // tree
                        // but should have been.  If mcid == -1, this was a known item not part of
                        // content tree.
                    }

                    xhtml.startElement("p");
                    writeString(entry.getValue());
                    xhtml.endElement("p");
                }
            }
        } catch (SAXException e) {
            throw new IOException(e);
        }
        //Step 6: for now, iterate through the pages again and do all the other handling
        //TODO: figure out when we're crossing page boundaries during the recursion
        // step above and do the page by page processing then...rather than dumping this
        // all here.
        for (PDPage page : pdDocument.getPages()) {
            startPage(page);
            endPage(page);
        }

    }

    private void recurse(COSBase kids, ObjectRef currentPageRef, int depth,
                         Map<MCID, String> paragraphs, Map<String, HtmlTag> roleMap)
            throws IOException, SAXException {

        if (depth > MAX_RECURSION_DEPTH) {
            throw new IOException(
                    new TikaException("Exceeded max recursion depth " + MAX_RECURSION_DEPTH));
        }

        if (kids instanceof COSArray) {
            for (COSBase k : ((COSArray) kids)) {
                recurse(k, currentPageRef, depth, paragraphs, roleMap);
            }
        } else if (kids instanceof COSObject) {
            COSBase cosType = ((COSObject) kids).getItem(COSName.TYPE);
            if (cosType != null && cosType instanceof COSName) {
                if ("OBJR".equals(((COSName) cosType).getName())) {
                    recurse(((COSObject) kids).getDictionaryObject(COSName.OBJ), currentPageRef,
                            depth + 1, paragraphs, roleMap);
                }
            }

            COSBase n = ((COSObject) kids).getItem(COSName.S);
            String name = "";
            if (n instanceof COSName) {
                name = ((COSName) n).getName();
            }
            COSBase grandkids = ((COSObject) kids).getItem(COSName.K);
            if (grandkids == null) {
                return;
            }
            COSBase pageBase = ((COSObject) kids).getItem(COSName.PG);

            if (pageBase != null && pageBase instanceof COSObject) {
                currentPageRef = new ObjectRef(((COSObject) pageBase).getObjectNumber(),
                        ((COSObject) pageBase).getGenerationNumber());
            }

            HtmlTag tag = getTag(name, roleMap);
            boolean startedLink = false;
            boolean ignoreTag = false;
            if ("link".equals(tag.clazz)) {
                state.inLink = true;
                startedLink = true;
            }
            if (!state.inLink) {
                //TODO: currently suppressing span and lbody...
                // is this what we want to do?  What else should we suppress?
                if ("span".equals(tag.tag)) {
                    ignoreTag = true;
                } else if ("lbody".equals(tag.clazz)) {
                    ignoreTag = true;
                }
                if (!ignoreTag) {
                    if (tag.clazz != null && tag.clazz.trim().length() > 0) {
                        xhtml.startElement(tag.tag, "class", tag.clazz);
                    } else {
                        xhtml.startElement(tag.tag);
                    }
                }
            }

            recurse(grandkids, currentPageRef, depth + 1, paragraphs, roleMap);
            if (startedLink) {
                writeLink();
            }
            if (!state.inLink && !startedLink && !ignoreTag) {
                xhtml.endElement(tag.tag);
            }
        } else if (kids instanceof COSInteger) {
            int mcidInt = ((COSInteger) kids).intValue();
            MCID mcid = new MCID(currentPageRef, mcidInt);
            if (paragraphs.containsKey(mcid)) {
                if (state.inLink) {
                    state.hrefAnchorBuilder.append(paragraphs.get(mcid));
                } else {
                    try {
                        //if it isn't a uri, output this anyhow
                        writeString(paragraphs.get(mcid));
                    } catch (IOException e) {
                        handleCatchableIOE(e);
                    }
                }
                state.processedMCIDs.add(mcid);
            } else {
                //TODO: log can't find mcid
            }
        } else if (kids instanceof COSDictionary) {
            //TODO: check for other types of dictionary?
            COSDictionary dict = (COSDictionary) kids;
            COSDictionary anchor = dict.getCOSDictionary(COSName.A);
            //check for subtype /Link ?
            //COSName subtype = obj.getCOSName(COSName.SUBTYPE);
            if (anchor != null) {
                state.uri = anchor.getString(COSName.URI);
            } else {
                if (dict.containsKey(COSName.K)) {
                    recurse(dict.getDictionaryObject(COSName.K), currentPageRef, depth + 1,
                            paragraphs, roleMap);
                } else if (dict.containsKey(COSName.OBJ)) {
                    recurse(dict.getDictionaryObject(COSName.OBJ), currentPageRef, depth + 1,
                            paragraphs, roleMap);

                }
            }
        } else {
            //TODO: handle a different object?
        }
    }

    private void writeLink() throws SAXException, IOException {
        //This is only for uris, obv.
        //If we want to catch within doc references (GOTO, we need to cache those in state.
        //See testPDF_childAttachments.pdf for examples
        if (state.uri != null && state.uri.trim().length() > 0) {
            xhtml.startElement("a", "href", state.uri);
            xhtml.characters(state.hrefAnchorBuilder.toString());
            xhtml.endElement("a");
        } else {
            try {
                //if it isn't a uri, output this anyhow
                writeString(state.hrefAnchorBuilder.toString());
            } catch (IOException e) {
                handleCatchableIOE(e);
            }
        }
        state.hrefAnchorBuilder.setLength(0);
        state.inLink = false;
        state.uri = null;

    }

    private HtmlTag getTag(String name, Map<String, HtmlTag> roleMap) {
        if (roleMap.containsKey(name)) {
            return roleMap.get(name);
        }
        String lc = name.toLowerCase(Locale.US);
        if (COMMON_TAG_MAP.containsKey(lc)) {
            return COMMON_TAG_MAP.get(lc);
        }
        roleMap.put(name, new HtmlTag(DIV, name.toLowerCase(Locale.US)));
        return roleMap.get(name);
    }

    private Map<MCID, String> loadTextByMCID(List<ObjectRef> pageRefs) throws IOException {
        int pageCount = 1;
        Map<MCID, String> paragraphs = new HashMap<>();
        for (PDPage page : pdDocument.getPages()) {
            ObjectRef pageRef = pageRefs.get(pageCount - 1);
            PDFMarkedContentExtractor ex = new PDFMarkedContentExtractor();
            try {
                ex.processPage(page);
            } catch (IOException e) {
                handleCatchableIOE(e);
                continue;
            }
            for (PDMarkedContent c : ex.getMarkedContents()) {
                //TODO: at some point also handle
                // 1. c.getActualText()
                // 2. c.getExpandedForm()
                // 3. c.getAlternateDescription()
                // 4. c.getLanguage()

                List<Object> objects = c.getContents();
                StringBuilder sb = new StringBuilder();
                //TODO: sort text positions? Figure out when to add/remove a newline and/or space?
                for (Object o : objects) {
                    if (o instanceof TextPosition) {
                        String unicode = ((TextPosition) o).getUnicode();
                        if (unicode != null) {
                            sb.append(unicode);
                        }
                    }
                    /*
                    TODO: do we want to do anything with these?
                    TODO: Are there other types of objects we need to handle here?
                    else if (o instanceof PDImageXObject) {

                    } else if (o instanceof PDTransparencyGroup) {

                    } else if (o instanceof PDMarkedContent) {

                    } else if (o instanceof PDFormXObject) {

                    } else {
                        throw new RuntimeException("can't handle "+o.getClass());
                    }*/
                }

                int mcidInt = c.getMCID();
                MCID mcid = new MCID(pageRef, mcidInt);
                String p = sb.toString();
                if (c.getTag().equals("P")) {
                    p = p.trim();
                }

                if (mcidInt < 0) {
                    //mcidInt == -1 for text bits that do not have an actual
                    //mcid -- concatenate these bits
                    if (paragraphs.containsKey(mcid)) {
                        p = paragraphs.get(mcid) + "\n" + p;
                    }
                }

                paragraphs.put(mcid, p);

            }
            pageCount++;
        }
        return paragraphs;
    }

    private static class State {
        Set<MCID> processedMCIDs = new HashSet<>();
        boolean inLink = false;
        //int tableDepth = 0;
        private StringBuilder hrefAnchorBuilder = new StringBuilder();
        private String uri = null;
        //private int tdDepth = 0;
    }

    private static class HtmlTag {
        private final String tag;
        private final String clazz;

        HtmlTag() {
            this("");
        }

        HtmlTag(String tag) {
            this(tag, "");
        }

        HtmlTag(String tag, String clazz) {
            this.tag = tag;
            this.clazz = clazz;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            HtmlTag htmlTag = (HtmlTag) o;

            if (!Objects.equals(tag, htmlTag.tag)) {
                return false;
            }
            return Objects.equals(clazz, htmlTag.clazz);
        }

        @Override
        public int hashCode() {
            int result = tag != null ? tag.hashCode() : 0;
            result = 31 * result + (clazz != null ? clazz.hashCode() : 0);
            return result;
        }
    }

    private static class ObjectRef {
        private final long objId;
        private final int version;

        public ObjectRef(long objId, int version) {
            this.objId = objId;
            this.version = version;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ObjectRef objectRef = (ObjectRef) o;
            return objId == objectRef.objId && version == objectRef.version;
        }

        @Override
        public int hashCode() {
            return Objects.hash(objId, version);
        }

        @Override
        public String toString() {
            return "ObjectRef{" + "objId=" + objId + ", version=" + version + '}';
        }
    }

    /**
     * In PDF land, MCID are integers that should be unique _per page_.
     * This class includes the object ref to the page and the mcid
     * so that this should be a cross-document unique key to
     * given content.
     * <p>
     * If the mcid integer == -1, that means that there is text on the page
     * not assigned to any marked content.
     */
    private static class MCID {
        //this is the object ref to the particular page
        private final ObjectRef objectRef;
        private final int mcid;

        public MCID(ObjectRef objectRef, int mcid) {
            this.objectRef = objectRef;
            this.mcid = mcid;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MCID mcid1 = (MCID) o;
            return mcid == mcid1.mcid && Objects.equals(objectRef, mcid1.objectRef);
        }

        @Override
        public int hashCode() {
            return Objects.hash(objectRef, mcid);
        }

        @Override
        public String toString() {
            return "MCID{" + "objectRef=" + objectRef + ", mcid=" + mcid + '}';
        }
    }
}
