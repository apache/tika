/**
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
package org.apache.tika.utils;

// JDK imports
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.apache.tika.config.Content;
// Jakarta POI imports
import org.apache.log4j.Logger;
import org.apache.poi.hpsf.PropertySetFactory;
import org.apache.poi.hpsf.SummaryInformation;
import org.apache.poi.poifs.eventfilesystem.POIFSReader;
import org.apache.poi.poifs.eventfilesystem.POIFSReaderEvent;
import org.apache.poi.poifs.eventfilesystem.POIFSReaderListener;

/**
 * Defines a Microsoft document content extractor.
 * 
 * @author J&eacute;r&ocirc;me Charron
 */
public abstract class MSExtractor {

    static Logger LOG = Logger.getRootLogger();

    private String text = null;

    private POIFSReader reader = null;
    
    private List<Content> contents;

    /** Constructs a new Microsoft document extractor. */
    public MSExtractor() {        
    }
    
    public void setContents(List<Content> contents){
        this.contents = contents;
    }

    /**
     * Extracts properties and text from an MS Document input stream
     */
    public void extract(InputStream input) throws Exception {
        // First, extract properties
        this.reader = new POIFSReader();
        
        this.reader.registerListener(new PropertiesReaderListener(contents),
                SummaryInformation.DEFAULT_STREAM_NAME);
        //input.reset();
        if (input.available() > 0) {
            reader.read(input);
        }
        //input.reset();
        this.text = extractText(input);
    }

    /**
     * Extracts the text content from a Microsoft document input stream.
     */
    public abstract String extractText(InputStream input) throws Exception;

    /**
     * Get the content text of the Microsoft document.
     * 
     * @return the content text of the document
     */
    protected String getText() {
        return this.text;
    }

    private class PropertiesReaderListener implements POIFSReaderListener {
        private List<Content> contents;

        PropertiesReaderListener(List<Content> contents) {
            this.contents = contents;
        }

        public void processPOIFSReaderEvent(POIFSReaderEvent event) {
            if (!event.getName().startsWith(
                    SummaryInformation.DEFAULT_STREAM_NAME)) {
                return;
            }

            try {
                SummaryInformation si = (SummaryInformation) PropertySetFactory
                        .create(event.getStream());

                for (int i = 0; i < contents.size(); i++) {
                    Content content = contents.get(i);
                    if (content.getTextSelect().equalsIgnoreCase("title")) {
                        content.setValue(si.getTitle());
                    }
                    if (content.getTextSelect().equalsIgnoreCase("author")) {
                        content.setValue(si.getAuthor());
                    }
                    else if (content.getTextSelect().equalsIgnoreCase("keywords")) {
                        content.setValue(si.getKeywords());
                    }
                    else if (content.getTextSelect().equalsIgnoreCase("subject")) {
                        content.setValue(si.getSubject());    
                    }
                    else if (content.getTextSelect().equalsIgnoreCase("lastauthor")) {
                        content.setValue(si.getLastAuthor());    
                    }
                    else if (content.getTextSelect().equalsIgnoreCase("comments")) {
                        content.setValue(si.getComments());    
                    }
                    else if (content.getTextSelect().equalsIgnoreCase("template")) {
                        content.setValue(si.getTemplate());    
                    }
                    else if (content.getTextSelect().equalsIgnoreCase("applicationname")) {
                        content.setValue(si.getApplicationName());
                    }
                    else if (content.getTextSelect().equalsIgnoreCase("revnumber")) {
                        content.setValue(si.getRevNumber());
                    }
                    else if (content.getTextSelect().equalsIgnoreCase("creationdate")) {
                        content.setValue(si.getCreateDateTime().toString());
                    }
                    else if (content.getTextSelect().equalsIgnoreCase("")) {
                        //content.setValue(si.getCharCount());
                    }
                    else if (content.getTextSelect().equals("")) {

                    }
                    else if (content.getTextSelect().equals("")) {

                    }
                    else if (content.getTextSelect().equals("")) {

                    }
                    else if (content.getTextSelect().equals("")) {

                    }
                    else if (content.getTextSelect().equals("")) {

                    }
                    else if (content.getTextSelect().equals("")) {

                    }
                    System.out.println(content.getName()+" :"+content.getValue());
                }

            } catch (Exception ex) {
            }

        }

    }

}

/*
 * setProperty(DublinCore.TITLE, si.getTitle());
 * setProperty(Office.APPLICATION_NAME, si.getApplicationName());
 * setProperty(Office.AUTHOR, si.getAuthor());
 * setProperty(Office.CHARACTER_COUNT, si.getCharCount());
 * setProperty(Office.COMMENTS, si.getComments()); setProperty(DublinCore.DATE,
 * si.getCreateDateTime()); // setProperty(Office.EDIT_TIME, si.getEditTime());
 * setProperty(HttpHeaders.LAST_MODIFIED, si.getLastSaveDateTime());
 * setProperty(Office.KEYWORDS, si.getKeywords());
 * setProperty(Office.LAST_AUTHOR, si.getLastAuthor());
 * setProperty(Office.LAST_PRINTED, si.getLastPrinted());
 * setProperty(Office.LAST_SAVED, si.getLastSaveDateTime());
 * setProperty(Office.PAGE_COUNT, si.getPageCount());
 * setProperty(Office.REVISION_NUMBER, si.getRevNumber());
 * setProperty(DublinCore.RIGHTS, si.getSecurity());
 * setProperty(DublinCore.SUBJECT, si.getSubject());
 * setProperty(Office.TEMPLATE, si.getTemplate());
 * setProperty(Office.WORD_COUNT, si.getWordCount());
 */
