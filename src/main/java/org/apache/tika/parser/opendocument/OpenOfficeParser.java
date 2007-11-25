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

package org.apache.tika.parser.opendocument;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.xml.XMLParser;
import org.apache.tika.sax.AppendableAdaptor;
import org.apache.tika.sax.XHTMLContentHandler;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * OpenOffice parser
 */
public class OpenOfficeParser implements Parser {
    static Logger logger = Logger.getRootLogger();

    private final Namespace NS_DC = Namespace.getNamespace("dc",
            "http://purl.org/dc/elements/1.1/");

    public org.jdom.Document parse(InputStream is) {
        Document xmlDoc = new org.jdom.Document();
        org.jdom.Document xmlMeta = new org.jdom.Document();
        try {
            List files = unzip(is);
            SAXBuilder builder = new SAXBuilder();
            builder.setEntityResolver(new OpenOfficeEntityResolver());
            builder.setValidation(false);

            xmlDoc = builder.build((InputStream) files.get(0));
            xmlMeta = builder.build((InputStream) files.get(1));
            Element rootMeta = xmlMeta.getRootElement();
            Element meta = null;
            List ls = rootMeta.getChildren();
            if (! ls.isEmpty()) {
                meta = (Element) ls.get(0);
            }
            xmlDoc.getRootElement().addContent(meta.detach());
            xmlDoc.getRootElement().addNamespaceDeclaration(NS_DC);
        } catch (JDOMException e) {
            logger.error(e.getMessage());
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        return xmlDoc;
    }

    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        Document xmlDoc = parse(stream);
        XMLParser xp = new XMLParser();
        xp.getAllDocumentNs(xmlDoc);
        xp.extractContent(xmlDoc, Metadata.TITLE, "//dc:title", metadata);
        xp.extractContent(xmlDoc, Metadata.SUBJECT, "//dc:subject", metadata);
        xp.extractContent(xmlDoc, Metadata.CREATOR, "//dc:creator", metadata);
        xp.extractContent(xmlDoc, Metadata.DESCRIPTION, "//dc:description", metadata);
        xp.extractContent(xmlDoc, Metadata.LANGUAGE, "//dc:language", metadata);
        xp.extractContent(xmlDoc, Metadata.KEYWORDS, "//meta:keyword", metadata);
        xp.extractContent(xmlDoc, Metadata.DATE, "//dc:date", metadata);
        xp.extractContent(xmlDoc, "nbTab", "//meta:document-statistic/@meta:table-count", metadata);
        xp.extractContent(xmlDoc, "nbObject", "//meta:document-statistic/@meta:object-count", metadata);
        xp.extractContent(xmlDoc, "nbImg", "//meta:document-statistic/@meta:image-count", metadata);
        xp.extractContent(xmlDoc, "nbPage", "//meta:document-statistic/@meta:page-count", metadata);
        xp.extractContent(xmlDoc, "nbPara", "//meta:document-statistic/@meta:paragraph-count", metadata);
        xp.extractContent(xmlDoc, "nbWord", "//meta:document-statistic/@meta:word-count", metadata);
        xp.extractContent(xmlDoc, "nbcharacter", "//meta:document-statistic/@meta:character-count", metadata);

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.startElement("p");
        xp.concatOccurrence(xmlDoc, "//*", " ", new AppendableAdaptor(xhtml));
        xhtml.endElement("p");
        xhtml.endDocument();
    }

    public List unzip(InputStream is) {
        List res = new ArrayList();
        try {
            ZipInputStream in = new ZipInputStream(is);
            ZipEntry entry = null;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.getName().equals("meta.xml")
                        || entry.getName().equals("content.xml")) {
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        stream.write(buf, 0, len);
                    }
                    InputStream isEntry = new ByteArrayInputStream(stream
                            .toByteArray());
                    res.add(isEntry);
                }
            }
            in.close();
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        return res;
    }

    protected void copyInputStream(InputStream in, OutputStream out)
            throws IOException {
        byte[] buffer = new byte[1024];
        int len;

        while ((len = in.read(buffer)) >= 0)
            out.write(buffer, 0, len);

        in.close();
        out.close();
    }

}
