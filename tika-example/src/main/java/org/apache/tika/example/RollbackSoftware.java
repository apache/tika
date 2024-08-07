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

package org.apache.tika.example;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.Link;
import org.apache.tika.sax.LinkContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Demonstrates Tika and its ability to sense symlinks.
 */
public class RollbackSoftware {
    public static void main(String[] args) throws Exception {
        RollbackSoftware r = new RollbackSoftware();
        r.rollback(new File(args[0]));
    }

    public void rollback(File deployArea) throws IOException, SAXException, TikaException {
        LinkContentHandler handler = new LinkContentHandler();
        Metadata met = new Metadata();
        DeploymentAreaParser parser = new DeploymentAreaParser();
        parser.parse(IOUtils.toInputStream(deployArea.getAbsolutePath(), UTF_8), handler, met);
        List<Link> links = handler.getLinks();
        if (links.size() < 2) {
            throw new IOException("Must have installed at least 2 versions!");
        }
        links.sort(Comparator.comparing(Link::getText));

        this.updateVersion(links
                .get(links.size() - 2)
                .getText());
    }

    private void updateVersion(String version) {
        System.out.println("Rolling back to version: [" + version + "]");
    }

    private boolean isSymlink(File f) throws IOException {
        return !f
                .getAbsolutePath()
                .equals(f.getCanonicalPath());
    }

    class DeploymentAreaParser implements Parser {
        private static final long serialVersionUID = -2356647405087933468L;

        /*
         * (non-Javadoc)
         *
         * @see org.apache.tika.parser.Parser#getSupportedTypes(
         * org.apache.tika.parser.ParseContext)
         */
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return Collections.unmodifiableSet(new HashSet<>(Collections.singletonList(MediaType.TEXT_PLAIN)));
        }

        /*
         * (non-Javadoc)
         *
         * @see org.apache.tika.parser.Parser#parse(java.io.InputStream,
         * org.xml.sax.ContentHandler, org.apache.tika.metadata.Metadata)
         */
        public void parse(InputStream is, ContentHandler handler, Metadata metadata) throws IOException, SAXException, TikaException {
            parse(is, handler, metadata, new ParseContext());
        }

        /*
         * (non-Javadoc)
         *
         * @see org.apache.tika.parser.Parser#parse(java.io.InputStream,
         * org.xml.sax.ContentHandler, org.apache.tika.metadata.Metadata,
         * org.apache.tika.parser.ParseContext)
         */
        public void parse(InputStream is, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

            File deployArea = new File(IOUtils.toString(is, UTF_8));
            File[] versions = deployArea.listFiles(pathname -> !pathname
                    .getName()
                    .startsWith("current"));

            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();
            for (File v : versions) {
                if (isSymlink(v)) {
                    continue;
                }
                xhtml.startElement("a", "href", v
                        .toURI()
                        .toURL()
                        .toExternalForm());
                xhtml.characters(v.getName());
                xhtml.endElement("a");
            }
        }
    }
}
