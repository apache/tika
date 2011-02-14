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
package org.apache.tika.parser.mail;

import java.io.IOException;
import java.io.InputStream;

import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.descriptor.BodyDescriptor;
import org.apache.james.mime4j.field.AbstractField;
import org.apache.james.mime4j.field.MailboxListField;
import org.apache.james.mime4j.field.UnstructuredField;
import org.apache.james.mime4j.field.address.MailboxList;
import org.apache.james.mime4j.parser.ContentHandler;
import org.apache.james.mime4j.parser.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

/**
 * Bridge between mime4j's content handler and the generic Sax content handler
 * used by Tika. See
 * http://james.apache.org/mime4j/apidocs/org/apache/james/mime4j/parser/ContentHandler.html
 */
class MailContentHandler implements ContentHandler {

    private XHTMLContentHandler handler;
    private Metadata metadata;

    private boolean inPart = false;

    MailContentHandler(XHTMLContentHandler xhtml, Metadata metadata) {
        this.handler = xhtml;
        this.metadata = metadata;
    }

    public void body(BodyDescriptor body, InputStream is) throws MimeException,
            IOException {
        // call the underlying parser for the part
        // TODO how to retrieve a non-default config?
        AutoDetectParser parser = new AutoDetectParser();
        // use a different metadata object
        // in order to specify the mime type of the
        // sub part without damaging the main metadata

        Metadata submd = new Metadata();
        submd.set(Metadata.CONTENT_TYPE, body.getMimeType());
        submd.set(Metadata.CONTENT_ENCODING, body.getCharset());

        try {
            BodyContentHandler bch = new BodyContentHandler(handler);
            parser.parse(is, new EmbeddedContentHandler(bch), submd);
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (TikaException e) {
            e.printStackTrace();
        }
    }

    public void endBodyPart() throws MimeException {
        try {
            handler.endElement("p");
            handler.endElement("div");
        } catch (SAXException e) {
            e.printStackTrace();
        }
    }

    public void endHeader() throws MimeException {
    }

    public void startMessage() throws MimeException {
        try {
            handler.startDocument();
        } catch (SAXException e) {
            e.printStackTrace();
        }
    }

    public void endMessage() throws MimeException {
        try {
            handler.endDocument();
        } catch (SAXException e) {
            e.printStackTrace();
        }
    }

    public void endMultipart() throws MimeException {
        inPart = false;
    }

    public void epilogue(InputStream is) throws MimeException, IOException {
    }

    /**
     * Header for the whole message or its parts
     * 
     * @see http 
     *      ://james.apache.org/mime4j/apidocs/org/apache/james/mime4j/parser/
     *      Field.html
     **/
    public void field(Field field) throws MimeException {
        // inPart indicates whether these metadata correspond to the
        // whole message or its parts
        if (inPart)
            return;
        // TODO add metadata to the parts later
        String fieldname = field.getName();
        if (fieldname.equalsIgnoreCase("From")) {
        	MailboxListField fromField = (MailboxListField) AbstractField.parse(field.getRaw());
        	MailboxList mailboxList = fromField.getMailboxList();
        	for (int i = 0; i < mailboxList.size(); ++i) {
                metadata.add(Metadata.AUTHOR, mailboxList.get(i).getDisplayString());        		
        	}
        } else if (fieldname.equalsIgnoreCase("Subject")) {
        	UnstructuredField subjectField = (UnstructuredField) AbstractField.parse(field.getRaw());
            metadata.add(Metadata.SUBJECT, subjectField.getValue());
        }
    }

    public void preamble(InputStream is) throws MimeException, IOException {
    }

    public void raw(InputStream is) throws MimeException, IOException {
    }

    public void startBodyPart() throws MimeException {
        try {
            handler.startElement("div", "class", "email-entry");
            handler.startElement("p");
        } catch (SAXException e) {
            e.printStackTrace();
        }
    }

    public void startHeader() throws MimeException {
        // TODO Auto-generated method stub

    }

    public void startMultipart(BodyDescriptor descr) throws MimeException {
        inPart = true;
    }

}