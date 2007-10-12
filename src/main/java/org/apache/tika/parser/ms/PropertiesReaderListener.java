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
package org.apache.tika.parser.ms;

import org.apache.poi.hpsf.PropertySetFactory;
import org.apache.poi.hpsf.SummaryInformation;
import org.apache.poi.poifs.eventfilesystem.POIFSReaderEvent;
import org.apache.poi.poifs.eventfilesystem.POIFSReaderListener;
import org.apache.tika.metadata.Metadata;

class PropertiesReaderListener implements POIFSReaderListener {

    private final Metadata metadata;

    public PropertiesReaderListener(Metadata metadata) {
        this.metadata = metadata;
    }

    public void processPOIFSReaderEvent(POIFSReaderEvent event) {
        if (!event.getName().startsWith(
                SummaryInformation.DEFAULT_STREAM_NAME)) {
            return;
        }

        try {
            SummaryInformation si = (SummaryInformation)
                PropertySetFactory.create(event.getStream());
            if (si.getTitle() != null) {
                metadata.set(Metadata.TITLE, si.getTitle());
            }
            if (si.getAuthor() != null) {
                metadata.set(Metadata.AUTHOR, si.getAuthor());
            }
            if (si.getKeywords() != null) {
                metadata.set(Metadata.KEYWORDS, si.getKeywords());
            }
            if (si.getSubject() != null) {
                metadata.set(Metadata.SUBJECT, si.getSubject());
            }
            if (si.getLastAuthor() != null) {
                metadata.set(Metadata.LAST_AUTHOR, si.getLastAuthor());
            }
            if (si.getComments() != null) {
                metadata.set(Metadata.COMMENTS, si.getComments());
            }
            if (si.getTemplate() != null) {
                metadata.set(Metadata.TEMPLATE, si.getTemplate());
            }
            if (si.getApplicationName() != null) {
                metadata.set(Metadata.APPLICATION_NAME, si.getApplicationName());
            }
            if (si.getRevNumber() != null) {
                metadata.set(Metadata.REVISION_NUMBER, si.getRevNumber());
            }
            if (si.getCreateDateTime() != null) {
                metadata.set("creationdate", si.getCreateDateTime().toString());
            }
            if (si.getCharCount() > 0) {
                metadata.set(
                        Metadata.CHARACTER_COUNT,
                        Integer.toString(si.getCharCount()));
            }
            if (si.getEditTime() > 0) {
                metadata.set("edittime", Long.toString(si.getEditTime()));
            }
            if (si.getLastSaveDateTime() != null) {
                metadata.set(
                        Metadata.LAST_SAVED,
                        si.getLastSaveDateTime().toString());
            }
            if (si.getPageCount() > 0) {
                metadata.set(
                        Metadata.PAGE_COUNT,
                        Integer.toString(si.getPageCount()));
            }
            if (si.getSecurity() > 0) {
                metadata.set(
                        "security", Integer.toString(si.getSecurity()));
            }
            if (si.getWordCount() > 0) {
                metadata.set(
                        Metadata.WORD_COUNT,
                        Integer.toString(si.getWordCount()));
            }
            if (si.getLastPrinted() != null) {
                metadata.set(
                        Metadata.LAST_PRINTED,
                        si.getLastPrinted().toString());
            }
        } catch (Exception ex) {
        }
    }
}