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
package org.apache.tika.parser.microsoft;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.HWPFOldDocument;
import org.apache.poi.hwpf.OldWordFileFormatException;
import org.apache.poi.hwpf.extractor.Word6Extractor;
import org.apache.poi.hwpf.model.FieldsDocumentPart;
import org.apache.poi.hwpf.model.PicturesTable;
import org.apache.poi.hwpf.model.StyleDescription;
import org.apache.poi.hwpf.usermodel.CharacterRun;
import org.apache.poi.hwpf.usermodel.Field;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Picture;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.hwpf.usermodel.Table;
import org.apache.poi.hwpf.usermodel.TableCell;
import org.apache.poi.hwpf.usermodel.TableRow;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.NPOIFSFileSystem;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class WordExtractor extends AbstractPOIFSExtractor {

    private static final char UNICODECHAR_NONBREAKING_HYPHEN = '\u2011';
    private static final char UNICODECHAR_ZERO_WIDTH_SPACE = '\u200b';

    public WordExtractor(ParseContext context) {
        super(context);
    }

    // True if we are currently in the named style tag:
    private boolean curStrikeThrough;
    private boolean curBold;
    private boolean curItalic;

    protected void parse(
            NPOIFSFileSystem filesystem, XHTMLContentHandler xhtml)
            throws IOException, SAXException, TikaException {
        parse(filesystem.getRoot(), xhtml);
    }

    protected void parse(
            DirectoryNode root, XHTMLContentHandler xhtml)
            throws IOException, SAXException, TikaException {
        HWPFDocument document;
        try {
            document = new HWPFDocument(root);
        } catch(OldWordFileFormatException e) {
            parseWord6(root, xhtml);
            return;
        }
        org.apache.poi.hwpf.extractor.WordExtractor wordExtractor =
            new org.apache.poi.hwpf.extractor.WordExtractor(document);

        addTextIfAny(xhtml, "header", wordExtractor.getHeaderText());

        // Grab the list of pictures. As far as we can tell,
        //  the pictures should be in order, and may be directly
        //  placed or referenced from an anchor
        PicturesTable pictureTable = document.getPicturesTable();
        PicturesSource pictures = new PicturesSource(document);
        
        // Do the main paragraph text
        Range r = document.getRange();
        for(int i=0; i<r.numParagraphs(); i++) {
           Paragraph p = r.getParagraph(i);
           i += handleParagraph(p, 0, r, document, pictures, pictureTable, xhtml);
        }

        // Do everything else
        for (String paragraph: wordExtractor.getMainTextboxText()) {
            xhtml.element("p", paragraph);
        }

	for (String paragraph : wordExtractor.getFootnoteText()) {
            xhtml.element("p", paragraph);
        }

        for (String paragraph : wordExtractor.getCommentsText()) {
            xhtml.element("p", paragraph);
        }

        for (String paragraph : wordExtractor.getEndnoteText()) {
            xhtml.element("p", paragraph);
        }

        addTextIfAny(xhtml, "footer", wordExtractor.getFooterText());

        // Handle any pictures that we haven't output yet
        for(Picture p = pictures.nextUnclaimed(); p != null; ) {
           handlePictureCharacterRun(
                 null, p, pictures, xhtml
           );
           p = pictures.nextUnclaimed();
        }
        
        // Handle any embeded office documents
        try {
            DirectoryEntry op = (DirectoryEntry) root.getEntry("ObjectPool");
            for (Entry entry : op) {
                if (entry.getName().startsWith("_")
                        && entry instanceof DirectoryEntry) {
                    handleEmbeddedOfficeDoc((DirectoryEntry) entry, xhtml);
                }
            }
        } catch(FileNotFoundException e) {
        }
    }
    
    private int handleParagraph(Paragraph p, int parentTableLevel, Range r, HWPFDocument document, 
          PicturesSource pictures, PicturesTable pictureTable, XHTMLContentHandler xhtml)
          throws SAXException, IOException, TikaException {
       // Note - a poi bug means we can't currently properly recurse
       //  into nested tables, so currently we don't
       if(p.isInTable() && p.getTableLevel() > parentTableLevel && parentTableLevel==0) {
          Table t = r.getTable(p);
          xhtml.startElement("table");
          xhtml.startElement("tbody");
          for(int rn=0; rn<t.numRows(); rn++) {
             TableRow row = t.getRow(rn);
             xhtml.startElement("tr");
             for(int cn=0; cn<row.numCells(); cn++) {
                TableCell cell = row.getCell(cn);
                xhtml.startElement("td");

                for(int pn=0; pn<cell.numParagraphs(); pn++) {
                   Paragraph cellP = cell.getParagraph(pn);
                   handleParagraph(cellP, p.getTableLevel(), cell, document, pictures, pictureTable, xhtml);
                }
                xhtml.endElement("td");
             }
             xhtml.endElement("tr");
          }
          xhtml.endElement("tbody");
          xhtml.endElement("table");
          return (t.numParagraphs()-1);
       }

       TagAndStyle tas;

       if (document.getStyleSheet().numStyles()>p.getStyleIndex()) {
           StyleDescription style =
              document.getStyleSheet().getStyleDescription(p.getStyleIndex());
           if (style!=null) {
               tas = buildParagraphTagAndStyle(style.getName(), (parentTableLevel>0));
           } else {
               tas = new TagAndStyle("p", null);
           }
       } else {
           tas = new TagAndStyle("p", null);
       }

       if(tas.getStyleClass() != null) {
           xhtml.startElement(tas.getTag(), "class", tas.getStyleClass());
       } else {
           xhtml.startElement(tas.getTag());
       }

       for(int j=0; j<p.numCharacterRuns(); j++) {
          CharacterRun cr = p.getCharacterRun(j);

          // FIELD_BEGIN_MARK:
          if (cr.text().getBytes()[0] == 0x13) {
             Field field = document.getFields().getFieldByStartOffset(FieldsDocumentPart.MAIN,
                                                                      cr.getStartOffset());
             if (field != null && field.getType() == 58) {
               // Embedded Object: add a <div
               // class="embedded" id="_X"/> so consumer can see where
               // in the main text each embedded document
               // occurred:
               String id = "_" + field.getMarkSeparatorCharacterRun(r).getPicOffset();
               AttributesImpl attributes = new AttributesImpl();
               attributes.addAttribute("", "class", "class", "CDATA", "embedded");
               attributes.addAttribute("", "id", "id", "CDATA", id);
               xhtml.startElement("div", attributes);
               xhtml.endElement("div");
             }
          }
          
          if(cr.text().equals("\u0013")) {
             j += handleSpecialCharacterRuns(p, j, tas.isHeading(), pictures, xhtml);
          } else if(cr.text().startsWith("\u0008")) {
             // Floating Picture(s)
             for(int pn=0; pn<cr.text().length(); pn++) {
                // Assume they're in the order from the unclaimed list...
                Picture picture = pictures.nextUnclaimed();
                
                // Output
                handlePictureCharacterRun(cr, picture, pictures, xhtml);
             }
          } else if(pictureTable.hasPicture(cr)) {
             // Inline Picture
             Picture picture = pictures.getFor(cr);
             handlePictureCharacterRun(cr, picture, pictures, xhtml);
          } else {
             handleCharacterRun(cr, tas.isHeading(), xhtml);
          }
       }
       
       // Close any still open style tags
       if (curStrikeThrough) {
         xhtml.endElement("s");
         curStrikeThrough = false;
       }
       if (curItalic) {
         xhtml.endElement("i");
         curItalic = false;
       }
       if (curBold) {
         xhtml.endElement("b");
         curBold = false;
       }

       xhtml.endElement(tas.getTag());
       
       return 0;
    }
    
    private void handleCharacterRun(CharacterRun cr, boolean skipStyling, XHTMLContentHandler xhtml) 
          throws SAXException {
       // Skip trailing newlines
       if(!isRendered(cr) || cr.text().equals("\r"))
          return;
       
       if(!skipStyling) {
         if (cr.isBold() != curBold) {
           // Enforce nesting -- must close s and i tags
           if (curStrikeThrough) {
             xhtml.endElement("s");
             curStrikeThrough = false;
           }
           if (curItalic) {
             xhtml.endElement("i");
             curItalic = false;
           }
           if (cr.isBold()) {
             xhtml.startElement("b");
           } else {
             xhtml.endElement("b");
           }
           curBold = cr.isBold();
         }

         if (cr.isItalic() != curItalic) {
           // Enforce nesting -- must close s tag
           if (curStrikeThrough) {
             xhtml.endElement("s");
             curStrikeThrough = false;
           }
           if (cr.isItalic()) {
             xhtml.startElement("i");
           } else {
             xhtml.endElement("i");
           }
           curItalic = cr.isItalic();
         }

         if (cr.isStrikeThrough() != curStrikeThrough) {
           if (cr.isStrikeThrough()) {
             xhtml.startElement("s");
           } else {
             xhtml.endElement("s");
           }
           curStrikeThrough = cr.isStrikeThrough();
         }
       }
       
       // Clean up the text
       String text = cr.text();
       text = text.replace('\r', '\n');
       if(text.endsWith("\u0007")) {
          // Strip the table cell end marker
          text = text.substring(0, text.length()-1);
       }

       // Copied from POI's org/apache/poi/hwpf/converter/AbstractWordConverter.processCharacters:

       // Non-breaking hyphens are returned as char 30
       text = text.replace((char) 30, UNICODECHAR_NONBREAKING_HYPHEN);

       // Non-required hyphens to zero-width space
       text = text.replace((char) 31, UNICODECHAR_ZERO_WIDTH_SPACE);
       
       xhtml.characters(text);
    }
    /**
     * Can be \13..text..\15 or \13..control..\14..text..\15 .
     * Nesting is allowed
     */
    private int handleSpecialCharacterRuns(Paragraph p, int index, boolean skipStyling, 
          PicturesSource pictures, XHTMLContentHandler xhtml) throws SAXException, TikaException, IOException {
       List<CharacterRun> controls = new ArrayList<CharacterRun>();
       List<CharacterRun> texts = new ArrayList<CharacterRun>();
       boolean has14 = false;
       
       // Split it into before and after the 14
       int i;
       for(i=index+1; i<p.numCharacterRuns(); i++) {
          CharacterRun cr = p.getCharacterRun(i);
          if(cr.text().equals("\u0013")) {
             // Nested, oh joy...
             int increment = handleSpecialCharacterRuns(p, i+1, skipStyling, pictures, xhtml);
             i += increment;
          } else if(cr.text().equals("\u0014")) {
             has14 = true;
          } else if(cr.text().equals("\u0015")) {
             if(!has14) {
                texts = controls;
                controls = new ArrayList<CharacterRun>();
             }
             break;
          } else {
             if(has14) {
                texts.add(cr);
             } else {
                controls.add(cr);
             }
          }
       }
       
       // Do we need to do something special with this?
       if(controls.size() > 0) {
          String text = controls.get(0).text();
          for(int j=1; j<controls.size(); j++) {
             text += controls.get(j).text();
          }
          
          if(text.startsWith("HYPERLINK") && text.indexOf('"') > -1) {
             String url = text.substring(
                   text.indexOf('"') + 1,
                   text.lastIndexOf('"')
             );
             xhtml.startElement("a", "href", url);
             for(CharacterRun cr : texts) {
                handleCharacterRun(cr, skipStyling, xhtml);
             }
             xhtml.endElement("a");
          } else {
             // Just output the text ones
             for(CharacterRun cr : texts) {
                if(pictures.hasPicture(cr)) {
                   Picture picture = pictures.getFor(cr);
                   handlePictureCharacterRun(cr, picture, pictures, xhtml);
                } else {
                   handleCharacterRun(cr, skipStyling, xhtml);
                }
             }
          }
       } else {
          // We only had text
          // Output as-is
          for(CharacterRun cr : texts) {
             handleCharacterRun(cr, skipStyling, xhtml);
          }
       }
       
       // Tell them how many to skip over
       return i-index;
    }

    private void handlePictureCharacterRun(CharacterRun cr, Picture picture, PicturesSource pictures, XHTMLContentHandler xhtml) 
          throws SAXException, IOException, TikaException {
       if(!isRendered(cr) || picture == null) {
          // Oh dear, we've run out...
          // Probably caused by multiple \u0008 images referencing
          //  the same real image
          return;
       }
       
       // Which one is it?
       String extension = picture.suggestFileExtension();
       int pictureNumber = pictures.pictureNumber(picture);
       
       // Make up a name for the picture
       // There isn't one in the file, but we need to be able to reference
       //  the picture from the img tag and the embedded resource
       String filename = "image"+pictureNumber+(extension.length()>0 ? "."+extension : "");
       
       // Grab the mime type for the picture
       String mimeType = picture.getMimeType();

       // Output the img tag
       AttributesImpl attr = new AttributesImpl();
       attr.addAttribute("", "src", "src", "CDATA", "embedded:" + filename);
       attr.addAttribute("", "alt", "alt", "CDATA", filename);
       xhtml.startElement("img", attr);
       xhtml.endElement("img");

       // Have we already output this one?
       // (Only expose each individual image once) 
       if(! pictures.hasOutput(picture)) {
          TikaInputStream stream = TikaInputStream.get(picture.getContent());
          handleEmbeddedResource(stream, filename, null, mimeType, xhtml, false);
          pictures.recordOutput(picture);
       }
    }
    
    /**
     * Outputs a section of text if the given text is non-empty.
     *
     * @param xhtml XHTML content handler
     * @param section the class of the &lt;div/&gt; section emitted
     * @param text text to be emitted, if any
     * @throws SAXException if an error occurs
     */
    private void addTextIfAny(
            XHTMLContentHandler xhtml, String section, String text)
            throws SAXException {
        if (text != null && text.length() > 0) {
            xhtml.startElement("div", "class", section);
            xhtml.element("p", text);
            xhtml.endElement("div");
        }
    }
    
    protected void parseWord6(
            NPOIFSFileSystem filesystem, XHTMLContentHandler xhtml)
            throws IOException, SAXException, TikaException {
        parseWord6(filesystem.getRoot(), xhtml);
    }

    protected void parseWord6(
            DirectoryNode root, XHTMLContentHandler xhtml)
            throws IOException, SAXException, TikaException {
        HWPFOldDocument doc = new HWPFOldDocument(root);
        Word6Extractor extractor = new Word6Extractor(doc);
        
        for(String p : extractor.getParagraphText()) {
            xhtml.element("p", p);
        }
    }

    private static final Map<String,TagAndStyle> fixedParagraphStyles = new HashMap<String,TagAndStyle>();
    private static final TagAndStyle defaultParagraphStyle = new TagAndStyle("p", null);
    static {
        fixedParagraphStyles.put("Default", defaultParagraphStyle);
        fixedParagraphStyles.put("Normal", defaultParagraphStyle);
        fixedParagraphStyles.put("heading", new TagAndStyle("h1", null));
        fixedParagraphStyles.put("Heading", new TagAndStyle("h1", null));
        fixedParagraphStyles.put("Title", new TagAndStyle("h1", "title"));
        fixedParagraphStyles.put("Subtitle", new TagAndStyle("h2", "subtitle"));
        fixedParagraphStyles.put("HTML Preformatted", new TagAndStyle("pre", null));
    }
    
    /**
     * Given a style name, return what tag should be used, and
     *  what style should be applied to it. 
     */
    public static TagAndStyle buildParagraphTagAndStyle(String styleName, boolean isTable) {
       TagAndStyle tagAndStyle = fixedParagraphStyles.get(styleName);
       if (tagAndStyle != null) {
           return tagAndStyle;
       }

       if (styleName.equals("Table Contents") && isTable) {
           return defaultParagraphStyle;
       }

       String tag = "p";
       String styleClass = null;

       if(styleName.startsWith("heading") || styleName.startsWith("Heading")) {
           // "Heading 3" or "Heading2" or "heading 4"
           int num = 1;
           try {
               num = Integer.parseInt(
                                      styleName.substring(styleName.length()-1)
                                      );
           } catch(NumberFormatException e) {}
           // Turn it into a H1 - H6 (H7+ isn't valid!)
           tag = "h" + Math.min(num, 6);
       } else {
           styleClass = styleName.replace(' ', '_');
           styleClass = styleClass.substring(0,1).toLowerCase() +
               styleClass.substring(1);
       }

       return new TagAndStyle(tag,styleClass);
    }
    
    public static class TagAndStyle {
       private String tag;
       private String styleClass;
       public TagAndStyle(String tag, String styleClass) {
         this.tag = tag;
         this.styleClass = styleClass;
       }
       public String getTag() {
         return tag;
       }
       public String getStyleClass() {
         return styleClass;
       }
       public boolean isHeading() {
          return tag.length()==2 && tag.startsWith("h");
       }
    }
    
    /**
     * Determines if character run should be included in the extraction.
     * 
     * @param cr character run.
     * @return true if character run should be included in extraction.
     */
    private boolean isRendered(final CharacterRun cr) {
 	   return cr == null || !cr.isMarkedDeleted();
    }
    
    
    /**
     * Provides access to the pictures both by offset, iteration
     *  over the un-claimed, and peeking forward
     */
    private static class PicturesSource {
       private PicturesTable picturesTable; 
       private Set<Picture> output = new HashSet<Picture>();
       private Map<Integer,Picture> lookup;
       private List<Picture> nonU1based;
       private List<Picture> all;
       private int pn = 0;
       
       private PicturesSource(HWPFDocument doc) {
          picturesTable = doc.getPicturesTable();
          all = picturesTable.getAllPictures();
          
          // Build the Offset-Picture lookup map
          lookup = new HashMap<Integer, Picture>();
          for(Picture p : all) {
             lookup.put(p.getStartOffset(), p);
          }
          
          // Work out which Pictures aren't referenced by
          //  a \u0001 in the main text
          // These are \u0008 escher floating ones, ones 
          //  found outside the normal text, and who
          //  knows what else...
          nonU1based = new ArrayList<Picture>();
          nonU1based.addAll(all);
          Range r = doc.getRange();
          for(int i=0; i<r.numCharacterRuns(); i++) {
             CharacterRun cr = r.getCharacterRun(i);
             if(picturesTable.hasPicture(cr)) {
                Picture p = getFor(cr);
                int at = nonU1based.indexOf(p);
                nonU1based.set(at, null);
             }
          }
       }
       
       private boolean hasPicture(CharacterRun cr) {
          return picturesTable.hasPicture(cr);
       }
       
       private void recordOutput(Picture picture) {
          output.add(picture);
       }
       private boolean hasOutput(Picture picture) {
          return output.contains(picture);
       }
       
       private int pictureNumber(Picture picture) {
          return all.indexOf(picture) + 1;
       }
       
       private Picture getFor(CharacterRun cr) {
          return lookup.get(cr.getPicOffset());
       }
       
       /**
        * Return the next unclaimed one, used towards
        *  the end
        */
       private Picture nextUnclaimed() {
          Picture p = null;
          while(pn < nonU1based.size()) {
             p = nonU1based.get(pn);
             pn++;
             if(p != null) return p;
          }
          return null;
       }
    }
}
