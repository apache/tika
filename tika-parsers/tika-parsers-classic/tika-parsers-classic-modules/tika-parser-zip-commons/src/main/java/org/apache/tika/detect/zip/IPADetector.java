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
package org.apache.tika.detect.zip;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;

public class IPADetector implements ZipContainerDetector {

    static final MediaType IPA = MediaType.application("x-itunes-ipa");

    /**
     * To be considered as an IPA file, it needs to match all of these
     */
    private static HashSet<Pattern> ipaEntryPatterns = new HashSet<Pattern>() {
        private static final long serialVersionUID = 6545295886322115362L;

        {
            add(Pattern.compile("^Payload/$"));
            add(Pattern.compile("^Payload/.*\\.app/$"));
            add(Pattern.compile("^Payload/.*\\.app/_CodeSignature/$"));
            add(Pattern.compile("^Payload/.*\\.app/_CodeSignature/CodeResources$"));
            add(Pattern.compile("^Payload/.*\\.app/Info\\.plist$"));
            add(Pattern.compile("^Payload/.*\\.app/PkgInfo$"));
        }
    };

    @Override
    public MediaType detect(ZipFile zip, TikaInputStream tis) throws IOException {
        // Note - consider generalising this logic, if another format needs many regexp matching
        TmpPatterns tmpPatterns = new TmpPatterns();

        Enumeration<ZipArchiveEntry> entries = zip.getEntries();
        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            String name = entry.getName();

            Iterator<Pattern> ip = tmpPatterns.patterns.iterator();
            while (ip.hasNext()) {
                if (ip.next().matcher(name).matches()) {
                    ip.remove();
                }
            }
            if (tmpPatterns.patterns.isEmpty()) {
                // We've found everything we need to find
                return MediaType.application("x-itunes-ipa");
            }
        }

        // If we get here, not all required entries were found
        return null;

    }

    @Override
    public MediaType streamingDetectUpdate(ZipArchiveEntry zae, InputStream zis,
                                           StreamingDetectContext detectContext) {
        String name = zae.getName();
        TmpPatterns tmp = detectContext.get(TmpPatterns.class);
        if (tmp == null) {
            tmp = new TmpPatterns();
            detectContext.set(TmpPatterns.class, tmp);
        }

        Iterator<Pattern> ip = tmp.patterns.iterator();
        while (ip.hasNext()) {
            if (ip.next().matcher(name).matches()) {
                ip.remove();
            }
        }
        if (tmp.patterns.isEmpty()) {
            // We've found everything we need to find
            return IPA;
        }
        return null;
    }

    @Override
    public MediaType streamingDetectFinal(StreamingDetectContext detectContext) {
        TmpPatterns tmp = detectContext.get(TmpPatterns.class);
        if (tmp == null) {
            return null;
        }
        if (tmp.patterns.isEmpty()) {
            // We've found everything we need to find
            return IPA;
        }
        detectContext.remove(TmpPatterns.class);
        return null;
    }

    private static class TmpPatterns {
        Set<Pattern> patterns = (Set<Pattern>) ipaEntryPatterns.clone();
    }
}
