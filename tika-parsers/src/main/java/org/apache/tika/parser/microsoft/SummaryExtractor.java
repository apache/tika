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
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.apache.poi.hpsf.CustomProperties;
import org.apache.poi.hpsf.DocumentSummaryInformation;
import org.apache.poi.hpsf.MarkUnsupportedException;
import org.apache.poi.hpsf.NoPropertySetStreamException;
import org.apache.poi.hpsf.PropertySet;
import org.apache.poi.hpsf.SummaryInformation;
import org.apache.poi.hpsf.UnexpectedPropertySetTypeException;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extractor for Common OLE2 (HPSF) metadata
 */
public class SummaryExtractor {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractPOIFSExtractor.class);

    private static final String SUMMARY_INFORMATION =
            SummaryInformation.DEFAULT_STREAM_NAME;

    private static final String DOCUMENT_SUMMARY_INFORMATION =
            DocumentSummaryInformation.DEFAULT_STREAM_NAME;

    private final Metadata metadata;

    public SummaryExtractor(Metadata metadata) {
        this.metadata = metadata;
    }

    public void parseSummaries(POIFSFileSystem filesystem)
            throws IOException, TikaException {
        parseSummaries(filesystem.getRoot());
    }

    public void parseSummaries(DirectoryNode root)
            throws IOException, TikaException {
        parseSummaryEntryIfExists(root, SUMMARY_INFORMATION);
        parseSummaryEntryIfExists(root, DOCUMENT_SUMMARY_INFORMATION);
    }

    private void parseSummaryEntryIfExists(
            DirectoryNode root, String entryName)
            throws IOException, TikaException {
        try {
            if (! root.hasEntry(entryName)) {
                return;
            }
            DocumentEntry entry =
                    (DocumentEntry) root.getEntry(entryName);
            PropertySet properties =
                    new PropertySet(new DocumentInputStream(entry));
            if (properties.isSummaryInformation()) {
                parse(new SummaryInformation(properties));
            }
            if (properties.isDocumentSummaryInformation()) {
                parse(new DocumentSummaryInformation(properties));
            }
        } catch (FileNotFoundException e) {
            // entry does not exist, just skip it
        } catch (NoPropertySetStreamException e) {
            // no property stream, just skip it
        } catch (UnexpectedPropertySetTypeException e) {
            throw new TikaException("Unexpected HPSF document", e);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("Ignoring unexpected exception while parsing summary entry {}", entryName, e);
        }
    }

    private void parse(SummaryInformation summary) {
        set(TikaCoreProperties.TITLE, summary.getTitle());
        addMulti(metadata, TikaCoreProperties.CREATOR, summary.getAuthor());
        //make sure these are retrievable specifically
        add(Office.KEYWORDS, summary.getKeywords());
        add(OfficeOpenXMLCore.SUBJECT, summary.getSubject());

        set(TikaCoreProperties.MODIFIER, summary.getLastAuthor());
        set(TikaCoreProperties.COMMENTS, summary.getComments());
        set(OfficeOpenXMLExtended.TEMPLATE, summary.getTemplate());
        set(OfficeOpenXMLExtended.APPLICATION, summary.getApplicationName());
        set(OfficeOpenXMLCore.REVISION, summary.getRevNumber());
        set(TikaCoreProperties.CREATED, summary.getCreateDateTime());
        set(TikaCoreProperties.MODIFIED, summary.getLastSaveDateTime());
        set(TikaCoreProperties.PRINT_DATE, summary.getLastPrinted());
        set(OfficeOpenXMLExtended.TOTAL_TIME, Long.toString(summary.getEditTime()));
        set(OfficeOpenXMLExtended.DOC_SECURITY, summary.getSecurity());

        // New style counts
        set(Office.WORD_COUNT, summary.getWordCount());
        set(Office.CHARACTER_COUNT, summary.getCharCount());
        set(Office.PAGE_COUNT, summary.getPageCount());
        if (summary.getPageCount() > 0) {
            metadata.set(PagedText.N_PAGES, summary.getPageCount());
        }
    }

    private void parse(DocumentSummaryInformation summary) {
        set(OfficeOpenXMLExtended.COMPANY, summary.getCompany());
        addMulti(metadata, OfficeOpenXMLExtended.MANAGER, summary.getManager());
        set(TikaCoreProperties.LANGUAGE, getLanguage(summary));
        set(OfficeOpenXMLCore.CATEGORY, summary.getCategory());

        // New style counts
        set(Office.SLIDE_COUNT, summary.getSlideCount());
        if (summary.getSlideCount() > 0) {
            metadata.set(PagedText.N_PAGES, summary.getSlideCount());
        }

        parse(summary.getCustomProperties());
    }

    private String getLanguage(DocumentSummaryInformation summary) {
        CustomProperties customProperties = summary.getCustomProperties();
        if (customProperties != null) {
            Object value = customProperties.get("Language");
            if (value instanceof String) {
                return (String) value;
            }
        }
        return null;
    }

    /**
     * Attempt to parse custom document properties and add to the collection of metadata
     *
     * @param customProperties
     */
    private void parse(CustomProperties customProperties) {
        if (customProperties != null) {
            for (String name : customProperties.nameSet()) {
                // Apply the custom prefix
                String key = Office.USER_DEFINED_METADATA_NAME_PREFIX + name;

                // Get, convert and save property value
                Object value = customProperties.get(name);
                if (value instanceof String) {
                    set(key, (String) value);
                } else if (value instanceof Date) {
                    Property prop = Property.externalDate(key);
                    metadata.set(prop, (Date) value);
                } else if (value instanceof Boolean) {
                    Property prop = Property.externalBoolean(key);
                    metadata.set(prop, value.toString());
                } else if (value instanceof Long) {
                    Property prop = Property.externalInteger(key);
                    metadata.set(prop, ((Long) value).intValue());
                } else if (value instanceof Double) {
                    Property prop = Property.externalReal(key);
                    metadata.set(prop, (Double) value);
                } else if (value instanceof Integer) {
                    Property prop = Property.externalInteger(key);
                    metadata.set(prop, ((Integer) value).intValue());
                }
            }
        }
    }

    private void set(String name, String value) {
        if (value != null) {
            metadata.set(name, value);
        }
    }

    private void set(Property property, String value) {
        if (value != null) {
            metadata.set(property, value);
        }
    }

    private void add(Property property, String value) {
        if (value != null) {
            metadata.add(property, value);
        }
    }

    private void set(Property property, Date value) {
        if (value != null) {
            metadata.set(property, value);
        }
    }

    private void set(Property property, int value) {
        if (value > 0) {
            metadata.set(property, value);
        }
    }

    private void set(String name, long value) {
        if (value > 0) {
            metadata.set(name, Long.toString(value));
        }
    }

    //MS stores values that should be multiple values (e.g. dc:creator)
    //as a semicolon-delimited list.  We need to split
    //on semicolon to add each value.
    public static void addMulti(Metadata metadata, Property property, String string) {
        if (string == null) {
            return;
        }
        String[] parts = string.split(";");
        String[] current = metadata.getValues(property);
        Set<String> seen = new HashSet<>();
        if (current != null) {
            for (String val : current) {
                seen.add(val);
            }
        }
        for (String part : parts) {
            if (! seen.contains(part)) {
                metadata.add(property, part);
                seen.add(part);
            }
        }
    }

}
