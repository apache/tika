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
package org.apache.tika.parser.mbox;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

public class MboxParserTest extends TestCase {

    public void testSimple() {
        Parser parser = new MboxParser();
        Metadata metadata = new Metadata();
        InputStream stream = getStream("test-documents/simple.mbox");
        ContentHandler handler = mock(DefaultHandler.class);

        try {
            parser.parse(stream, handler, metadata, new ParseContext());
            verify(handler).startDocument();
            verify(handler, times(2)).startElement(eq(XHTMLContentHandler.XHTML), eq("p"), eq("p"), any(Attributes.class));
            verify(handler, times(2)).endElement(XHTMLContentHandler.XHTML, "p", "p");
            verify(handler).characters(new String("Test content 1").toCharArray(), 0, 14);
            verify(handler).characters(new String("Test content 2").toCharArray(), 0, 14);
            verify(handler).endDocument();
        } catch (Exception e) {
            fail("Exception thrown: " + e.getMessage());
        }
    }

    public void testHeaders() {
        Parser parser = new MboxParser();
        Metadata metadata = new Metadata();
        InputStream stream = getStream("test-documents/headers.mbox");
        ContentHandler handler = mock(DefaultHandler.class);

        try {
            parser.parse(stream, handler, metadata, new ParseContext());

            verify(handler).startDocument();
            verify(handler).startElement(eq(XHTMLContentHandler.XHTML), eq("p"), eq("p"), any(Attributes.class));
            verify(handler).characters(new String("Test content").toCharArray(), 0, 12);
            verify(handler).endDocument();

            assertEquals("subject", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("subject", metadata.get(Metadata.SUBJECT));
            assertEquals("<author@domain.com>", metadata.get(Metadata.AUTHOR));
            assertEquals("<author@domain.com>", metadata.get(TikaCoreProperties.CREATOR));
            assertEquals(null, metadata.get(Metadata.MESSAGE_RECIPIENT_ADDRESS));
            assertEquals("<name@domain.com>", metadata.get("MboxParser-return-path"));
            assertEquals("Should be ISO date in UTC, converted from 'Tue, 9 Jun 2009 23:58:45 -0400'", 
                    "2009-06-10T03:58:45Z", metadata.get(TikaCoreProperties.CREATED));
        } catch (Exception e) {
            fail("Exception thrown: " + e.getMessage());
        }
    }

    public void testMultilineHeader() {
        Parser parser = new MboxParser();
        Metadata metadata = new Metadata();
        InputStream stream = getStream("test-documents/multiline.mbox");
        ContentHandler handler = mock(DefaultHandler.class);

        try {
            parser.parse(stream, handler, metadata, new ParseContext());

            verify(handler).startDocument();
            verify(handler).startElement(eq(XHTMLContentHandler.XHTML), eq("p"), eq("p"), any(Attributes.class));
            verify(handler).characters(new String("Test content").toCharArray(), 0, 12);
            verify(handler).endDocument();

            assertEquals("from xxx by xxx with xxx; date", metadata.get("MboxParser-received"));
        } catch (Exception e) {
            fail("Exception thrown: " + e.getMessage());
        }
    }

    public void testQuoted() {
        Parser parser = new MboxParser();
        Metadata metadata = new Metadata();
        InputStream stream = getStream("test-documents/quoted.mbox");
        ContentHandler handler = mock(DefaultHandler.class);

        try {
            parser.parse(stream, handler, metadata, new ParseContext());

            verify(handler).startDocument();
            verify(handler).startElement(eq(XHTMLContentHandler.XHTML), eq("p"), eq("p"), any(Attributes.class));
            verify(handler).startElement(eq(XHTMLContentHandler.XHTML), eq("q"), eq("q"), any(Attributes.class));
            verify(handler).endElement(eq(XHTMLContentHandler.XHTML), eq("q"), eq("q"));
            verify(handler).endElement(eq(XHTMLContentHandler.XHTML), eq("p"), eq("p"));
            verify(handler).characters(new String("Test content").toCharArray(), 0, 12);
            verify(handler).characters(new String("> quoted stuff").toCharArray(), 0, 14);
            verify(handler).endDocument();
        } catch (Exception e) {
            fail("Exception thrown: " + e.getMessage());
        }
    }

    public void testComplex() {
        Parser parser = new MboxParser();
        Metadata metadata = new Metadata();
        InputStream stream = getStream("test-documents/complex.mbox");
        ContentHandler handler = mock(DefaultHandler.class);

        try {
            parser.parse(stream, handler, metadata, new ParseContext());

            // TODO: Remove subject and author in Tika 2.0
            assertEquals("Re: question about when shuffle/sort start working", metadata.get(Metadata.SUBJECT));
            assertEquals("Re: question about when shuffle/sort start working", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Jothi Padmanabhan <jothipn@yahoo-inc.com>", metadata.get(Metadata.AUTHOR));
            assertEquals("Jothi Padmanabhan <jothipn@yahoo-inc.com>", metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("core-user@hadoop.apache.org", metadata.get(Metadata.MESSAGE_RECIPIENT_ADDRESS));
            
            verify(handler).startDocument();
            verify(handler, times(3)).startElement(eq(XHTMLContentHandler.XHTML), eq("p"), eq("p"), any(Attributes.class));
            verify(handler, times(3)).endElement(eq(XHTMLContentHandler.XHTML), eq("p"), eq("p"));
            verify(handler, times(3)).startElement(eq(XHTMLContentHandler.XHTML), eq("q"), eq("q"), any(Attributes.class));
            verify(handler, times(3)).endElement(eq(XHTMLContentHandler.XHTML), eq("q"), eq("q"));
            verify(handler).endDocument();
        } catch (Exception e) {
            fail("Exception thrown: " + e.getMessage());
        }
    }

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader()
        .getResourceAsStream(name);
    }


}
