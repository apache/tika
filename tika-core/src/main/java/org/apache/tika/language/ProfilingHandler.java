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
package org.apache.tika.language;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

public class ProfilingHandler extends BodyContentHandler {

    private static final long CHECK_INTERVAL = 1000;

    private final LanguageProfile profile;

    private final Metadata metadata;

    private long nextCheckCount = CHECK_INTERVAL;

    private ProfilingHandler(ProfilingWriter writer, Metadata metadata) {
        super(writer);
        this.profile = writer.getProfile();
        this.metadata = metadata;
    }

    public ProfilingHandler(Metadata metadata) {
        this(new ProfilingWriter(), metadata);
    }

    private void checkAndSetLanguage() {
        LanguageIdentifier identifier = new LanguageIdentifier(profile);
        if (identifier.isReasonablyCertain()) {
            metadata.set(Metadata.LANGUAGE, identifier.getLanguage());
        }
    }

    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        super.characters(ch, start, length);
        if (profile.getCount() > nextCheckCount) {
            checkAndSetLanguage();
            nextCheckCount = profile.getCount() + CHECK_INTERVAL;
        }
    }

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();
        checkAndSetLanguage();
    }

}
