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

import org.apache.log4j.Logger;
import org.apache.poi.hpsf.PropertySetFactory;
import org.apache.poi.hpsf.SummaryInformation;
import org.apache.poi.poifs.eventfilesystem.POIFSReader;
import org.apache.poi.poifs.eventfilesystem.POIFSReaderEvent;
import org.apache.poi.poifs.eventfilesystem.POIFSReaderListener;
import org.apache.tika.config.Content;

/**
 * Defines a Microsoft document content extractor.
 * 
 * 
 */
public abstract class MSExtractor {

	static Logger LOG = Logger.getRootLogger();

	private String text = null;

	private POIFSReader reader = null;

	private Iterable<Content> contents;

	private final int MEMORY_THRESHOLD = 1024 * 1024;

	/** Constructs a new Microsoft document extractor. */
	public MSExtractor() {
	}

	public void setContents(Iterable<Content> contents) {
		this.contents = contents;
	}

	/**
	 * Extracts properties and text from an MS Document input stream
	 */
	public void extract(InputStream input) throws Exception {
		RereadableInputStream ris = new RereadableInputStream(input,
				MEMORY_THRESHOLD);
		try {
			// First, extract properties
			this.reader = new POIFSReader();

			this.reader.registerListener(new PropertiesReaderListener(),
					SummaryInformation.DEFAULT_STREAM_NAME);

			if (input.available() > 0) {
				reader.read(ris);
			}
			while (ris.read() != -1) {
			}
			ris.rewind();
			// Extract document full text
			this.text = extractText(ris);
		} finally {
			ris.close();
		}
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
	public String getText() {
		return this.text;
	}

	private class PropertiesReaderListener implements POIFSReaderListener {

		public void processPOIFSReaderEvent(POIFSReaderEvent event) {
			if (!event.getName().startsWith(
					SummaryInformation.DEFAULT_STREAM_NAME)) {
				return;
			}

			try {
				SummaryInformation si = (SummaryInformation) PropertySetFactory
						.create(event.getStream());
				for (Content content : contents) {
					if (content.getTextSelect().equalsIgnoreCase("title")) {
						if (si.getTitle() != null)
							content.setValue(si.getTitle());
					} else if (content.getTextSelect().equalsIgnoreCase(
							"author")) {
						if (si.getAuthor() != null)
							content.setValue(si.getAuthor());
					} else if (content.getTextSelect().equalsIgnoreCase(
							"keywords")) {
						if (si.getKeywords() != null)
							content.setValue(si.getKeywords());
					} else if (content.getTextSelect().equalsIgnoreCase(
							"subject")) {
						if (si.getSubject() != null)
							content.setValue(si.getSubject());
					} else if (content.getTextSelect().equalsIgnoreCase(
							"lastauthor")) {
						if (si.getLastAuthor() != null)
							content.setValue(si.getLastAuthor());
					} else if (content.getTextSelect().equalsIgnoreCase(
							"comments")) {
						if (si.getComments() != null)
							content.setValue(si.getComments());
					} else if (content.getTextSelect().equalsIgnoreCase(
							"template")) {
						if (si.getTemplate() != null)
							content.setValue(si.getTemplate());
					} else if (content.getTextSelect().equalsIgnoreCase(
							"applicationname")) {
						if (si.getApplicationName() != null)
							content.setValue(si.getApplicationName());
					} else if (content.getTextSelect().equalsIgnoreCase(
							"revnumber")) {
						if (si.getRevNumber() != null)
							content.setValue(si.getRevNumber());
					} else if (content.getTextSelect().equalsIgnoreCase(
							"creationdate")) {
						if (si.getCreateDateTime() != null)
							content.setValue(si.getCreateDateTime().toString());
					} else if (content.getTextSelect().equalsIgnoreCase(
							"charcount")) {
						if (si.getCharCount() > 0)
							content.setValue("" + si.getCharCount());
					} else if (content.getTextSelect().equals("edittime")) {
						if (si.getEditTime() > 0)
							content.setValue("" + si.getEditTime());
					} else if (content.getTextSelect().equals(
							"lastsavedatetime")) {
						if (si.getLastSaveDateTime() != null)
							content.setValue(si.getLastSaveDateTime()
									.toString());
					} else if (content.getTextSelect().equals("pagecount")) {
						if (si.getPageCount() > 0)
							content.setValue("" + si.getPageCount());
					} else if (content.getTextSelect().equals("security")) {
						if (si.getSecurity() > 0)
							content.setValue("" + si.getSecurity());
					} else if (content.getTextSelect().equals("wordcount")) {
						if (si.getWordCount() > 0)
							content.setValue("" + si.getWordCount());
					} else if (content.getTextSelect().equals("lastprinted")) {
						if (si.getLastPrinted() != null)
							content.setValue(si.getLastPrinted().toString());
					}

				}

			} catch (Exception ex) {
			}

		}

	}

}