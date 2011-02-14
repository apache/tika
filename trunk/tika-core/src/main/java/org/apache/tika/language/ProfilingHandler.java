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

import org.apache.tika.sax.WriteOutContentHandler;

/**
 * SAX content handler that builds a language profile based on all the
 * received character content.
 *
 * @since Apache Tika 0.5
 */
public class ProfilingHandler extends WriteOutContentHandler {

    private final ProfilingWriter writer;

    public ProfilingHandler(ProfilingWriter writer) {
        super(writer);
        this.writer = writer;
    }

    public ProfilingHandler(LanguageProfile profile) {
        this(new ProfilingWriter(profile));
    }

    public ProfilingHandler() {
        this(new ProfilingWriter());
    }

    /**
     * Returns the language profile being built by this content handler.
     * Note that the returned profile gets updated whenever new SAX events
     * are received by this content handler. Use the {@link #getLanguage()}
     * method to get the language that best matches the current state of
     * the profile.
     *
     * @return language profile
     */
    public LanguageProfile getProfile() {
        return writer.getProfile();
    }

    /**
     * Returns the language that best matches the current state of the
     * language profile.
     *
     * @return language that best matches the current profile
     */
    public LanguageIdentifier getLanguage() {
        return writer.getLanguage();
    }

}
