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
package org.apache.tika.parser.iwork;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A parser for the IWork container files. This includes *.key, *.pages and *.numbers files.
 * This parser delegates the relevant files to {@link IWorkParser} that parsers the content.
 */
public class IWorkPackageParser implements Parser {

    private final static Set<MediaType> supportedTypes =
            Collections.singleton(MediaType.application("vnd.apple.iwork"));

    private final static Set<String> relevantFileNames = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("index.apxl", "index.xml", "presentation.apxl"))
    );

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return supportedTypes;
    }

    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        ArchiveInputStream zip = new ZipArchiveInputStream(stream);
        ArchiveEntry entry = zip.getNextEntry();
        Parser parser = context.get(Parser.class, EmptyParser.INSTANCE);
        while (entry != null) {
            if (!relevantFileNames.contains(entry.getName())) {
                entry = zip.getNextEntry();
                continue;
            }

            parser.parse(new CloseShieldInputStream(zip), handler, metadata, context);
            entry = zip.getNextEntry();
        }
        zip.close();
    }

    /**
     * @deprecated This method will be removed in Apache Tika 1.0.
     */
    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        parse(stream, handler, metadata, new ParseContext());
    }

}
