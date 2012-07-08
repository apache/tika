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
package org.apache.tika.parser.iwork;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

class PagesContentHandler extends DefaultHandler {

    private final XHTMLContentHandler xhtml;
    private final Metadata metadata;

    /** The (interesting) part of the document we're in. Should be more structured... */
    private enum DocumentPart {
       METADATA, PARSABLE_TEXT, 
       HEADERS, HEADER_ODD, HEADER_EVEN, HEADER_FIRST,
       FOOTERS, FOOTER_ODD, FOOTER_EVEN, FOOTER_FIRST,
       FOOTNOTES, ANNOTATIONS;
    }
    private DocumentPart inPart = null;
    private boolean ghostText;
    
    private static String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private boolean parseProperty = false;
    private int pageCount = 0;
    private int slPageCount = 0;

    private HeaderFooter headers = null;
    private HeaderFooter footers = null;
    private Footnotes footnotes = null; 
    private Annotations annotations = null; 
    
    private Map<String, List<List<String>>> tableData =
        new HashMap<String, List<List<String>>>();
    private String activeTableId;
    private int numberOfColumns = 0;
    private List<String> activeRow = new ArrayList<String>();

    private String metaDataLocalName;
    private String metaDataQName;

    PagesContentHandler(XHTMLContentHandler xhtml, Metadata metadata) {
        this.xhtml = xhtml;
        this.metadata = metadata;
    }

    @Override
    public void endDocument() throws SAXException {
        metadata.set(Metadata.PAGE_COUNT, String.valueOf(pageCount));
        if (pageCount > 0) {
            doFooter();
            xhtml.endElement("div");
        }
    }

    @Override
    public void startElement(
            String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        if (parseProperty) {
            String value = parsePrimitiveElementValue(qName, attributes);
            if (value != null) {
                Object metaDataKey = resolveMetaDataKey(metaDataLocalName);
                if(metaDataKey instanceof Property) {
                    metadata.set((Property)metaDataKey, value);
                } else {
                    metadata.add((String)metaDataKey, value);
                }
            }
        }

        if ("sl:publication-info".equals(qName)) {
            inPart = DocumentPart.METADATA;
        } else if ("sf:metadata".equals(qName)) {
           inPart = DocumentPart.METADATA;
        } else if ("sf:page-start".equals(qName) || "sl:page-group".equals(qName)) {
            if (pageCount > 0) {
                doFooter();
                xhtml.endElement("div");
            }
            xhtml.startElement("div");
            if ("sl:page-group".equals(qName)) {
                slPageCount++;
            } else {
                pageCount++;
            }
            doHeader();
        } else if ("sf:p".equals(qName)) {
          if (pageCount+slPageCount > 0) {
            inPart = DocumentPart.PARSABLE_TEXT;
            xhtml.startElement("p");
          }
        } else if ("sf:attachment".equals(qName)) {
            String kind = attributes.getValue("sf:kind");
            if ("tabular-attachment".equals(kind)) {
                activeTableId = attributes.getValue("sfa:ID");
                tableData.put(activeTableId, new ArrayList<List<String>>());
            }
        } else if ("sf:attachment-ref".equals(qName)) {
            String idRef = attributes.getValue("sfa:IDREF");
            outputTable(idRef);
        } else if ("sf:headers".equals(qName)) {
            headers = new HeaderFooter(qName);
            inPart = DocumentPart.HEADERS;
        } else if ("sf:footers".equals(qName)) {
           footers = new HeaderFooter(qName);
           inPart = DocumentPart.FOOTERS;
        } else if ("sf:header".equals(qName)) {
            inPart = headers.identifyPart(attributes.getValue("sf:name"));
        } else if ("sf:footer".equals(qName)) {
           inPart = footers.identifyPart(attributes.getValue("sf:name"));
        } else if ("sf:page-number".equals(qName)) {	
        	if (inPart == DocumentPart.FOOTER_ODD
        		|| inPart == DocumentPart.FOOTER_FIRST
        		|| inPart == DocumentPart.FOOTER_EVEN) {
        		// We are in a footer
        		footers.hasAutoPageNumber = true;
        		footers.autoPageNumberFormat = attributes.getValue("sf:format");   
        	} else {
        		headers.hasAutoPageNumber = true;
        		headers.autoPageNumberFormat = attributes.getValue("sf:format");   
        	}

        	xhtml.characters(Integer.toString(this.pageCount));
        } else if ("sf:footnotes".equals(qName)) {
           footnotes = new Footnotes();
           inPart = DocumentPart.FOOTNOTES;
        } else if ("sf:footnote-mark".equals(qName)) {
           footnotes.recordMark(attributes.getValue("sf:mark"));
        } else if ("sf:footnote".equals(qName) && inPart == DocumentPart.PARSABLE_TEXT) {
           // What about non auto-numbered?
           String footnoteMark = attributes.getValue("sf:autonumber");
           if (footnotes != null) {
              String footnoteText = footnotes.footnotes.get(footnoteMark);
              if (footnoteText != null) {
                 xhtml.startElement("div", "style", "footnote");
                 xhtml.characters("Footnote:" ); // As shown in Pages
                 xhtml.characters(footnoteText);
                 xhtml.endElement("div");
              }
           }
        } else if ("sf:annotations".equals(qName)) {
           annotations = new Annotations();
           inPart = DocumentPart.ANNOTATIONS;
        } else if ("sf:annotation".equals(qName) && inPart == DocumentPart.ANNOTATIONS) {
           annotations.start(attributes.getValue("sf:target"));
        } else if ("sf:annotation-field".equals(qName) && inPart == DocumentPart.PARSABLE_TEXT) {
           xhtml.startElement("div", "style", "annotated");
           
           String annotationText = annotations.annotations.get(attributes.getValue("sfa:ID"));
           if (annotationText != null) {
              xhtml.startElement("div", "style", "annotation");
              xhtml.characters(annotationText);
              xhtml.endElement("div");
           }
        } else if ("sf:ghost-text".equals(qName)) {
            ghostText = true;
        }

        if (activeTableId != null) {
            parseTableData(qName, attributes);
        }

        if (inPart == DocumentPart.METADATA) {
            metaDataLocalName = localName;
            metaDataQName = qName;
            parseProperty = true;
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        if (metaDataLocalName != null && metaDataLocalName.equals(localName)) {
            metaDataLocalName = null;
            parseProperty = false;
        }

        if ("sl:publication-info".equals(qName)) {
            inPart = null;
        } else if ("sf:metadata".equals(qName)) {
            inPart = null;
        } else if ("sf:p".equals(qName) && (pageCount+slPageCount) > 0) {
            inPart = null;
            xhtml.endElement("p");
        } else if ("sf:attachment".equals(qName)) {
            activeTableId = null;
        } else if ("sf:annotation".equals(qName) && inPart == DocumentPart.ANNOTATIONS) {
            annotations.end();
        } else if ("sf:annotation-field".equals(qName) && inPart == DocumentPart.PARSABLE_TEXT) {
            xhtml.endElement("div");
        } else if ("sf:ghost-text".equals(qName)) {
            ghostText = false;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (length > 0) {
           if (inPart == DocumentPart.PARSABLE_TEXT) {
               if (!ghostText) {
                   xhtml.characters(ch, start, length);
               }
          } else if(inPart != null) {
              String str = new String(ch, start, length);
              if (inPart == DocumentPart.HEADER_FIRST) headers.defaultFirst = str;
              if (inPart == DocumentPart.HEADER_EVEN)  headers.defaultEven = str;
              if (inPart == DocumentPart.HEADER_ODD)   headers.defaultOdd = str;
              if (inPart == DocumentPart.FOOTER_FIRST) footers.defaultFirst = str;
              if (inPart == DocumentPart.FOOTER_EVEN)  footers.defaultEven = str;
              if (inPart == DocumentPart.FOOTER_ODD)   footers.defaultOdd = str;
              if (inPart == DocumentPart.FOOTNOTES)    footnotes.text(str);
              if (inPart == DocumentPart.ANNOTATIONS)  annotations.text(str);
          }
        }
    }

    private void parseTableData(String qName, Attributes attributes) {
        if ("sf:grid".equals(qName)) {
            String numberOfColumns = attributes.getValue("sf:numcols");
            this.numberOfColumns = Integer.parseInt(numberOfColumns);
        } else if ("sf:ct".equals(qName)) {
            activeRow.add(attributes.getValue("sfa:s"));

            if (activeRow.size() >= 3) {
                tableData.get(activeTableId).add(activeRow);
                activeRow = new ArrayList<String>();
            }
        }
    }

    private void outputTable(String idRef) throws SAXException {
        List<List<String>> tableData = this.tableData.get(idRef);
        if (tableData != null) {
            xhtml.startElement("table");
            for (List<String> row : tableData) {
                xhtml.startElement("tr");
                for (String cell : row) {
                    xhtml.element("td", cell);
                }
                xhtml.endElement("tr");
            }
            xhtml.endElement("table");
        }
    }

    /**
     * Returns a resolved key that is common in other document types or
     * returns the specified metaDataLocalName if no common key could be found.
     * The key could be a simple String key, or could be a {@link Property}
     *
     * @param metaDataLocalName The localname of the element containing metadata
     * @return a resolved key that is common in other document types
     */
    private Object resolveMetaDataKey(String metaDataLocalName) {
        Object metaDataKey = metaDataLocalName;
        if ("sf:authors".equals(metaDataQName)) {
            metaDataKey = TikaCoreProperties.CREATOR;
        } else if ("sf:title".equals(metaDataQName)) {
            metaDataKey = TikaCoreProperties.TITLE;
        } else if ("sl:SLCreationDateProperty".equals(metaDataQName)) {
            metaDataKey = TikaCoreProperties.CREATED;
        } else if ("sl:SLLastModifiedDateProperty".equals(metaDataQName)) {
            metaDataKey = Metadata.LAST_MODIFIED;
        } else if ("sl:language".equals(metaDataQName)) {
            metaDataKey = TikaCoreProperties.LANGUAGE;
        }
        return metaDataKey;
    }

    /**
     * Returns the value of a primitive element e.g.:
     * &lt;sl:number sfa:number="0" sfa:type="f"/&gt; - the number attribute
     * &lt;sl:string sfa:string="en"/&gt; = the string attribute
     * <p>
     * Returns <code>null</code> if the value could not be extracted from
     * the list of attributes.
     *
     * @param qName      The fully qualified name of the element containing
     *                   the value to extract
     * @param attributes The list of attributes of which one contains the
     *                   value to be extracted
     * @return the value of a primitive element
     */
    private String parsePrimitiveElementValue(
            String qName, Attributes attributes) {
        if ("sl:string".equals(qName) || "sf:string".equals(qName)) {
            return attributes.getValue("sfa:string");
        } else if ("sl:number".equals(qName)) {
            return attributes.getValue("sfa:number");
        } else if ("sl:date".equals(qName)) {
            return attributes.getValue("sf:val");
        }

        return null;
    }
    
    private void doHeader() throws SAXException {
       if (headers != null) {
          headers.output("header");
       }
    }
    private void doFooter() throws SAXException {
       if (footers != null) {
          footers.output("footer");
       }
    }

    /**
     * Represents the Headers or Footers in a document
     */
    private class HeaderFooter {
       private String type; // sf:headers or sf:footers
       private String defaultOdd;
       private String defaultEven;
       private String defaultFirst;
       private boolean hasAutoPageNumber;
       private String autoPageNumberFormat;
       // TODO Can there be custom ones?
       
       private HeaderFooter(String type) {
          this.type = type; 
       }
       private DocumentPart identifyPart(String name) {
          if("SFWPDefaultOddHeaderIdentifier".equals(name))
             return DocumentPart.HEADER_ODD;
          if("SFWPDefaultEvenHeaderIdentifier".equals(name))
             return DocumentPart.HEADER_EVEN;
          if("SFWPDefaultFirstHeaderIdentifier".equals(name))
             return DocumentPart.HEADER_FIRST;
          
          if("SFWPDefaultOddFooterIdentifier".equals(name))
             return DocumentPart.FOOTER_ODD;
          if("SFWPDefaultEvenFooterIdentifier".equals(name))
             return DocumentPart.FOOTER_EVEN;
          if("SFWPDefaultFirstFooterIdentifier".equals(name))
             return DocumentPart.FOOTER_FIRST;
          
          return null;
       }
       private void output(String what) throws SAXException {
          String text = null;
          if (pageCount == 1 && defaultFirst != null) {
             text = defaultFirst;
          } else if (pageCount % 2 == 0 && defaultEven != null) {
             text = defaultEven;
          } else {
             text = defaultOdd;
          }
          
          if (text != null) {
             xhtml.startElement("div", "class", "header");
             xhtml.characters(text);
             if (hasAutoPageNumber) {
            	 if (autoPageNumberFormat == null) { // raw number
            		 xhtml.characters("\t" + pageCount);
            	 } else if (autoPageNumberFormat.equals("upper-roman")){
            		 xhtml.characters("\t" + AutoPageNumberUtils.asRomanNumerals(pageCount));
            	 } else if (autoPageNumberFormat.equals("lower-roman")){
            		 xhtml.characters("\t" + AutoPageNumberUtils.asRomanNumeralsLower(pageCount));
            	 } else if (autoPageNumberFormat.equals("upper-alpha")){
            		 xhtml.characters("\t" + AutoPageNumberUtils.asAlphaNumeric(pageCount));
            	 } else if (autoPageNumberFormat.equals("lower-alpha")){
            		 xhtml.characters("\t" + AutoPageNumberUtils.asAlphaNumericLower(pageCount));
            	 }
             }
             xhtml.endElement("div");
          }
       }
    }
    /**
     * Represents Footnotes in a document. The way these work
     *  in the file format isn't very clean...
     */
    private static class Footnotes {
       /** Mark -> Text */
       Map<String,String> footnotes = new HashMap<String, String>();
       String lastSeenMark = null;
       
       /**
        * Normally happens before the text of the mark
        */
       private void recordMark(String mark) {
          lastSeenMark = mark;
       }
       private void text(String text) {
          if (lastSeenMark != null) {
             if (footnotes.containsKey(lastSeenMark)) {
                text = footnotes.get(lastSeenMark) + text;
             }
             footnotes.put(lastSeenMark, text);
          }
       }
    }
    /**
     * Represents Annotations in a document. We currently
     *  just grab all the sf:p text in each one 
     */
    private class Annotations {
       /** ID -> Text */
       Map<String,String> annotations = new HashMap<String, String>();
       String currentID = null;
       StringBuffer currentText = null;
       
       private void start(String id) {
          currentID = id;
          currentText = new StringBuffer();
       }
       private void text(String text) {
          if (text != null && text.length() > 0 && currentText != null) {
             currentText.append(text);
          }
       }
       private void end() {
          if (currentText.length() > 0) {
             annotations.put(currentID, currentText.toString());
             currentID = null;
             currentText = null;
          }
       }
    }

}
