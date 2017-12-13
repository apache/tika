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
package org.apache.tika.parser.microsoft.ooxml.xps;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;


/**
 * Handles an individual page.  For now, this marks up
 * canvas entities in a &lt;div&gt; tag.  Based on the spec,
 * it currently relies on order within the xml for order of output
 * of text to xhtml.  We could do more complex processing of coordinates
 * for bidi-languages, but the spec implies that we should be able
 * to rely on storage order.
 * <p/>
 * As with our PDFParser, this currently dumps urls at the bottom of the page
 * and does not attempt to calculate the correct anchor text.
 * <p/>
 * TODO: integrate table markup
 */
class XPSPageContentHandler extends DefaultHandler {

    private static final String GLYPHS = "Glyphs";
    private static final String CANVAS = "Canvas";
    private static final String CLIP = "Clip";
    private static final String NULL_CLIP = "NULL_CLIP";
    private static final String UNICODE_STRING = "UnicodeString";
    private static final String ORIGIN_X = "OriginX";
    private static final String ORIGIN_Y = "OriginY";
    private static final String BIDI_LEVEL = "BidiLevel";
    private static final String INDICES = "Indices";
    private static final String NAME = "Name";
    private static final String PATH = "Path";
    private static final String NAVIGATE_URI = "FixedPage.NavigateUri";
    private static final String IMAGE_SOURCE = "ImageSource";
    private static final String IMAGE_BRUSH = "ImageBrush";
    private static final String AUTOMATION_PROPERITES_HELP_TEXT = "AutomationProperties.HelpText";

    private static final String URL_DIV = "urls";
    private static final String DIV = "div";
    private static final String CLASS = "class";
    private static final String PAGE = "page";
    private static final String CANVAS_SAX = "canvas";
    private static final String P = "p";
    private static final String HREF = "href";
    private static final String A = "a";


    private final XHTMLContentHandler xhml;

    //path in zip file for an image rendered on this page
    private String imageSourcePathInZip = null;
    //embedded images sometimes include full path info of original image
    private String originalLocationOnDrive = null;

    //buffer for the glyph runs within a given canvas
    //in insertion order
    private Map<String, List<GlyphRun>> canvases = new LinkedHashMap<>();

    private Set<String> urls = new LinkedHashSet();
    private Stack<String> canvasStack = new Stack<>();
    private final Map<String, Metadata> embeddedInfos;
    //sort based on y coordinate of first element in each row
    //this requires every row to have at least one element
    private static Comparator<? super List<GlyphRun>> ROW_SORTER = new Comparator<List<GlyphRun>>() {
        @Override
        public int compare(List<GlyphRun> o1, List<GlyphRun> o2) {
            if (o1.get(0).originY < o2.get(0).originY) {
                return -1;
            } else if (o1.get(0).originY > o2.get(0).originY) {
                return 1;
            }
            return 0;
        }
    };

    public XPSPageContentHandler(XHTMLContentHandler xhtml, Map<String, Metadata> embeddedInfos) {
        this.xhml = xhtml;
        this.embeddedInfos = embeddedInfos;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        if (CANVAS.equals(localName)) {
            String clip = getVal(CLIP, atts);
            if (clip == null) {
                canvasStack.push(NULL_CLIP);
            } else {
                canvasStack.push(clip);
            }
            return;
        } else if (PATH.equals(localName)) {
            //for now just grab them and dump them at the end of the page.
            String url = getVal(NAVIGATE_URI, atts);
            if (url != null) {
                urls.add(url);
            }
            originalLocationOnDrive = getVal(AUTOMATION_PROPERITES_HELP_TEXT, atts);
        } else if (IMAGE_BRUSH.equals(localName)) {
            imageSourcePathInZip = getVal(IMAGE_SOURCE, atts);
        }

        if (!GLYPHS.equals(localName)) {
            return;
        }
        String name = null;
        Float originX = null;
        Float originY = null;
        String unicodeString = null;
        Integer bidilevel = 1;
        String indicesString = null;

        for (int i = 0; i < atts.getLength(); i++) {
            String lName = atts.getLocalName(i);
            String value = atts.getValue(i);
            value = (value == null) ? "" : value.trim();

            if (ORIGIN_X.equals(lName) && value.length() > 0) {
                try {
                    originX = Float.parseFloat(atts.getValue(i));
                } catch (NumberFormatException e) {
                    throw new SAXException(e);
                }
            } else if (ORIGIN_Y.equals(lName) && value.length() > 0) {
                try {
                    originY = Float.parseFloat(atts.getValue(i));
                } catch (NumberFormatException e) {
                    throw new SAXException(e);
                }
            } else if (UNICODE_STRING.equals(lName)) {
                unicodeString = atts.getValue(i);
            } else if (BIDI_LEVEL.equals(lName) && value.length() > 0) {
                try {
                    bidilevel = Integer.parseInt(atts.getValue(i));
                } catch (NumberFormatException e) {
                    throw new SAXException(e);
                }
            } else if (INDICES.equals(lName)) {
                indicesString = atts.getValue(i);
            } else if (NAME.equals(lName)) {
                name = value;
            }
        }
        if (unicodeString != null) {
            originX = (originX == null) ? Integer.MIN_VALUE : originX;
            originY = (originY == null) ? Integer.MAX_VALUE : originY;
            String currentCanvasClip = (canvasStack.size() > 0) ? canvasStack.peek() : NULL_CLIP;
            List<GlyphRun> runs = canvases.get(currentCanvasClip);
            if (runs == null) {
                runs = new ArrayList<>();
            }
            runs.add(new GlyphRun(name, originY, originX, unicodeString, bidilevel, indicesString));
            canvases.put(currentCanvasClip, runs);
        }

    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (CANVAS.equals(localName)) {
            if (! canvasStack.isEmpty()) {
                canvasStack.pop();
            }
        } else if (PATH.equals(localName)) {
            //this assumes that there cannot be a path within a path
            //not sure if this is true or if we need to track path depth
            if (imageSourcePathInZip != null) {
                Metadata m = embeddedInfos.get(imageSourcePathInZip);
                if (m == null) {
                    m = new Metadata();
                }
                if (originalLocationOnDrive != null) {
                    String val = m.get(TikaCoreProperties.ORIGINAL_RESOURCE_NAME);
                    if (val == null) {
                        m.set(TikaCoreProperties.ORIGINAL_RESOURCE_NAME, originalLocationOnDrive);
                    }
                }
                m.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                        TikaCoreProperties.EmbeddedResourceType.INLINE.toString());
                embeddedInfos.put(imageSourcePathInZip, m);
            }
            //reset
            imageSourcePathInZip = null;
            originalLocationOnDrive = null;
        }
    }
    @Override
    public void startDocument() throws SAXException {
        xhml.startElement(DIV, CLASS, PAGE);
    }

    @Override
    public void endDocument() throws SAXException {
        writePage();
        xhml.endElement(DIV);
    }


    private final void writePage() throws SAXException {
        if (canvases.size() == 0) {
            return;
        }

        for (Map.Entry<String, List<GlyphRun>> e : canvases.entrySet()) {
            String clip = e.getKey();
            List<GlyphRun> runs = e.getValue();
            if (runs.size() == 0) {
                continue;
            }
            xhml.startElement(DIV, CLASS, CANVAS_SAX);
            //a list of rows sorted by the y of the first element in each row
            List<List<GlyphRun>> rows = buildRows(runs);
            for (List<GlyphRun> row : rows) {
                writeRow(row);
            }
            xhml.endElement(DIV);
        }
        //for now just dump the urls at the end of the page
        //At some point, we could link them back up to their
        //true anchor text.
        if (urls.size() > 0) {
            xhml.startElement(DIV, CLASS, URL_DIV);
            for (String u : urls) {
                xhml.startElement(A, HREF, u);
                xhml.characters(u);
                xhml.endElement(A);
            }
            xhml.endElement(DIV);
        }
        canvases.clear();
    }

    private void writeRow(List<GlyphRun> row) throws SAXException {
/*
        int rtl = 0;
        int ltr = 0;
        //if the row is entirely rtl, sort all as rtl
        //otherwise sort ltr
        for (GlyphRun r : row) {
            //ignore directionality of pure spaces
            if (r.unicodeString == null || r.unicodeString.trim().length() == 0) {
                continue;
            }
            if (r.direction == GlyphRun.DIRECTION.RTL) {
                rtl++;
            } else {
                ltr++;
            }
        }
        if (rtl > 0 && ltr == 0) {
            Collections.sort(row, GlyphRun.RTL_COMPARATOR);
        } else {
            Collections.sort(row, GlyphRun.LTR_COMPARATOR);
        }*/

        xhml.startElement(P);
        for (GlyphRun run : row) {
            //figure out if you need to add a space
            xhml.characters(run.unicodeString);
        }
        xhml.endElement(P);
    }

    //returns a List of rows (where a row is a list of glyphruns)
    //the List is sorted in increasing order of the first y of each row
    private List<List<GlyphRun>> buildRows(List<GlyphRun> glyphRuns) {
        List<List<GlyphRun>> rows = new ArrayList<>();
        float maxY = -1.0f;
        for (GlyphRun glyphRun : glyphRuns) {
            if (rows.size() == 0) {
                List<GlyphRun> row = new ArrayList<>();
                row.add(glyphRun);
                rows.add(row);
                continue;
            } else {
                boolean addedNewRow = false;
                //can rely on the last row having the highest y
                List<GlyphRun> row = rows.get(rows.size()-1);
                //0.5 is a purely heuristic/magical number that should be derived
                //from the data, not made up. TODO: fix this
                if (Math.abs(glyphRun.originY -row.get(0).originY) < 0.5) {
                    row.add(glyphRun);
                } else {
                    row = new ArrayList<>();
                    row.add(glyphRun);
                    rows.add(row);
                    addedNewRow = true;
                }
                //sort rows so that they are in ascending order of y
                //in most xps files in our test corpus, this is never triggered
                //because the runs are already ordered correctly
                if (maxY > -1.0f && addedNewRow && glyphRun.originY < maxY) {
                    Collections.sort(rows, ROW_SORTER);
                }
                if (glyphRun.originY > maxY) {
                    maxY = glyphRun.originY;
                }
            }
        }
        return rows;
    }

    private static String getVal(String localName, Attributes atts) {
        for (int i = 0; i < atts.getLength(); i++) {
            if (localName.equals(atts.getLocalName(i))) {
                return atts.getValue(i);
            }
        }
        return null;
    }

    final static class GlyphRun {

        private enum DIRECTION {
            LTR,
            RTL
        }

        //TODO: use name in conjunction with Frag information
        //to do a better job of extracting paragraph and table structure
        private final String name;
        private final float originY;
        private final float originX;//not currently used, but could be used for bidi text calculations
        private final String unicodeString;
        private final String indicesString;//not currently used, but could be used for width calculations

        //not used yet
        private final DIRECTION direction;

        private GlyphRun(String name, float originY, float originX, String unicodeString, Integer bidiLevel, String indicesString) {
            this.name = name;
            this.unicodeString = unicodeString;
            this.originY = originY;
            this.originX = originX;
            if (bidiLevel == null) {
                direction = DIRECTION.LTR;
            } else {
                if (bidiLevel % 2 == 0) {
                    direction = DIRECTION.LTR;
                } else {
                    direction = DIRECTION.RTL;
                }
            }
            this.indicesString = indicesString;
        }
    }

}
