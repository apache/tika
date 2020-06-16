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
import org.apache.poi.util.IOUtils;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.IOExceptionWithCause;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Set;

/**
 * Parser that wraps com.dd.plist's PList parser to handle
 * binary property lists
 */
public class BPListDetector implements Detector {


    MediaType MEMGRAPH = MediaType.application("x-memgraph");
    MediaType WEBARCHIVE = MediaType.application("x-webarchive");
    MediaType BPLIST = MediaType.application("x-bplist");
    MediaType ITUNES = MediaType.application("x-itunes-bplist");

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
            int read = IOUtils.readFully(input, bytes);
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

    private MediaType detectOnKeys(Set<String> keySet) {
        if (keySet.contains("nodes") && keySet.contains("edges")
                && keySet.contains("graphEncodingVersion")) {
            return MEMGRAPH;
        } else if (keySet.contains("WebMainResource") //&& keySet.contains("WebSubresources") should we require this?
        ) {
            return WEBARCHIVE;
        } else if (keySet.contains("Playlists") && keySet.contains("Tracks")
                && keySet.contains("Music Folder")) {
            return ITUNES;
        } //if it contains $archiver and $objects, it is a bplist inside a webarchive

        return BPLIST;
    }
}
