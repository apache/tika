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
import org.apache.poi.hwpf.model.PicturesTable;
import org.apache.poi.hwpf.model.StyleDescription;
import org.apache.poi.hwpf.usermodel.CharacterRun;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Picture;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.hwpf.usermodel.Table;
import org.apache.poi.hwpf.usermodel.TableCell;
import org.apache.poi.hwpf.usermodel.TableRow;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

public class WordExtractor extends AbstractPOIFSExtractor {

    public WordExtractor(ParseContext context) {
        super(context);
    }

    protected void parse(
            POIFSFileSystem filesystem, XHTMLContentHandler xhtml)
            throws IOException, SAXException, TikaException {
        HWPFDocument document;
        try {
            document = new HWPFDocument(filesystem);
        } catch(OldWordFileFormatException e) {
            parseWord6(filesystem, xhtml);
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
            DirectoryEntry op =
                (DirectoryEntry) filesystem.getRoot().getEntry("ObjectPool");
            for (Entry entry : op) {
                if (entry.getName().startsWith("_")
                        && entry instanceof DirectoryEntry) {
                    handleEmbededOfficeDoc((DirectoryEntry) entry, xhtml);
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

       StyleDescription style = 
          document.getStyleSheet().getStyleDescription(p.getStyleIndex());
       TagAndStyle tas = buildParagraphTagAndStyle(
             style.getName(), (parentTableLevel>0)
       );

       if(tas.getStyleClass() != null) {
          xhtml.startElement(tas.getTag(), "class", tas.getStyleClass());
       } else {
          xhtml.startElement(tas.getTag());
       }
       
       for(int j=0; j<p.numCharacterRuns(); j++) {
          CharacterRun cr = p.getCharacterRun(j);
          
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
       
       xhtml.endElement(tas.getTag());
       
       return 0;
    }
    
    private void handleCharacterRun(CharacterRun cr, boolean skipStyling, XHTMLContentHandler xhtml) 
          throws SAXException {
       // Skip trailing newlines
       if(cr.text().equals("\r"))
          return;
       
       List<String> tags = new ArrayList<String>();
       if(!skipStyling) {
          if(cr.isBold()) tags.add("b");
          if(cr.isItalic()) tags.add("i");
          if(cr.isStrikeThrough()) tags.add("s");
          for(String tag : tags) {
             xhtml.startElement(tag);
          }
       }
       
       // Clean up the text
       String text = cr.text();
       text = text.replace('\r', '\n');
       if(text.endsWith("\u0007")) {
          // Strip the table cell end marker
          text = text.substring(0, text.length()-1);
       }
       
       xhtml.characters(text);

       for(int tn=tags.size()-1; tn>=0; tn--) {
          xhtml.endElement(tags.get(tn));
       }
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
       if(picture == null) {
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
       xhtml.startElement("img", "src", "embedded:" + filename);
       xhtml.endElement("img");

       // Have we already output this one?
       // (Only expose each individual image once) 
       if(! pictures.hasOutput(picture)) {
          TikaInputStream stream = TikaInputStream.get(picture.getContent());
          handleEmbeddedResource(stream, filename, mimeType, xhtml, false);
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
            POIFSFileSystem filesystem, XHTMLContentHandler xhtml)
            throws IOException, SAXException, TikaException {
        HWPFOldDocument doc = new HWPFOldDocument(filesystem);
        Word6Extractor extractor = new Word6Extractor(doc);
        
        for(String p : extractor.getParagraphText()) {
            xhtml.element("p", p);
        }
    }
    
    /**
     * Given a style name, return what tag should be used, and
     *  what style should be applied to it. 
     */
    public static TagAndStyle buildParagraphTagAndStyle(String styleName, boolean isTable) {
       String tag = "p";
       String styleClass = null;
       
       if(styleName.equals("Default") || styleName.equals("Normal")) {
          // Already setup
       } else if(styleName.equals("Table Contents") && isTable) {
          // Already setup
       } else if(styleName.equals("heading") || styleName.equals("Heading")) {
          tag = "h1";
       } else if(styleName.startsWith("heading") || styleName.startsWith("Heading")) {
          // "Heading 3" or "Heading2" or "heading 4"
          int num = 1;
          try {
             num = Integer.parseInt( 
                   styleName.substring(styleName.length()-1)
             );
          } catch(NumberFormatException e) {}
          tag = "h"+num;
       } else if(styleName.equals("Title")) {
          tag = "h1";
          styleClass = "title";
       } else if(styleName.equals("Subtitle")) {
          tag = "h2";
          styleClass = "subtitle";
       } else if(styleName.equals("HTML Preformatted")) {
          tag = "pre";
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
          
          // Compute the Offset-Picture lookup
          lookup = new HashMap<Integer, Picture>();
          for(Picture p : all) {
             // TODO Make this nicer when POI 3.7 is out
             String name = p.suggestFullFileName();
             if(name.indexOf('.') > -1)
                name = name.substring(0, name.indexOf('.'));
             int offset = Integer.parseInt(name, 16);
             lookup.put(offset, p);
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
