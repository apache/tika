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

import java.util.Stack;

import org.apache.tika.metadata.Metadata;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

public class DIFContentHandler extends DefaultHandler {

	private static final char[] NEWLINE = new char[] { '\n' };
	private static final char[] TABSPACE = new char[] { '\t' };
	private static final Attributes EMPTY_ATTRIBUTES = new AttributesImpl();

	private Stack<String> treeStack;
	private Stack<String> dataStack;
	private final ContentHandler delegate;
	private boolean isLeaf;
	private Metadata metadata;

	public DIFContentHandler(ContentHandler delegate, Metadata metadata) {
		this.delegate = delegate;
		this.isLeaf = false;
		this.metadata = metadata;
		this.treeStack = new Stack<String>();
		this.dataStack = new Stack<String>();
	}

	@Override
	public void setDocumentLocator(org.xml.sax.Locator locator) {
		delegate.setDocumentLocator(locator);
	}

	@Override
	public void characters(char[] ch, int start, int length)
			throws SAXException {
		String value = (new String(ch, start, length)).toString();
		this.dataStack.push(value);

		if (this.treeStack.peek().equals("Entry_Title")) {
			this.delegate.characters(NEWLINE, 0, NEWLINE.length);
			this.delegate.characters(TABSPACE, 0, TABSPACE.length);
			this.delegate.startElement("", "h3", "h3", EMPTY_ATTRIBUTES);
			String title = "Title: ";
			title = title + value;
			this.delegate.characters(title.toCharArray(), 0, title.length());
			this.delegate.endElement("", "h3", "h3");
		}
		if (this.treeStack.peek().equals("Southernmost_Latitude")
				|| this.treeStack.peek().equals("Northernmost_Latitude")
				|| this.treeStack.peek().equals("Westernmost_Longitude")
				|| this.treeStack.peek().equals("Easternmost_Longitude")) {
			this.delegate.characters(NEWLINE, 0, NEWLINE.length);
			this.delegate.characters(TABSPACE, 0, TABSPACE.length);
			this.delegate.characters(TABSPACE, 0, TABSPACE.length);
			this.delegate.startElement("", "tr", "tr", EMPTY_ATTRIBUTES);
			this.delegate.startElement("", "td", "td", EMPTY_ATTRIBUTES);
			String key = this.treeStack.peek() + " : ";
			this.delegate.characters(key.toCharArray(), 0, key.length());
			this.delegate.endElement("", "td", "td");
			this.delegate.startElement("", "td", "td", EMPTY_ATTRIBUTES);
			this.delegate.characters(value.toCharArray(), 0, value.length());
			this.delegate.endElement("", "td", "td");
			this.delegate.endElement("", "tr", "tr");
		}
	}

	@Override
	public void ignorableWhitespace(char[] ch, int start, int length)
			throws SAXException {
		delegate.ignorableWhitespace(ch, start, length);
	}

	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) throws SAXException {
		this.isLeaf = true;
		if (localName.equals("Spatial_Coverage")) {
			this.delegate.characters(NEWLINE, 0, NEWLINE.length);
			this.delegate.characters(TABSPACE, 0, TABSPACE.length);
			this.delegate.startElement("", "h3", "h3", EMPTY_ATTRIBUTES);
			String value = "Geographic Data: ";
			this.delegate.characters(value.toCharArray(), 0, value.length());
			this.delegate.endElement("", "h3", "h3");
			this.delegate.characters(NEWLINE, 0, NEWLINE.length);
			this.delegate.characters(TABSPACE, 0, TABSPACE.length);
			this.delegate.startElement("", "table", "table", EMPTY_ATTRIBUTES);
		}
		this.treeStack.push(localName);
	}

	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {
		if (localName.equals("Spatial_Coverage")) {
			this.delegate.characters(NEWLINE, 0, NEWLINE.length);
			this.delegate.characters(TABSPACE, 0, TABSPACE.length);
			this.delegate.endElement("", "table", "table");
		}
		if (this.isLeaf) {
			Stack<String> tempStack = (Stack<String>) this.treeStack.clone();
			String key = "";
			while (!tempStack.isEmpty()) {
				if (key.length() == 0) {
					key = tempStack.pop();
				} else {
					key = tempStack.pop() + "-" + key;
				}
			}
			String value = this.dataStack.peek();
			this.metadata.add(key, value);
			this.isLeaf = false;
		}
		this.treeStack.pop();
		this.dataStack.pop();
	}

	@Override
	public void startDocument() throws SAXException {
		delegate.startDocument();
	}

	@Override
	public void endDocument() throws SAXException {
		delegate.endDocument();
	}

	@Override
	public String toString() {
		return delegate.toString();
	}

}
