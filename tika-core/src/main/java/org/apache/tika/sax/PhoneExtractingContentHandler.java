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

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.CleanPhoneText;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.Arrays;
import java.util.List;

/**
 * Class used to extract phone numbers while parsing.
 *
 * Every time a document is parsed in Tika, the content is split into SAX events.
 * Those SAX events are handled by a ContentHandler. You can think of these events
 * as marking a tag in an HTML file. Once you're finished parsing, you can call
 * handler.toString(), for example, to get the text contents of the file. On the other
 * hand, any of the metadata of the file will be added to the Metadata object passed
 * in during the parse() call.  So, the Parser class sends metadata to the Metadata
 * object and content to the ContentHandler.
 *
 * This class is an example of how to combine a ContentHandler and a Metadata.
 * As content is passed to the handler, we first check to see if it matches a
 * textual pattern for a phone number. If the extracted content is a phone number,
 * we add it to the metadata under the key "phonenumbers". So, if you used this
 * ContentHandler when you parsed a document, then called
 * metadata.getValues("phonenumbers"), you would get an array of Strings of phone
 * numbers found in the document.
 *
 * Please see the PhoneExtractingContentHandlerTest for an example of how to use
 * this class.
 *
 */
public class PhoneExtractingContentHandler extends ContentHandlerDecorator {
    private Metadata metadata;
    private static final String PHONE_NUMBERS = "phonenumbers";
    private StringBuilder stringBuilder;

    /**
     * Creates a decorator for the given SAX event handler and Metadata object.
     *
     * @param handler SAX event handler to be decorated
     */
    public PhoneExtractingContentHandler(ContentHandler handler, Metadata metadata) {
        super(handler);
        this.metadata = metadata;
        this.stringBuilder = new StringBuilder();
    }

    /**
     * Creates a decorator that by default forwards incoming SAX events to
     * a dummy content handler that simply ignores all the events. Subclasses
     * should use the {@link #setContentHandler(ContentHandler)} method to
     * switch to a more usable underlying content handler.
     * Also creates a dummy Metadata object to store phone numbers in.
     */
    protected PhoneExtractingContentHandler() {
        this(new DefaultHandler(), new Metadata());
    }

    /**
     * The characters method is called whenever a Parser wants to pass raw...
     * characters to the ContentHandler. But, sometimes, phone numbers are split
     * accross different calls to characters, depending on the specific Parser
     * used. So, we simply add all characters to a StringBuilder and analyze it
     * once the document is finished.
     */
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        try {
            String text = new String(Arrays.copyOfRange(ch, start, start + length));
            stringBuilder.append(text);
            super.characters(ch, start, length);
        } catch (SAXException e) {
            handleException(e);
        }
    }


    /**
     * This method is called whenever the Parser is done parsing the file. So,
     * we check the output for any phone numbers.
     */
    @Override
    public void endDocument() throws SAXException {
        super.endDocument();
        List<String> numbers = CleanPhoneText.extractPhoneNumbers(stringBuilder.toString());
        for (String number : numbers) {
            metadata.add(PHONE_NUMBERS, number);
        }
    }
}
