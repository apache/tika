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
package org.apache.tika.parser.apple;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.PropertyListFormatException;
import com.dd.plist.PropertyListParser;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.IOExceptionWithCause;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Detector for BPList with utility functions for PList.
 *
 * Without significant refactoring, this can't easily work as a true
 * detector on plist subtypes.  Rather, for now, we require the file to be
 * parsed and then the parser adds the subtype for xml-based plists.
 * @since 1.25
 */
public class BPListDetector implements Detector {

    //xml versions
    static MediaType MEMGRAPH = MediaType.application("x-plist-memgraph");
    static MediaType WEBARCHIVE = MediaType.application("x-plist-webarchive");
    static MediaType PLIST = MediaType.application("x-plist");
    static MediaType ITUNES = MediaType.application("x-plist-itunes");


    //binary versions
    static MediaType BMEMGRAPH = MediaType.application("x-bplist-memgraph");
    static MediaType BWEBARCHIVE = MediaType.application("x-bplist-webarchive");
    static MediaType BPLIST = MediaType.application("x-bplist");
    static MediaType BITUNES = MediaType.application("x-bplist-itunes");

    private static Map<MediaType, MediaType> BINARY_TO_XML = new HashMap<>();

    static {
        BINARY_TO_XML.put(BMEMGRAPH, MEMGRAPH);
        BINARY_TO_XML.put(BWEBARCHIVE, WEBARCHIVE);
        BINARY_TO_XML.put(BPLIST, PLIST);
        BINARY_TO_XML.put(BITUNES, ITUNES);
    }

    /**
     * @param input    input stream must support reset
     * @param metadata input metadata for the document
     * @return
     * @throws IOException
     */
    @Override
    public MediaType detect(InputStream input, Metadata metadata) throws IOException {
        if (input == null) {
            return MediaType.OCTET_STREAM;
        }
        input.mark(8);
        byte[] bytes = new byte[8];

        try {
            int read = IOUtils.read(input, bytes);
            if (read < 6) {
                return MediaType.OCTET_STREAM;
            }
        } catch (IOException e) {
            return MediaType.OCTET_STREAM;
        } finally {
            input.reset();
        }

        int i = 0;
        if (bytes[i++] != 'b' || bytes[i++] != 'p'
                || bytes[i++] != 'l' || bytes[i++] != 'i'
                || bytes[i++] != 's' || bytes[i++] != 't') {
            return MediaType.OCTET_STREAM;
        }
        //TODO: extract the version with the next two bytes if they were read
        NSObject rootObj = null;
        try {
            if (input instanceof TikaInputStream && ((TikaInputStream) input).hasFile()) {
                rootObj = PropertyListParser.parse(((TikaInputStream) input).getFile());
            } else {
                rootObj = PropertyListParser.parse(input);
            }
            if (input instanceof TikaInputStream) {
                ((TikaInputStream) input).setOpenContainer(rootObj);
            }
        } catch (PropertyListFormatException | ParseException | ParserConfigurationException | SAXException e) {
            throw new IOExceptionWithCause("problem parsing root", e);
        }
        if (rootObj instanceof NSDictionary) {
            return detectOnKeys(((NSDictionary) rootObj).getHashMap().keySet());
        }
        return BPLIST;
    }

    static MediaType detectOnKeys(Set<String> keySet) {
        if (keySet.contains("nodes") && keySet.contains("edges")
                && keySet.contains("graphEncodingVersion")) {
            return BMEMGRAPH;
        } else if (keySet.contains("WebMainResource")){ //&& keySet.contains("WebSubresources") should we require this?
            return BWEBARCHIVE;
        } else if (keySet.contains("Playlists") && keySet.contains("Tracks")
                && keySet.contains("Music Folder")) {
            return BITUNES;
        } //if it contains $archiver and $objects, it is a bplist inside a webarchive
        return BPLIST;
    }

    static MediaType detectXMLOnKeys(Set<String> keySet) {
        return BINARY_TO_XML.get(detectOnKeys(keySet));
    }
}
