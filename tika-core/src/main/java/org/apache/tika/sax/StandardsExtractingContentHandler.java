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

package org.apache.tika.sax;

import java.util.Arrays;
import java.util.List;

import org.apache.tika.metadata.Metadata;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * StandardsExtractingContentHandler is a Content Handler used to extract
 * standard references while parsing.
 *
 */
public class StandardsExtractingContentHandler extends EndDocumentHandler {
	public static final String STANDARD_REFERENCES = "standard_references";
	private double threshold = 0;

	/**
	 * Creates a decorator for the given SAX event handler and Metadata object.
	 * 
	 * @param handler
	 *            SAX event handler to be decorated.
	 * @param metadata
	 *            {@link Metadata} object.
	 */
	public StandardsExtractingContentHandler(ContentHandler handler, Metadata metadata) {
		super(handler, metadata);
	}

	/**
	 * Creates a decorator that by default forwards incoming SAX events to a
	 * dummy content handler that simply ignores all the events. Subclasses
	 * should use the {@link #setContentHandler(ContentHandler)} method to
	 * switch to a more usable underlying content handler. Also creates a dummy
	 * Metadata object to store phone numbers in.
	 */
	protected StandardsExtractingContentHandler() {
		this(new DefaultHandler(), new Metadata());
	}

	/**
	 * Gets the threshold to be used for selecting the standard references found
	 * within the text based on their score.
	 * 
	 * @return the threshold to be used for selecting the standard references
	 *         found within the text based on their score.
	 */
	public double getThreshold() {
		return threshold;
	}

	/**
	 * Sets the score to be used as threshold.
	 * 
	 * @param score
	 *            the score to be used as threshold.
	 */
	public void setThreshold(double score) {
		this.threshold = score;
	}


	/**
	 * This method is called whenever the Parser is done parsing the file. So,
	 * we check the output for any standard references.
	 */
	@Override
	protected void _endDocument() throws SAXException {
		List<StandardReference> standards = StandardsText.extractStandardReferences(stringBuilder.toString(),
				threshold);
		for (StandardReference standardReference : standards) {
			metadata.add(STANDARD_REFERENCES, standardReference.toString());
		}
	}
}