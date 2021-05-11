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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Parses the output of /bin/ls and counts the number of files and the number of
 * executables using Tika.
 */
public class DirListParser implements Parser {

    private static final long serialVersionUID = 2717930544410610735L;

    private static Set<MediaType> SUPPORTED_TYPES =
            new HashSet<>(Collections.singletonList(MediaType.TEXT_PLAIN));

    public static void main(String[] args) throws IOException, SAXException, TikaException {
        DirListParser parser = new DirListParser();
        Metadata met = new Metadata();
        parser.parse(System.in, new BodyContentHandler(), met);

        System.out.println("Num files: " + met.getValues("Filename").length);
        System.out.println("Num executables: " + met.get("NumExecutables"));
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.tika.parser.Parser#getSupportedTypes(
     * org.apache.tika.parser.ParseContext)
     */
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.tika.parser.Parser#parse(java.io.InputStream,
     * org.xml.sax.ContentHandler, org.apache.tika.metadata.Metadata)
     */
    public void parse(InputStream is, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        this.parse(is, handler, metadata, new ParseContext());
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.tika.parser.Parser#parse(java.io.InputStream,
     * org.xml.sax.ContentHandler, org.apache.tika.metadata.Metadata,
     * org.apache.tika.parser.ParseContext)
     */
    public void parse(InputStream is, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        List<String> lines = FileUtils.readLines(TikaInputStream.get(is).getFile(), UTF_8);
        for (String line : lines) {
            String[] fileToks = line.split("\\s+");
            if (fileToks.length < 8) {
                continue;
            }
            String filePermissions = fileToks[0];
            String numHardLinks = fileToks[1];
            String fileOwner = fileToks[2];
            String fileOwnerGroup = fileToks[3];
            String fileSize = fileToks[4];
            StringBuilder lastModDate = new StringBuilder();
            lastModDate.append(fileToks[5]);
            lastModDate.append(" ");
            lastModDate.append(fileToks[6]);
            lastModDate.append(" ");
            lastModDate.append(fileToks[7]);
            StringBuilder fileName = new StringBuilder();
            for (int i = 8; i < fileToks.length; i++) {
                fileName.append(fileToks[i]);
                fileName.append(" ");
            }
            fileName.deleteCharAt(fileName.length() - 1);
            this.addMetadata(metadata, filePermissions, numHardLinks, fileOwner, fileOwnerGroup,
                    fileSize, lastModDate.toString(), fileName.toString());
        }
    }

    private void addMetadata(Metadata metadata, String filePerms, String numHardLinks,
                             String fileOwner, String fileOwnerGroup, String fileSize,
                             String lastModDate, String fileName) {
        metadata.add("FilePermissions", filePerms);
        metadata.add("NumHardLinks", numHardLinks);
        metadata.add("FileOwner", fileOwner);
        metadata.add("FileOwnerGroup", fileOwnerGroup);
        metadata.add("FileSize", fileSize);
        metadata.add("LastModifiedDate", lastModDate);
        metadata.add("Filename", fileName);

        if (filePerms.contains("x") && !filePerms.contains("d")) {
            if (metadata.get("NumExecutables") != null) {
                int numExecs = Integer.parseInt(metadata.get("NumExecutables"));
                numExecs++;
                metadata.set("NumExecutables", String.valueOf(numExecs));
            } else {
                metadata.set("NumExecutables", "1");
            }
        }
    }

}
