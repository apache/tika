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
package org.apache.tika.parser.font;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.fontbox.afm.AFMParser;
import org.apache.fontbox.afm.FontMetric;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Parser for AFM Font Files
 */
public class AdobeFontMetricParser extends AbstractParser { 
    /** Serial version UID */
    private static final long serialVersionUID = -4820306522217196835L;

    private static final MediaType AFM_TYPE =
         MediaType.application( "x-font-adobe-metric" );

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(AFM_TYPE);

    // TIKA-1325 Replace these with properties, from a well known standard
    static final String MET_AVG_CHAR_WIDTH = "AvgCharacterWidth";
    static final String MET_DOC_VERSION = "DocVersion";
    static final String MET_PS_NAME = "PSName";
    static final String MET_FONT_NAME = "FontName";
    static final String MET_FONT_FULL_NAME = "FontFullName";
    static final String MET_FONT_FAMILY_NAME = "FontFamilyName";
    static final String MET_FONT_SUB_FAMILY_NAME = "FontSubFamilyName";
    static final String MET_FONT_VERSION = "FontVersion";
    static final String MET_FONT_WEIGHT = "FontWeight";
    static final String MET_FONT_NOTICE = "FontNotice";
    static final String MET_FONT_UNDERLINE_THICKNESS = "FontUnderlineThickness";
    
    public Set<MediaType> getSupportedTypes( ParseContext context ) { 
       return SUPPORTED_TYPES;
    }

    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context)
                      throws IOException, SAXException, TikaException { 
       FontMetric fontMetrics;
       AFMParser  parser      = new AFMParser( stream );

       // Have FontBox process the file
       parser.parse();
       fontMetrics = parser.getResult();

       // Get the comments in the file to display in xhtml
       List<String> comments = fontMetrics.getComments();

       // Get the creation date
       extractCreationDate( metadata, comments );

       metadata.set( Metadata.CONTENT_TYPE, AFM_TYPE.toString() );
       metadata.set( TikaCoreProperties.TITLE, fontMetrics.getFullName() );

       // Add metadata associated with the font type
       addMetadataByString( metadata, MET_AVG_CHAR_WIDTH, Float.toString( fontMetrics.getAverageCharacterWidth() ) );
       addMetadataByString( metadata, MET_DOC_VERSION, Float.toString( fontMetrics.getAFMVersion() ) );
       addMetadataByString( metadata, MET_FONT_NAME, fontMetrics.getFontName() );
       addMetadataByString( metadata, MET_FONT_FULL_NAME, fontMetrics.getFullName() );
       addMetadataByString( metadata, MET_FONT_FAMILY_NAME, fontMetrics.getFamilyName() );
       addMetadataByString( metadata, MET_FONT_VERSION, fontMetrics.getFontVersion() );
       addMetadataByString( metadata, MET_FONT_WEIGHT, fontMetrics.getWeight() );
       addMetadataByString( metadata, MET_FONT_NOTICE, fontMetrics.getNotice() );
       addMetadataByString( metadata, MET_FONT_UNDERLINE_THICKNESS, Float.toString( fontMetrics.getUnderlineThickness() ) );

       // Output the remaining comments as text
       XHTMLContentHandler xhtml = new XHTMLContentHandler( handler, metadata );
       xhtml.startDocument();

       // Display the comments
       if (comments.size() > 0) {
          xhtml.element( "h1", "Comments" );
          xhtml.startElement("div", "class", "comments");
          for (String comment : comments) {
              xhtml.element( "p", comment );
          }
          xhtml.endElement("div");
       }

       xhtml.endDocument();
    }

    private void addMetadataByString( Metadata metadata, String name, String value ) { 
       // Add metadata if an appropriate value is passed 
       if (value != null) { 
          metadata.add( name, value );
       }
    }

    private void addMetadataByProperty( Metadata metadata, Property property, String value ) { 
       // Add metadata if an appropriate value is passed 
       if (value != null) 
       {
          metadata.set( property, value );
       }
    }


    private void extractCreationDate( Metadata metadata, List<String> comments ) {
       String   date = null;

       for (String value : comments) {
          // Look for the creation date
          if( value.matches( ".*Creation\\sDate.*" ) ) {
             date = value.substring( value.indexOf( ":" ) + 2 );
             comments.remove( value );

             break;
          }
       }

       // If appropriate date then store as metadata
       if( date != null ) {
          addMetadataByProperty( metadata, Metadata.CREATION_DATE, date );
       }
    }
}
