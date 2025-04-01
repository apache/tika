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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.StringUtils;


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
    private static final String VISUAL_BRUSH = "VisualBrush";
    private static final String TRANSFORM = "Transform";
    private static final String UNICODE_STRING = "UnicodeString";
    private static final String ORIGIN_X = "OriginX";
    private static final String ORIGIN_Y = "OriginY";
    private static final String BIDI_LEVEL = "BidiLevel";
    private static final String INDICES = "Indices";
    private static final String NAME = "Name";
    private static final String FONT_RENDERING_EM_SIZE = "FontRenderingEmSize";
    private static final String FONT_URI = "FontUri";
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

    private static final char[] SPACE = new char[]{' '};

    // Estimate width of glyph when better information is not available, measured in em
    private static final float ESTIMATE_GLYPH_WIDTH = 0.5f;

    // The threshold for the horizontal distance between glyph runs to insert a whitespace, measured in em
    private static final float WHITESPACE_THRESHOLD = 0.3f;

    // The threshold for the horizontal distance between glyphs to split that glyph run into two, measured in em
    private static final float SPLIT_THRESHOLD = 1.0f;

    // The threshold for the vertical distance between glyph runs to be considered on the same row, measured in em
    private static final float ROW_COMBINE_THRESHOLD = 0.5f;

    //sort based on y coordinate of first element in each row
    //this requires every row to have at least one element
    private static Comparator<? super List<GlyphRun>> ROW_SORTER =
            (Comparator<List<GlyphRun>>) (o1, o2) -> {
                if (o1.get(0).originY < o2.get(0).originY) {
                    return -1;
                } else if (o1.get(0).originY > o2.get(0).originY) {
                    return 1;
                }
                return 0;
            };
    private static Comparator<GlyphRun> LTR_SORTER = new Comparator<GlyphRun>() {
        @Override
        public int compare(GlyphRun a, GlyphRun b) {
            return Float.compare(a.left(), b.left());
        }
    };
    private static Comparator<GlyphRun> RTL_SORTER = new Comparator<GlyphRun>() {
        @Override
        public int compare(GlyphRun a, GlyphRun b) {
            return Float.compare(b.left(), a.left());
        }
    };
    private final XHTMLContentHandler xhml;
    private final Map<String, Metadata> embeddedInfos;
    //path in zip file for an image rendered on this page
    private String imageSourcePathInZip = null;
    //embedded images sometimes include full path info of original image
    private String originalLocationOnDrive = null;
    //buffer for the glyph runs within a given canvas
    //in insertion order
    private Map<String, List<GlyphRun>> canvases = new LinkedHashMap<>();
    private Set<String> urls = new LinkedHashSet<String>();
    private Stack<String> canvasStack = new Stack<>();

    public XPSPageContentHandler(XHTMLContentHandler xhtml, Map<String, Metadata> embeddedInfos) {
        this.xhml = xhtml;
        this.embeddedInfos = embeddedInfos;
    }

    private static String getVal(String localName, Attributes atts) {
        for (int i = 0; i < atts.getLength(); i++) {
            if (localName.equals(atts.getLocalName(i))) {
                return atts.getValue(i);
            }
        }
        return null;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts)
            throws SAXException {
        if (CANVAS.equals(localName)) {
            String clip = getVal(CLIP, atts);
            if (clip == null) {
                canvasStack.push(NULL_CLIP);
            } else {
                canvasStack.push(clip);
            }
            return;
        } else if (VISUAL_BRUSH.equals(localName)) {
            // Also push visual brush transform onto stack as this will move children
            String transform = getVal(TRANSFORM, atts);
            if (transform == null) {
                canvasStack.push(NULL_CLIP);
            } else {
                canvasStack.push(transform);
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
        Integer bidilevel = null;
        List<GlyphIndex> indices = null;
        float fontSize = 0;
        String fontUri = null;

        for (int i = 0; i < atts.getLength(); i++) {
            String lName = atts.getLocalName(i);
            String value = atts.getValue(i);
            value = (value == null) ? "" : value.trim();

            if (ORIGIN_X.equals(lName) && value.length() > 0) {
                try {
                    originX = Float.parseFloat(value);
                } catch (NumberFormatException e) {
                    throw new SAXException(e);
                }
            } else if (ORIGIN_Y.equals(lName) && value.length() > 0) {
                try {
                    originY = Float.parseFloat(value);
                } catch (NumberFormatException e) {
                    throw new SAXException(e);
                }
            } else if (UNICODE_STRING.equals(lName)) {
                unicodeString = atts.getValue(i);
            } else if (BIDI_LEVEL.equals(lName) && value.length() > 0) {
                try {
                    bidilevel = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new SAXException(e);
                }
            } else if (INDICES.equals(lName)) {
                indices = parseIndicesString(value);
            } else if (NAME.equals(lName)) {
                name = value;
            } else if (FONT_RENDERING_EM_SIZE.equals(lName)) {
                fontSize = Float.parseFloat(value);
            } else if (FONT_URI.equals(lName)) {
                fontUri = value;
            }
        }
        if (unicodeString != null) {
            originX = (originX == null) ? Integer.MIN_VALUE : originX;
            originY = (originY == null) ? Integer.MAX_VALUE : originY;
            StringBuilder canvasStringBuilder = new StringBuilder();
            for (String s : canvasStack) {
                canvasStringBuilder.append(s);
                canvasStringBuilder.append(';');
            }
            String canvasCombined = canvasStringBuilder.toString();
            List<GlyphRun> runs = canvases.get(canvasCombined);
            if (runs == null) {
                runs = new ArrayList<>();
            }
            if (indices == null) {
                indices = new ArrayList<>();
            }
            runs.add(new GlyphRun(name, originY, originX, unicodeString, bidilevel, indices, fontSize, fontUri));
            canvases.put(canvasCombined, runs);
        }
    }

    // Parses a indices string into a list of GlyphIndex
    private List<GlyphIndex> parseIndicesString(String indicesString) throws SAXException {
        try {
            ArrayList<GlyphIndex> indices = new ArrayList<>();
            for (String indexString : indicesString.split(";", -1)) {
                // We only want to extract the advance which will be the second comma separated value
                String[] commaSplit = indexString.split(",", -1);
                if (commaSplit.length < 2 || StringUtils.isBlank(commaSplit[1])) {
                    indices.add(new GlyphIndex(0.0f));
                } else {
                    // Advance is measured in hundredths so divide by 100
                    float advance = Float.parseFloat(commaSplit[1]) / 100.0f;
                    indices.add(new GlyphIndex(advance));
                }
            }
            return indices;
        } catch (NumberFormatException e) {
            throw new SAXException(e);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (CANVAS.equals(localName)) {
            if (!canvasStack.isEmpty()) {
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
        row = splitRow(row);
        sortRow(row);

        xhml.startElement(P);
        GlyphRun previous = null;
        for (GlyphRun run : row) {
            if (previous != null) {
                float distanceFromPrevious = run.left() - previous.right();
                float averageFontSize = (run.fontSize + previous.fontSize) / 2f;
                if (distanceFromPrevious > averageFontSize * WHITESPACE_THRESHOLD) {
                    xhml.ignorableWhitespace(SPACE, 0, SPACE.length);
                }
            }
            xhml.characters(run.unicodeString);
            previous = run;
        }
        xhml.endElement(P);
    }

    // Returns a new list of glyph runs in a row after splitting any runs with large advances into multiple runs
    // This fixes issues where a single run has a large advance and text visually is placed in that gap so should be read in a different order
    private static List<GlyphRun> splitRow(List<GlyphRun> row) {
        List<GlyphRun> newRuns = new ArrayList<>();
        for (int j = 0; j < row.size(); j++) {
            GlyphRun run = row.get(j);
            // TODO: Implement splitting for RTL too
            if (run.direction != GlyphRun.DIRECTION.LTR) {
                newRuns.add(run);
                continue;
            }
            float width = 0f;
            for (int i = 0; i < run.indices.size() - 1; i++) {
                GlyphIndex index = run.indices.get(i);
                if (index.advance == 0.0f) {
                    if (i == 0) {
                        // If this is the first glyph use hard coded estimate
                        width += ESTIMATE_GLYPH_WIDTH;
                    } else {
                        // If advance is 0.0 it is probably the last glyph in the run, we don't know how wide it is so we use the average of the previous widths as an estimate
                        width += width / i;
                    }
                } else {
                    width += index.advance;
                }
                if (index.advance > SPLIT_THRESHOLD) {
                    newRuns.add(new GlyphRun(
                            run.name,
                            run.originY,
                            run.originX,
                            run.unicodeString.substring(0, i + 1),
                            null,
                            run.indices.subList(0, i + 1),
                            run.fontSize,
                            run.fontUri
                    ));
                    run.indices.set(i, new GlyphIndex(0.0f));
                    run = new GlyphRun(
                        run.name,
                        run.originY,
                        run.originX + width * run.fontSize,
                        run.unicodeString.substring(i + 1, run.unicodeString.length()),
                        null,
                        run.indices.subList(i + 1, run.indices.size()),
                        run.fontSize,
                        run.fontUri
                    );
                    i = 0;
                    width = 0f;
                }
            }
            newRuns.add(run);
        }
        return newRuns;
    }

    private static void sortRow(List<GlyphRun> row) {
        boolean allRTL = true;
        for (GlyphRun run : row)  {
            if (run.unicodeString.isBlank()) {
                // ignore whitespace for all RTL check
                continue;
            }
            if (run.direction == GlyphRun.DIRECTION.LTR) {
                allRTL = false;
                break;
            }
        }
        if (allRTL) {
            // If all the text in a row is RTL then sort it in reverse
            java.util.Collections.sort(row, RTL_SORTER);
        } else {
            // Otherwise sort it from left to right
            java.util.Collections.sort(row, LTR_SORTER);
        }
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
                List<GlyphRun> row = findClosestRowVertically(rows, glyphRun.originY);
                GlyphRun firstRun = row.get(0);
                float averageFontSize = (glyphRun.fontSize + firstRun.fontSize) / 2f;
                if (Math.abs(glyphRun.originY - firstRun.originY) < averageFontSize * ROW_COMBINE_THRESHOLD) {
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
                    rows.sort(ROW_SORTER);
                }
                if (glyphRun.originY > maxY) {
                    maxY = glyphRun.originY;
                }
            }
        }
        return rows;
    }

    // Search to find the closest row vertically to the y, if rows is empty returns null
    private List<GlyphRun> findClosestRowVertically(List<List<GlyphRun>> rows, float y) {
        List<GlyphRun> best = null;
        float bestDistance = Float.POSITIVE_INFINITY;
        // Loop backwards since normally XPS files are in order so we will match the last element
        // TODO: This could be optimised using a binary search since we know rows is sorted
        for (int i = rows.size() - 1; i >= 0; i--) {
            List<GlyphRun> row = rows.get(i);
            if (row.size() == 0) {
                continue;
            }
            float distance = Math.abs(row.get(row.size() - 1).originY - y);
            // There is nothing better than 0
            if (distance == 0f) {
                return row;
            }
            if (distance < bestDistance) {
                best = row;
                bestDistance = distance;
            }
        }
        return best;
    }

    final static class GlyphRun {

        //TODO: use name in conjunction with Frag information
        //to do a better job of extracting paragraph and table structure
        private final String name;
        private final float originY;
        private final float originX;
        private final String unicodeString;
        private final List<GlyphIndex> indices;
        private final DIRECTION direction;
        // Fonts em-size
        private final float fontSize;
        // Not used currently
        private final String fontUri;

        private GlyphRun(String name, float originY, float originX, String unicodeString,
                         Integer bidiLevel, List<GlyphIndex> indices, float fontSize, String fontUri) {
            this.name = name;
            this.unicodeString = unicodeString;
            this.originY = originY;
            this.originX = originX;
            this.fontSize = fontSize;
            this.fontUri = fontUri;
            this.indices = indices;
            if (bidiLevel == null) {
                direction = DIRECTION.LTR;
            } else {
                if (bidiLevel % 2 == 0) {
                    direction = DIRECTION.LTR;
                } else {
                    direction = DIRECTION.RTL;
                }
            }
        }

        private enum DIRECTION {
            LTR, RTL
        }

        private float left() {
            if (direction == DIRECTION.LTR) {
                return originX;
            } else {
                return originX - width();
            }
        }

        private float right() {
            if (direction == DIRECTION.LTR) {
                return originX + width();
            } else {
                return originX;
            }
        }

        private float width() {
            float width = 0.0f;
            for (int i = 0; i < indices.size(); i++) {
                if (indices.get(i).advance == 0.0) {
                    if (i == 0) {
                        // If this is the first glyph use hard coded estimate
                        width += ESTIMATE_GLYPH_WIDTH;
                    } else {
                        // If advance is 0.0 it is probably the last glyph in the run, we don't know how wide it is so we use the average of the previous widths as an estimate
                        width += width / i;
                    }
                } else {
                    width += indices.get(i).advance;
                }
            }
            return width * fontSize;
        }
    }

    final static class GlyphIndex {
        // TODO: Parse other elements of GlyphIndex
        
        // private int index;
        // private int clusterCodeUnitCount;
        // private int clusterGlyphCount;

        // The placement of the glyph that follows relative to the origin of the current glyph. Measured as a multiple of the fonts em-size.
        // Should be multiplied by the font em-size to get a value that can be compared across GlyphRuns
        // Will be zero for the last glpyh in a glyph run
        private final float advance;
        // private float uOffset;
        // private float vOffset;

        private GlyphIndex(float advance) {
            this.advance = advance;
        }
    }
}
