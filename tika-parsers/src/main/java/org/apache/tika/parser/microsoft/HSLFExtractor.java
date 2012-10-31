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

import org.apache.poi.hslf.HSLFSlideShow;
import org.apache.poi.hslf.model.*;
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

import java.io.IOException;
import java.util.HashSet;

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
         // TODO: re-enable this once we fix TIKA-712
         /*
         MasterSheet master = slide.getMasterSheet();
         if(master != null) {
            xhtml.startElement("p", "class", "slide-master-content");
            textRunsToText(xhtml, master.getTextRuns() );
            xhtml.endElement("p");
         }
         */

         // Slide text
         {
            xhtml.startElement("p", "class", "slide-content");

            textRunsToText(xhtml, slide.getTextRuns() );

            xhtml.endElement("p");
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
         Integer id = Integer.valueOf(notes._getSheetNumber());
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

   private void textRunsToText(XHTMLContentHandler xhtml, TextRun[] runs) throws SAXException {
      if (runs==null) {
         return;
      }

      for (TextRun run : runs) {
         if (run != null) {
            xhtml.characters( run.getText() );
            xhtml.startElement("br");
            xhtml.endElement("br");
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
            
            try {
               ObjectData data = oleShape.getObjectData();

               if(data != null) {
                  TikaInputStream stream =
                     TikaInputStream.get(data.getData());
                  try {
                     String mediaType = null;
                     if ("Excel.Chart.8".equals(oleShape.getProgID())) {
                        mediaType = "application/vnd.ms-excel";
                     }
                     handleEmbeddedResource(
                           stream, Integer.toString(oleShape.getObjectID()), null,
                           mediaType, xhtml, false);
                  } finally {
                     stream.close();
                  }
               }
            } catch( NullPointerException e ) { 
               /* getObjectData throws NPE some times. */
            }
         }
      }
   }
}
