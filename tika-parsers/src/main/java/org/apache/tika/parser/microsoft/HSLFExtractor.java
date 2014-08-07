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

import java.io.IOException;
import java.util.HashSet;

import org.apache.poi.hslf.HSLFSlideShow;
import org.apache.poi.hslf.model.Comment;
import org.apache.poi.hslf.model.HeadersFooters;
import org.apache.poi.hslf.model.MasterSheet;
import org.apache.poi.hslf.model.Notes;
import org.apache.poi.hslf.model.OLEShape;
import org.apache.poi.hslf.model.Picture;
import org.apache.poi.hslf.model.Shape;
import org.apache.poi.hslf.model.Slide;
import org.apache.poi.hslf.model.Table;
import org.apache.poi.hslf.model.TableCell;
import org.apache.poi.hslf.model.TextRun;
import org.apache.poi.hslf.model.TextShape;
import org.apache.poi.hslf.usermodel.ObjectData;
import org.apache.poi.hslf.usermodel.PictureData;
import org.apache.poi.hslf.usermodel.SlideShow;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.NPOIFSFileSystem;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class HSLFExtractor extends AbstractPOIFSExtractor {
   public HSLFExtractor(ParseContext context) {
      super(context);
   }
	
   protected void parse(
         NPOIFSFileSystem filesystem, XHTMLContentHandler xhtml)
         throws IOException, SAXException, TikaException {
       parse(filesystem.getRoot(), xhtml);
   }
    
   protected void parse(
         DirectoryNode root, XHTMLContentHandler xhtml)
         throws IOException, SAXException, TikaException {
      HSLFSlideShow ss = new HSLFSlideShow(root);
      SlideShow _show = new SlideShow(ss);
      Slide[] _slides = _show.getSlides();

      xhtml.startElement("div", "class", "slideShow");

      /* Iterate over slides and extract text */
      for( Slide slide : _slides ) {
         xhtml.startElement("div", "class", "slide");

         // Slide header, if present
         HeadersFooters hf = slide.getHeadersFooters();
         if (hf != null && hf.isHeaderVisible() && hf.getHeaderText() != null) {
            xhtml.startElement("p", "class", "slide-header");

            xhtml.characters( hf.getHeaderText() );

            xhtml.endElement("p");
         }

         // Slide master, if present
         extractMaster(xhtml, slide.getMasterSheet());

         // Slide text
         {
            xhtml.startElement("p", "class", "slide-content");

            textRunsToText(xhtml, slide.getTextRuns());

            xhtml.endElement("p");
         }

         // Table text
         for (Shape shape: slide.getShapes()){
            if (shape instanceof Table){
               extractTableText(xhtml, (Table)shape);
            }
         }

         // Slide footer, if present
         if (hf != null && hf.isFooterVisible() && hf.getFooterText() != null) {
            xhtml.startElement("p", "class", "slide-footer");

            xhtml.characters( hf.getFooterText() );

            xhtml.endElement("p");
         }

         // Comments, if present
         for( Comment comment : slide.getComments() ) {
            xhtml.startElement("p", "class", "slide-comment");
            if (comment.getAuthor() != null) {
               xhtml.startElement("b");
               xhtml.characters( comment.getAuthor() );
               xhtml.endElement("b");
               
               if (comment.getText() != null) {
                  xhtml.characters( " - ");
               }
            }
            if (comment.getText() != null) {
               xhtml.characters( comment.getText() );
            }
            xhtml.endElement("p");
         }

         // Now any embedded resources
         handleSlideEmbeddedResources(slide, xhtml);

         // TODO Find the Notes for this slide and extract inline

         // Slide complete
         xhtml.endElement("div");
      }

      // All slides done
      xhtml.endElement("div");

      /* notes */
      xhtml.startElement("div", "class", "slideNotes");
      HashSet<Integer> seenNotes = new HashSet<Integer>();
      HeadersFooters hf = _show.getNotesHeadersFooters();

      for (Slide slide : _slides) {
         Notes notes = slide.getNotesSheet();
         if (notes == null) {
            continue;
         }
         Integer id = notes._getSheetNumber();
         if (seenNotes.contains(id)) {
            continue;
         }
         seenNotes.add(id);

         // Repeat the Notes header, if set
         if (hf != null && hf.isHeaderVisible() && hf.getHeaderText() != null) {
            xhtml.startElement("p", "class", "slide-note-header");
            xhtml.characters( hf.getHeaderText() );
            xhtml.endElement("p");
         }

         // Notes text
         textRunsToText(xhtml, notes.getTextRuns());

         // Repeat the notes footer, if set
         if (hf != null && hf.isFooterVisible() && hf.getFooterText() != null) {
            xhtml.startElement("p", "class", "slide-note-footer");
            xhtml.characters( hf.getFooterText() );
            xhtml.endElement("p");
         }
      }

      handleSlideEmbeddedPictures(_show, xhtml);

      xhtml.endElement("div");
   }

   private void extractMaster(XHTMLContentHandler xhtml, MasterSheet master) throws SAXException {
      if (master == null){
         return;
      }
      Shape[] shapes = master.getShapes();
      if (shapes == null || shapes.length == 0){
         return;
      }

      xhtml.startElement("div", "class", "slide-master-content");
      for (Shape shape : shapes){
         if (shape != null && ! MasterSheet.isPlaceholder(shape)){
            if (shape instanceof TextShape){
               TextShape tsh = (TextShape)shape;
               String text = tsh.getText();
               if (text != null){
                  xhtml.element("p", text);
               }
            }
         }
      }
      xhtml.endElement("div");
   }

   private void extractTableText(XHTMLContentHandler xhtml, Table shape) throws SAXException {
      xhtml.startElement("table");
      for (int row = 0; row < shape.getNumberOfRows(); row++){
         xhtml.startElement("tr");
         for (int col = 0; col < shape.getNumberOfColumns(); col++){
            TableCell cell = shape.getCell(row, col);
            //insert empty string for empty cell if cell is null
            String txt = "";
            if (cell != null){
               txt = cell.getText();
            }
            xhtml.element("td", txt);
         }
         xhtml.endElement("tr");
      }
      xhtml.endElement("table");   
   }

   private void textRunsToText(XHTMLContentHandler xhtml, TextRun[] runs) throws SAXException {
      if (runs==null) {
         return;
      }

      for (TextRun run : runs) {
         if (run != null) {
           // Leaving in wisdom from TIKA-712 for easy revert.
           // Avoid boiler-plate text on the master slide (0
           // = TextHeaderAtom.TITLE_TYPE, 1 = TextHeaderAtom.BODY_TYPE):
           //if (!isMaster || (run.getRunType() != 0 && run.getRunType() != 1)) {
           String txt = run.getText();
           if (txt != null){
               xhtml.characters(txt);
               xhtml.startElement("br");
               xhtml.endElement("br");
           }
         }
      }
   }

    private void handleSlideEmbeddedPictures(SlideShow slideshow, XHTMLContentHandler xhtml)
            throws TikaException, SAXException, IOException {
        for (PictureData pic : slideshow.getPictureData()) {
            String mediaType = null;

            switch (pic.getType()) {
                case Picture.EMF:
                    mediaType = "application/x-emf";
                    break;
                case Picture.JPEG:
                    mediaType = "image/jpeg";
                    break;
                case Picture.PNG:
                    mediaType = "image/png";
                    break;
                case Picture.WMF:
                    mediaType = "application/x-msmetafile";
                    break;
                case Picture.DIB:
                    mediaType = "image/bmp";
                    break;
            }

            handleEmbeddedResource(
                  TikaInputStream.get(pic.getData()), null, null,
                  mediaType, xhtml, false);
        }
    }

    private void handleSlideEmbeddedResources(Slide slide, XHTMLContentHandler xhtml)
                throws TikaException, SAXException, IOException {
      Shape[] shapes;
      try {
         shapes = slide.getShapes();
      } catch(NullPointerException e) {
         // Sometimes HSLF hits problems
         // Please open POI bugs for any you come across!
         return;
      }
      
      for( Shape shape : shapes ) {
         if( shape instanceof OLEShape ) {
            OLEShape oleShape = (OLEShape)shape;
            ObjectData data = null;
            try {
                data = oleShape.getObjectData();
            } catch( NullPointerException e ) { 
                /* getObjectData throws NPE some times. */
            }
 
            if (data != null) {
               String objID = Integer.toString(oleShape.getObjectID());

               // Embedded Object: add a <div
               // class="embedded" id="X"/> so consumer can see where
               // in the main text each embedded document
               // occurred:
               AttributesImpl attributes = new AttributesImpl();
               attributes.addAttribute("", "class", "class", "CDATA", "embedded");
               attributes.addAttribute("", "id", "id", "CDATA", objID);
               xhtml.startElement("div", attributes);
               xhtml.endElement("div");

               TikaInputStream stream =
                    TikaInputStream.get(data.getData());
               try {
                  String mediaType = null;
                  if ("Excel.Chart.8".equals(oleShape.getProgID())) {
                     mediaType = "application/vnd.ms-excel";
                  }
                  handleEmbeddedResource(
                        stream, objID, objID,
                        mediaType, xhtml, false);
               } finally {
                  stream.close();
               }
            }
         }
      }
   }
}
